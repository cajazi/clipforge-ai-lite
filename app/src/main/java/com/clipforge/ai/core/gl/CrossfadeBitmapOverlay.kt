package com.clipforge.ai.core.gl

import android.graphics.Bitmap
import android.util.Log
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

/**
 * Crossfade via overlay. Clip A is the base frame; this overlay supplies clip B's
 * frame at each timestamp with alpha ramping 0 -> 1 across the fade window.
 *
 * The frame cache MUST be pre-built (cache.build()) BEFORE transformer.start().
 * Building it lazily inside getBitmap() blocks the video-processing thread for the
 * whole extraction, producing no output samples - which trips Media3's 10s muxer
 * watchdog ("no output sample written"). So the executor builds the cache up front,
 * off the render thread, then hands the ready cache here.
 *
 * Times are COMPOSITION-GLOBAL: fadeStartUs/fadeEndUs are where the crossfade item
 * actually plays on the timeline, not segment-local 0.
 *
 * @param cache        a pre-built CrossfadeFrameCache (already build()-ed).
 * @param fadeStartUs  composition time where the fade begins (alpha 0).
 * @param fadeEndUs    composition time where the fade completes (alpha 1).
 */
@UnstableApi
class CrossfadeBitmapOverlay(
    private val cache: CrossfadeFrameCache,
    private val fadeStartUs: Long,
    private val fadeEndUs: Long
) : BitmapOverlay() {

    private val tag = "CROSSFADE_OV"
    private var lastBitmap: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val intoWindowUs = (presentationTimeUs - fadeStartUs).coerceAtLeast(0L)
        val frame = cache.frameForOffset(intoWindowUs)
        if (frame != null) lastBitmap = frame
        return lastBitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val alpha: Float = when {
            presentationTimeUs <= fadeStartUs -> 0f
            presentationTimeUs >= fadeEndUs -> 1f
            else -> {
                val span = (fadeEndUs - fadeStartUs).toFloat()
                ((presentationTimeUs - fadeStartUs).toFloat() / span).coerceIn(0f, 1f)
            }
        }
        return StaticOverlaySettings.Builder()
            .setAlphaScale(alpha)
            .setScale(1f, 1f)
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(0f, 0f)
            .build()
    }

    override fun release() {
        super.release()
        try { cache.release() } catch (_: Exception) {}
        Log.d(tag, "released")
    }
}
