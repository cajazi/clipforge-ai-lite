package com.clipforge.ai.core.gl

import android.graphics.Bitmap
import android.util.Log
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

/**
 * Zoom transition overlay.
 *
 * Clip A stays as the base frame. Clip B is an opaque cached overlay that settles into
 * the full-frame center using smoothstep easing, so the export never exposes black.
 */
@UnstableApi
class ZoomOverlay(
    private val cache: CrossfadeFrameCache,
    private val fadeStartUs: Long,
    private val fadeEndUs: Long,
    private val mode: Mode
) : BitmapOverlay() {

    enum class Mode { IN, OUT }

    private val tag = "ZOOM_OV"
    private val scaleStart = when (mode) {
        Mode.IN -> ZOOM_IN_SCALE_START
        Mode.OUT -> ZOOM_OUT_SCALE_START
    }
    private val scaleEnd = when (mode) {
        Mode.IN -> ZOOM_IN_SCALE_END
        Mode.OUT -> ZOOM_OUT_SCALE_END
    }
    private val alphaStart = when (mode) {
        Mode.IN -> ZOOM_IN_ALPHA_START
        Mode.OUT -> ZOOM_OUT_ALPHA_START
    }
    private val alphaEnd = when (mode) {
        Mode.IN -> ZOOM_IN_ALPHA_END
        Mode.OUT -> ZOOM_OUT_ALPHA_END
    }
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
        cache.logStats("ZOOM_OVERLAY_CREATE_CACHE_STATS")
        Log.d(
            tag,
            "CREATE fadeStartUs=$fadeStartUs fadeEndUs=$fadeEndUs durationUs=${fadeEndUs - fadeStartUs} " +
                "mode=ZOOM_$mode scaleStart=$scaleStart scaleEnd=$scaleEnd " +
                "cacheEmpty=${cache.isEmpty()} initialBitmap=${lastBitmap.width}x${lastBitmap.height}"
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
            Log.e(tag, "getBitmap failed ptsUs=$presentationTimeUs mode=$mode", t)
            lastBitmap
        }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        try {
            val span = (fadeEndUs - fadeStartUs).toFloat().coerceAtLeast(1f)
            val tRaw = ((presentationTimeUs - fadeStartUs).toFloat() / span).coerceIn(0f, 1f)
            val t = tRaw * tRaw * (3f - 2f * tRaw)
            val scale = scaleStart + ((scaleEnd - scaleStart) * t)
            val alpha = alphaStart + ((alphaEnd - alphaStart) * t)

            settingsCallCount++
            if (settingsCallCount <= 5 || settingsCallCount % 30 == 0) {
                Log.d(
                    tag,
                    "getOverlaySettings call=$settingsCallCount ptsUs=$presentationTimeUs tRaw=$tRaw t=$t " +
                        "mode=ZOOM_$mode scale=$scale alpha=$alpha scaleStart=$scaleStart scaleEnd=$scaleEnd"
                )
            }

            return StaticOverlaySettings.Builder()
                .setAlphaScale(alpha)
                .setScale(scale, scale)
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, 0f)
                .build()
        } catch (t: Throwable) {
            Log.e(tag, "getOverlaySettings failed ptsUs=$presentationTimeUs mode=$mode", t)
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
                "mode=ZOOM_$mode scaleStart=$scaleStart scaleEnd=$scaleEnd " +
                "finalMode=${stats.finalMode} prepMs=${stats.prepMs} estimatedBytes=${stats.estimatedBytes} " +
                "coverage=${stats.coveragePercent} targetCoverage=${stats.targetCoveragePercent} " +
                "maxAllowedSpacingUs=$maxAllowedSpacingUs histogram=$histogramPreview"
        )
        cache.logStats("ZOOM_OVERLAY_RELEASE_CACHE_STATS")
        try { cache.release() } catch (t: Throwable) { Log.e(tag, "cache release failed", t) }
        Log.d(tag, "released bitmapCalls=$bitmapCallCount settingsCalls=$settingsCallCount mode=$mode")
    }
}

private const val ZOOM_IN_SCALE_START = 0.82f
private const val ZOOM_IN_SCALE_END = 1.0f
private const val ZOOM_IN_ALPHA_START = 0.92f
private const val ZOOM_IN_ALPHA_END = 1.0f
private const val ZOOM_OUT_SCALE_START = 1.16f
private const val ZOOM_OUT_SCALE_END = 1.0f
private const val ZOOM_OUT_ALPHA_START = 1.0f
private const val ZOOM_OUT_ALPHA_END = 1.0f
