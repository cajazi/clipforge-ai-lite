package com.clipforge.ai.core.gl

import android.graphics.Bitmap
import android.util.Log
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

/**
 * Flash transition overlays.
 *
 * Clip A remains the base item. [FlashRevealOverlay] switches cached clip B on at the
 * midpoint, while [FlashColorOverlay] reaches full opacity over that cut so the A/B swap
 * is hidden under the burst instead of crossfading.
 */
@UnstableApi
class FlashRevealOverlay(
    private val cache: CrossfadeFrameCache,
    private val startUs: Long,
    private val endUs: Long
) : BitmapOverlay() {

    private val tag = "FLASH_REVEAL_OV"
    private var lastBitmap: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val intoWindowUs = (presentationTimeUs - startUs).coerceAtLeast(0L)
        val frame = cache.frameForOffset(intoWindowUs)
        if (frame != null) lastBitmap = frame
        return lastBitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val midpointUs = startUs + ((endUs - startUs).coerceAtLeast(0L) / 2L)
        val alpha = if (presentationTimeUs >= midpointUs) 1f else 0f
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

@UnstableApi
class FlashColorOverlay(
    colorInt: Int,
    private val startUs: Long,
    private val endUs: Long
) : BitmapOverlay() {

    private val tag = "FLASH_COLOR_OV"
    private val colorBitmap: Bitmap = Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
        eraseColor(colorInt)
    }

    override fun getBitmap(presentationTimeUs: Long): Bitmap = colorBitmap

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val span = (endUs - startUs).toFloat().coerceAtLeast(1f)
        val t = ((presentationTimeUs - startUs).toFloat() / span).coerceIn(0f, 1f)
        val alpha = flashAlpha(t)
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

    private fun flashAlpha(t: Float): Float = when {
        t <= ATTACK_END -> smoothstep(t / ATTACK_END)
        t <= PEAK_END -> 1f
        else -> 1f - smoothstep((t - PEAK_END) / (1f - PEAK_END))
    }.coerceIn(0f, 1f)

    private fun smoothstep(value: Float): Float {
        val x = value.coerceIn(0f, 1f)
        return x * x * (3f - 2f * x)
    }

    private companion object {
        const val ATTACK_END = 0.38f
        const val PEAK_END = 0.52f
    }
}
