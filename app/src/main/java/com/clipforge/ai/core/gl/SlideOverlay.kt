package com.clipforge.ai.core.gl

import android.graphics.Bitmap
import android.util.Log
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

/**
 * Slide transition overlay (beside CrossfadeBitmapOverlay/DipToColorOverlay; touches neither).
 *
 * Clip A is the static base. Clip B is supplied as an OPAQUE overlay (alpha=1) whose
 * POSITION animates from off-screen to centered across the window - so B slides in over A.
 * Reuses CrossfadeFrameCache for B's frames (same MediaCodec fast path as dissolve).
 *
 * Position is driven by setBackgroundFrameAnchor(x, y) in NDC where (0,0)=center,
 * and the frame spans -1..+1 (2 units wide/tall). Starting the anchor at +/-2 places B
 * fully off-screen; animating to 0 brings it to center (covering A).
 *
 * Direction (motion of B as it enters):
 *   LEFT  : B enters from right edge, moves left   (bgX: +2 -> 0)
 *   RIGHT : B enters from left edge,  moves right  (bgX: -2 -> 0)
 *   UP    : B enters from bottom,     moves up     (bgY: -2 -> 0)
 *   DOWN  : B enters from top,        moves down   (bgY: +2 -> 0)
 *
 * smoothstep easing on the position for a softer motion.
 *
 * @param cache       pre-built CrossfadeFrameCache for clip B (built before transformer.start).
 * @param fadeStartUs composition time where the slide begins.
 * @param fadeEndUs   composition time where the slide completes (B centered).
 * @param direction   one of LEFT/RIGHT/UP/DOWN.
 */
