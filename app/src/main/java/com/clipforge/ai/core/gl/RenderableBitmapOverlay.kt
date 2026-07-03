package com.clipforge.ai.core.gl

import android.graphics.Bitmap
import androidx.media3.common.OverlaySettings
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BitmapOverlay
import androidx.media3.effect.StaticOverlaySettings
import com.clipforge.ai.core.overlay.DeterministicOverlayFrameClock
import com.clipforge.ai.core.overlay.ExportOverlayFrameAdapter
import com.clipforge.ai.core.overlay.OverlayFrameEngine
import com.clipforge.ai.core.overlay.OverlayFrameEvaluator
import com.clipforge.ai.core.overlay.OverlayFrameInput
import com.clipforge.ai.core.overlay.OverlayFrameLayerState
import com.clipforge.ai.core.overlay.RenderableOverlay

@UnstableApi
class RenderableBitmapOverlay(
    private val renderable: RenderableOverlay,
    private val windowStartUs: Long,
    private val windowEndUs: Long,
    private val frameW: Int,
    private val frameH: Int,
    frameEvaluator: OverlayFrameEvaluator = OverlayFrameEngine.fromCompositionOverlays(
        overlays = listOf(
            OverlayFrameInput(
                renderable = renderable,
                windowStartUs = windowStartUs,
                windowEndUs = windowEndUs,
                sourceOrder = 0
            )
        ),
        frameClock = DeterministicOverlayFrameClock()
    )
) : BitmapOverlay() {

    private val retainedBitmaps = mutableListOf<Bitmap>()
    private val frameAdapter = ExportOverlayFrameAdapter(frameEvaluator)
    private var lastBitmap: Bitmap = renderable.frameAt(0f, frameW, frameH).also { retainedBitmaps += it }

    val compositionWindowStartUs: Long get() = windowStartUs
    val compositionWindowEndUs: Long get() = windowEndUs
    val renderableId: String get() = renderable.id

    override fun getBitmap(presentationTimeUs: Long): Bitmap {
        val layer = layerAt(presentationTimeUs)
        if (layer != null) {
            val bitmap = renderable.frameAt(layer.progress, frameW, frameH)
            lastBitmap = bitmap
            if (retainedBitmaps.none { it === bitmap }) {
                retainedBitmaps += bitmap
            }
        }
        return lastBitmap
    }

    override fun getOverlaySettings(presentationTimeUs: Long): OverlaySettings {
        val layer = layerAt(presentationTimeUs)
        if (layer == null) {
            return StaticOverlaySettings.Builder()
                .setAlphaScale(0f)
                .setScale(1f, 1f)
                .setRotationDegrees(0f)
                .setOverlayFrameAnchor(0f, 0f)
                .setBackgroundFrameAnchor(0f, 0f)
                .build()
        }

        val transform = layer.transform
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

    private fun layerAt(presentationTimeUs: Long): OverlayFrameLayerState? =
        frameAdapter.evaluateAtTimeUs(presentationTimeUs)
            .activeOverlays
            .firstOrNull { layer -> layer.id == renderable.id }

    companion object {
        fun normalizedXToNdc(xNorm: Float): Float = xNorm * 2f - 1f
        fun normalizedYToNdc(yNorm: Float): Float = 1f - yNorm * 2f
    }
}
