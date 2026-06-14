package com.clipforge.ai.core.gl

import android.graphics.Bitmap
import android.util.Log
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

/**
 * Pure, JVM-testable easing for the BOUNCE transition.
 *
 * BOUNCE is a zoom-class pop-in: clip B scales up from [SCALE_START] to [SCALE_END] with an
 * elastic OVERSHOOT (back-out) then settles to exactly 1.0, matching the timeline preview's
 * bounce wobble. No Android/Media3 dependency so it can be unit-tested without GL.
 */
object BounceEasing {
    const val SCALE_START = 0.80f
    const val SCALE_END = 1.0f

    // Standard easeOutBack constants (overshoot then settle to exactly 1.0 at t=1).
    private const val C1 = 1.70158f
    private const val C3 = C1 + 1f

    /** easeOutBack in [.. ,1]: 0 at t=0, overshoots >1 near the end, exactly 1 at t=1. */
    fun easeOutBack(tRaw: Float): Float {
        val t = tRaw.coerceIn(0f, 1f) - 1f
        return 1f + C3 * t * t * t + C1 * t * t
    }

    /** Smoothstep in [0,1] for the alpha fade-in. */
    fun smoothstep(tRaw: Float): Float {
        val t = tRaw.coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    /** B scale at progress t: SCALE_START -> SCALE_END with overshoot, exactly SCALE_END at t=1. */
    fun scale(tRaw: Float): Float = SCALE_START + (SCALE_END - SCALE_START) * easeOutBack(tRaw)

    /** B alpha at progress t: 0 -> 1 (B fades in over the A base). */
    fun alpha(tRaw: Float): Float = smoothstep(tRaw)
}

/**
 * Bounce transition overlay.
 *
 * Mirrors the ZoomOverlay structure (clip A is the base frame; clip B is the cached overlay)
 * but uses [BounceEasing] back-out scale instead of smoothstep, so B pops/bounces into place.
 * Single variant (no mode/direction). Does not modify ZoomOverlay.
 */
@UnstableApi
class BounceOverlay(
    private val cache: CrossfadeFrameCache,
    private val fadeStartUs: Long,
    private val fadeEndUs: Long
) : BitmapOverlay() {

    private val tag = "BOUNCE_OV"
    private var lastBitmap: Bitmap = cache.frameInfoForOffset(0L)?.bitmap
        ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private var bitmapCallCount = 0
    private var settingsCallCount = 0
    private var frameHitCount = 0
    private var frameMissCount = 0
    private val usedFrameIndices = linkedSetOf<Int>()

    init {
        cache.logStats("BOUNCE_OVERLAY_CREATE_CACHE_STATS")
        Log.d(
            tag,
            "CREATE fadeStartUs=$fadeStartUs fadeEndUs=$fadeEndUs durationUs=${fadeEndUs - fadeStartUs} " +
                "scaleStart=${BounceEasing.SCALE_START} scaleEnd=${BounceEasing.SCALE_END} " +
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
                lastBitmap = frame.bitmap
            } else {
                frameMissCount++
            }
            bitmapCallCount++
            if (bitmapCallCount <= 5 || bitmapCallCount % 30 == 0) {
                Log.d(
                    tag,
                    "getBitmap call=$bitmapCallCount ptsUs=$presentationTimeUs intoWindowUs=$intoWindowUs " +
                        "frameHit=${frame != null} frameIndex=${frame?.index} " +
                        "bitmap=${lastBitmap.width}x${lastBitmap.height} recycled=${lastBitmap.isRecycled}"
                )
            }
            lastBitmap
        } catch (t: Throwable) {
            Log.e(tag, "getBitmap failed ptsUs=$presentationTimeUs", t)
            lastBitmap
        }
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        return try {
            val span = (fadeEndUs - fadeStartUs).toFloat().coerceAtLeast(1f)
            val tRaw = ((presentationTimeUs - fadeStartUs).toFloat() / span).coerceIn(0f, 1f)
            val scale = BounceEasing.scale(tRaw)
            val alpha = BounceEasing.alpha(tRaw)

            settingsCallCount++
            if (settingsCallCount <= 5 || settingsCallCount % 30 == 0) {
                Log.d(tag, "getOverlaySettings call=$settingsCallCount ptsUs=$presentationTimeUs tRaw=$tRaw scale=$scale alpha=$alpha")
            }

            StaticOverlaySettings.Builder()
                .setAlphaScale(alpha)
                .setScale(scale, scale)
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, 0f)
                .build()
        } catch (t: Throwable) {
            Log.e(tag, "getOverlaySettings failed ptsUs=$presentationTimeUs", t)
            StaticOverlaySettings.Builder()
                .setAlphaScale(1f)
                .setScale(1f, 1f)
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, 0f)
                .build()
        }
    }

    override fun release() {
        super.release()
        Log.d(
            tag,
            "FRAME_USAGE bitmapCalls=$bitmapCallCount hits=$frameHitCount misses=$frameMissCount " +
                "uniqueFrames=${usedFrameIndices.size}"
        )
        cache.logStats("BOUNCE_OVERLAY_RELEASE_CACHE_STATS")
        try { cache.release() } catch (t: Throwable) { Log.e(tag, "cache release failed", t) }
        Log.d(tag, "released bitmapCalls=$bitmapCallCount settingsCalls=$settingsCallCount")
    }
}
