package com.clipforge.ai.core.gl

import android.graphics.Bitmap
import android.graphics.Color
import android.util.Log
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

/**
 * Dip-to-color transition overlay (beside CrossfadeBitmapOverlay; does NOT touch it).
 *
 * Unlike the dissolve overlay (clip B over clip A), this lays a SOLID COLOR over the
 * base clip and ramps its alpha in one direction across the window:
 *  - FADE_OUT (direction=+1): alpha 0 -> 1  (base clip disappears under the color)
 *  - FADE_IN  (direction=-1): alpha 1 -> 0  (color reveals the base clip)
 *
 * A dip-to-black is two of these back to back: clip A's tail with FADE_OUT to black,
 * then clip B's head with FADE_IN from black. No frame extraction needed (solid color),
 * so dip transitions are fast - no MediaCodec/cache involved.
 *
 * Times are COMPOSITION-GLOBAL (fadeStartUs/fadeEndUs = where this item plays).
 *
 * @param colorInt   ARGB color to dip through (e.g. Color.BLACK, Color.WHITE).
 * @param fadeStartUs composition time where this half begins.
 * @param fadeEndUs   composition time where this half ends.
 * @param fadeOut     true = ramp 0->1 (A to color); false = ramp 1->0 (color to B).
 */
@UnstableApi
class DipToColorOverlay(
    private val colorInt: Int,
    private val fadeStartUs: Long,
    private val fadeEndUs: Long,
    private val fadeOut: Boolean
) : BitmapOverlay() {

    private val tag = "DIP_OV"
    // A small solid-color bitmap; setScale(1,1) fills the frame (uniform color, no aspect issue).
    private val colorBitmap: Bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
        eraseColor(colorInt)
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap = colorBitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val span = (fadeEndUs - fadeStartUs).toFloat().coerceAtLeast(1f)
        val tRaw = ((presentationTimeUs - fadeStartUs).toFloat() / span).coerceIn(0f, 1f)
        // smoothstep easing
        val t = tRaw * tRaw * (3f - 2f * tRaw)
        val alpha = if (fadeOut) t else (1f - t)
        return StaticOverlaySettings.Builder()
            .setAlphaScale(alpha)
            .setScale(1f, 1f)
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(0f, 0f)
            .build()
    }

    override fun release() {
        super.release()
        try { if (!colorBitmap.isRecycled) colorBitmap.recycle() } catch (_: Exception) {}
        Log.d(tag, "released")
    }

    companion object {
        const val BLACK = Color.BLACK
        const val WHITE = Color.WHITE
    }
}
