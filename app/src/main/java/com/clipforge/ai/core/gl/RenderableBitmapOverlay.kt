package com.clipforge.ai.core.gl

import android.graphics.Bitmap
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import com.clipforge.ai.core.overlay.RenderableOverlay

@UnstableApi
class RenderableBitmapOverlay(
    private val renderable: RenderableOverlay,
    private val windowStartUs: Long,
    private val windowEndUs: Long,
    private val frameW: Int,
    private val frameH: Int
) : BitmapOverlay() {

    private val retainedBitmaps = mutableListOf<Bitmap>()
    private var lastBitmap: Bitmap = renderable.frameAt(0f, frameW, frameH).also { retainedBitmaps += it }

    val compositionWindowStartUs: Long get() = windowStartUs
    val compositionWindowEndUs: Long get() = windowEndUs
    val renderableId: String get() = renderable.id

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        if (presentationTimeUs in windowStartUs until windowEndUs) {
            val bitmap = renderable.frameAt(progressAt(presentationTimeUs), frameW, frameH)
            lastBitmap = bitmap
            if (retainedBitmaps.none { it === bitmap }) {
                retainedBitmaps += bitmap
            }
        }
        return lastBitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        if (presentationTimeUs !in windowStartUs until windowEndUs) {
            return StaticOverlaySettings.Builder()
                .setAlphaScale(0f)
                .setScale(1f, 1f)
                .setRotationDegrees(0f)
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, 0f)
                .build()
        }

        val transform = renderable.transformAt(progressAt(presentationTimeUs))
        return StaticOverlaySettings.Builder()
            .setAlphaScale(transform.alpha)
            .setScale(transform.scale, transform.scale)
            .setRotationDegrees(transform.rotationDeg)
            .setOverlayFrameAnchor(0f, 0f)
            .setBackgroundFrameAnchor(
                normalizedXToNdc(transform.xNorm),
                normalizedYToNdc(transform.yNorm)
            )
            .build()
    }

    override fun release() {
        // TODO(C10.5b): recycle retained export-owned bitmaps after export completion owns lifecycle.
        retainedBitmaps.clear()
        super.release()
    }

    private fun progressAt(presentationTimeUs: Long): Float {
        val spanUs = (windowEndUs - windowStartUs).coerceAtLeast(1L)
        return ((presentationTimeUs - windowStartUs).toFloat() / spanUs.toFloat()).coerceIn(0f, 1f)
    }

    companion object {
        fun normalizedXToNdc(xNorm: Float): Float = xNorm * 2f - 1f
        fun normalizedYToNdc(yNorm: Float): Float = 1f - yNorm * 2f
    }
}
