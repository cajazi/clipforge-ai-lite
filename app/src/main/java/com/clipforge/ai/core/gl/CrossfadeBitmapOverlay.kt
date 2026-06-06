package com.clipforge.ai.core.gl

import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

/**
 * Crossfade via overlay (Path 1 proof).
 *
 * Clip A flows through the pipeline as the base frame. This overlay supplies
 * clip B's frame at each timestamp as a full-frame bitmap, with alpha ramping
 * 0 -> 1 across the fade window. Media3's BitmapOverlay base class uploads the
 * bitmap we return from getBitmap() to a GL texture and blends it over A using
 * the alpha from getOverlaySettings(). That alpha-over-base blend is a TRUE
 * dissolve (unlike VideoCompositorSettings, which is PiP/grid layout).
 *
 * PROOF STAGE: clip B frames are pulled with MediaMetadataRetriever.getFrameAtTime
 * per call. This is slow (seek+decode each frame) but proves whether the blend
 * works visually. If it does, a real decoder replaces the retriever for speed.
 *
 * @param clipBPath        file path to clip B.
 * @param clipBStartUs     where in clip B the overlay content begins (usually 0).
 * @param fadeStartUs      composition time where the fade begins.
 * @param fadeEndUs        composition time where the fade completes (alpha = 1).
 * @param frameWidth/Height target size for the extracted bitmap (match clip A).
 */
@UnstableApi
class CrossfadeBitmapOverlay(
    private val clipBPath: String,
    private val clipBStartUs: Long,
    private val fadeStartUs: Long,
    private val fadeEndUs: Long
) : BitmapOverlay() {

    private val retriever = MediaMetadataRetriever().apply { setDataSource(clipBPath) }
    private var lastBitmap: Bitmap? = null

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        // Map composition time -> clip B source time. At fadeStartUs we want clip B's
        // frame at clipBStartUs; advancing in lockstep after that.
        val intoFadeUs = (presentationTimeUs - fadeStartUs).coerceAtLeast(0L)
        val clipBTimeUs = clipBStartUs + intoFadeUs
        val frame = retriever.getFrameAtTime(clipBTimeUs, MediaMetadataRetriever.OPTION_CLOSEST)
            ?: lastBitmap
            ?: Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        lastBitmap = frame
        return frame
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
        android.util.Log.d("CROSSFADE_OV", "ptUs=" + presentationTimeUs + " fade=[" + fadeStartUs + ".." + fadeEndUs + "] alpha=" + alpha)
        // Full-frame: scale 1x1, anchor centered. Explicitly set to avoid any
        // default that would shrink/offset the overlay (the mis-positioning risk).
        return StaticOverlaySettings.Builder()
            .setAlphaScale(alpha)
            .setScale(1f, 1f)
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(0f, 0f)
            .build()
    }

    override fun release() {
        super.release()
        try { retriever.release() } catch (_: Exception) {}
        lastBitmap = null
    }
}
