package com.clipforge.ai.core.text

import android.graphics.Bitmap
import com.clipforge.ai.core.overlay.OverlayLayer
import com.clipforge.ai.core.overlay.OverlayTransform
import com.clipforge.ai.core.overlay.RenderableOverlay
import com.clipforge.ai.domain.model.TextOverlay

class TextOverlayRenderable(
    private val overlay: TextOverlay,
    private val rasterizer: TextOverlayRasterizer
) : RenderableOverlay {
    override val id: String = overlay.id
    override val windowStartMs: Long = overlay.windowStartMs
    override val windowEndMs: Long = overlay.windowEndMs
    override val layer: OverlayLayer = OverlayLayer.USER
    override val zIndex: Int = overlay.zIndex

    override fun transformAt(progress: Float): OverlayTransform =
        OverlayTransform(
            xNorm = overlay.transform.xNorm,
            yNorm = overlay.transform.yNorm,
            scale = overlay.transform.scale,
            rotationDeg = overlay.transform.rotationDeg,
            alpha = 1f
        )

    override fun frameAt(progress: Float, frameW: Int, frameH: Int): Bitmap =
        rasterizer.rasterize(overlay.renderSpec, frameW, frameH)
}