@UnstableApi
class SlideOverlay(
    private val cache: CrossfadeFrameCache,
    private val fadeStartUs: Long,
    private val fadeEndUs: Long,
    private val direction: Direction
) : BitmapOverlay() {

    enum class Direction { LEFT, RIGHT, UP, DOWN }

    private val tag = "SLIDE_OV"
    private var lastBitmap: Bitmap = cache.frameInfoForOffset(0L)?.bitmap
        ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private var bitmapCallCount = 0
    private var settingsCallCount = 0
    private var frameHitCount = 0
    private var frameMissCount = 0
    private var maxFrameDeltaUs = 0L
    private var totalFrameDeltaUs = 0L
    private val usedFrameIndices = linkedSetOf<Int>()
    private val usedFrameHistogram = linkedMapOf<Int, Int>()

    init {
        cache.logStats("SLIDE_OVERLAY_CREATE_CACHE_STATS")
        Log.d(
            tag,
            "CREATE fadeStartUs=$fadeStartUs fadeEndUs=$fadeEndUs durationUs=${fadeEndUs - fadeStartUs} " +
                "direction=$direction cacheEmpty=${cache.isEmpty()} initialBitmap=${lastBitmap.width}x${lastBitmap.height}"
        )
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        return try {
            val intoWindowUs = (presentationTimeUs - fadeStartUs).coerceAtLeast(0L)
            val frame = cache.frameInfoForOffset(intoWindowUs)
            if (frame != null) {
                frameHitCount++
                usedFrameIndices.add(frame.index)
                usedFrameHistogram[frame.index] = (usedFrameHistogram[frame.index] ?: 0) + 1
                totalFrameDeltaUs += frame.deltaUs
                maxFrameDeltaUs = maxOf(maxFrameDeltaUs, frame.deltaUs)
                lastBitmap = frame.bitmap
            } else {
                frameMissCount++
            }
            bitmapCallCount++
            if (bitmapCallCount <= 5 || bitmapCallCount % 30 == 0) {
                Log.d(
                    tag,
                    "getBitmap call=$bitmapCallCount ptsUs=$presentationTimeUs intoWindowUs=$intoWindowUs " +
                        "frameHit=${frame != null} frameIndex=${frame?.index} framePtsUs=${frame?.ptsUs} " +
                        "requestedUs=${frame?.requestedUs} deltaUs=${frame?.deltaUs} " +
                        "bitmap=${lastBitmap.width}x${lastBitmap.height} recycled=${lastBitmap.isRecycled}"
                )
            }
            lastBitmap
        } catch (t: Throwable) {
            Log.e(tag, "getBitmap failed ptsUs=$presentationTimeUs direction=$direction", t)
            lastBitmap
        }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        try {
            val span = (fadeEndUs - fadeStartUs).toFloat().coerceAtLeast(1f)
            val tRaw = ((presentationTimeUs - fadeStartUs).toFloat() / span).coerceIn(0f, 1f)
            val t = tRaw * tRaw * (3f - 2f * tRaw) // smoothstep

            // Keep both Media3 anchors in their normalized [-1, 1] range. Aligning an
            // overlay edge to the opposite background edge still places a full-frame
            // overlay off-screen without using out-of-range anchor values.
            val s = 1f - t
            var overlayX = 0f
            var overlayY = 0f
            var bgX = 0f
            var bgY = 0f
            when (direction) {
                Direction.LEFT -> {
                    overlayX = -s
                    bgX = s
                }
                Direction.RIGHT -> {
                    overlayX = s
                    bgX = -s
                }
                Direction.UP -> {
                    overlayY = -s
                    bgY = s
                }
                Direction.DOWN -> {
                    overlayY = s
                    bgY = -s
                }
            }

            settingsCallCount++
            if (settingsCallCount <= 5 || settingsCallCount % 30 == 0) {
                Log.d(
                    tag,
                    "getOverlaySettings call=$settingsCallCount ptsUs=$presentationTimeUs tRaw=$tRaw t=$t " +
                        "overlayAnchor=($overlayX,$overlayY) backgroundAnchor=($bgX,$bgY) direction=$direction"
                )
            }

            return StaticOverlaySettings.Builder()
                .setAlphaScale(1f)
                .setScale(1f, 1f)
                .setOverlayFrameAnchor(overlayX, overlayY)
                .setBackgroundFrameAnchor(bgX, bgY)
                .build()
        } catch (t: Throwable) {
            Log.e(tag, "getOverlaySettings failed ptsUs=$presentationTimeUs direction=$direction", t)
            return StaticOverlaySettings.Builder()
                .setAlphaScale(1f)
                .setScale(1f, 1f)
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, 0f)
                .build()
        }
    }

    override fun release() {
        super.release()
        val avgDeltaUs = if (frameHitCount > 0) totalFrameDeltaUs / frameHitCount else 0L
        val histogramPreview = usedFrameHistogram.entries.take(12).joinToString { "${it.key}:${it.value}" }
        val stats = cache.stats()
        val maxAllowedSpacingUs = if (stats.finalMode == "BOUNDED_RETRIEVER_FILL") 100_000L else 80_000L
        val cachePass = stats.coveragePercent >= stats.targetCoveragePercent && stats.maxSpacingUs <= maxAllowedSpacingUs
        val usagePass = frameMissCount == 0 && usedFrameIndices.size >= (stats.keptFrames * 0.70f).toInt().coerceAtLeast(1)
        val deltaPass = avgDeltaUs <= 25_000L && maxFrameDeltaUs <= 50_000L
        Log.d(
            tag,
            "FRAME_USAGE bitmapCalls=$bitmapCallCount hits=$frameHitCount misses=$frameMissCount " +
                "uniqueFrames=${usedFrameIndices.size} avgDeltaUs=$avgDeltaUs maxDeltaUs=$maxFrameDeltaUs " +
                "cachePass=$cachePass usagePass=$usagePass deltaPass=$deltaPass " +
                "finalMode=${stats.finalMode} prepMs=${stats.prepMs} estimatedBytes=${stats.estimatedBytes} " +
                "coverage=${stats.coveragePercent} targetCoverage=${stats.targetCoveragePercent} " +
                "maxAllowedSpacingUs=$maxAllowedSpacingUs histogram=$histogramPreview"
        )
        cache.logStats("SLIDE_OVERLAY_RELEASE_CACHE_STATS")
        try { cache.release() } catch (t: Throwable) { Log.e(tag, "cache release failed", t) }
        Log.d(tag, "released bitmapCalls=$bitmapCallCount settingsCalls=$settingsCallCount direction=$direction")
    }
}
