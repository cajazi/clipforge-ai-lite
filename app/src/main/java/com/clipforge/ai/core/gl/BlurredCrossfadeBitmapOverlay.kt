package com.clipforge.ai.core.gl

import android.graphics.Bitmap
import android.util.Log
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings

/**
 * Crossfade overlay for clip B that starts block-blurred and resolves to sharp.
 *
 * The blur approximation deliberately uses bitmap down/up sampling instead of per-frame GL
 * overlay shaders because BitmapOverlay is the existing B-frame infrastructure.
 */
@UnstableApi
class BlurredCrossfadeBitmapOverlay(
    private val cache: CrossfadeFrameCache,
    private val fadeStartUs: Long,
    private val fadeEndUs: Long,
    private val maxDownsample: Float = 0.18f
) : BitmapOverlay() {

    private val tag = "BLUR_XFADE_OV"
    private var lastBitmap: Bitmap = Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
    private var lastSource: Bitmap? = null
    private var lastBucket: Int = -1
    private var generated: Bitmap? = null

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val intoWindowUs = (presentationTimeUs - fadeStartUs).coerceAtLeast(0L)
        val frame = cache.frameForOffset(intoWindowUs)
        if (frame != null) lastBitmap = frame

        val p = progress(presentationTimeUs)
        val bucket = ((1f - p) * 8f).toInt().coerceIn(0, 8)
        if (bucket == 0) return lastBitmap

        if (lastSource !== lastBitmap || lastBucket != bucket) {
            generated?.recycle()
            generated = scaledBlur(lastBitmap, bucket)
            lastSource = lastBitmap
            lastBucket = bucket
        }
        return generated ?: lastBitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val p = progress(presentationTimeUs)
        val alpha = p * p * (3f - 2f * p)
        return StaticOverlaySettings.Builder()
            .setAlphaScale(alpha)
            .setScale(1f, 1f)
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(0f, 0f)
            .build()
    }

    override fun release() {
        super.release()
        generated?.recycle()
        generated = null
        try { cache.release() } catch (_: Exception) {}
        Log.d(tag, "released")
    }

    private fun progress(presentationTimeUs: Long): Float = when {
        presentationTimeUs <= fadeStartUs -> 0f
        presentationTimeUs >= fadeEndUs -> 1f
        else -> ((presentationTimeUs - fadeStartUs).toFloat() / (fadeEndUs - fadeStartUs).toFloat()).coerceIn(0f, 1f)
    }

    private fun scaledBlur(source: Bitmap, bucket: Int): Bitmap {
        val blurStrength = bucket / 8f
        val scale = (1f - blurStrength) + (maxDownsample * blurStrength)
        val smallW = (source.width * scale).toInt().coerceAtLeast(2)
        val smallH = (source.height * scale).toInt().coerceAtLeast(2)
        val small = Bitmap.createScaledBitmap(source, smallW, smallH, true)
        val restored = Bitmap.createScaledBitmap(small, source.width, source.height, true)
        if (small !== source) small.recycle()
        return restored
    }
}
