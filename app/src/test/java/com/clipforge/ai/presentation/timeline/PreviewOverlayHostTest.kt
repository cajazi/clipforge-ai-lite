package com.clipforge.ai.presentation.timeline

import android.graphics.Bitmap
import android.graphics.Color
import com.clipforge.ai.core.overlay.ExportOverlayFrameAdapter
import com.clipforge.ai.core.overlay.OverlayLayer
import com.clipforge.ai.core.overlay.OverlayTransform
import com.clipforge.ai.core.overlay.PreviewOverlayFrameAdapter
import com.clipforge.ai.core.text.TextAlignment
import com.clipforge.ai.core.text.TextOverlayRasterizer
import com.clipforge.ai.core.text.TextRenderSpec
import com.clipforge.ai.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewOverlayHostTest {

    @Test
    fun `preview frame state uses supplied timeline time not wall clock`() {
        val overlay = textOverlay(id = "caption", startMs = 1_000L, endMs = 2_000L)

        val before = previewOverlayFrameStateAt(listOf(overlay), 999L, NoopRasterizer)
        val atStart = previewOverlayFrameStateAt(listOf(overlay), 1_000L, NoopRasterizer)
        val atEnd = previewOverlayFrameStateAt(listOf(overlay), 2_000L, NoopRasterizer)

        assertTrue(before.activeOverlays.isEmpty())
        assertEquals(listOf("caption"), atStart.activeOverlays.map { it.id })
        assertTrue(atEnd.activeOverlays.isEmpty())
    }

    @Test
    fun `preview and export adapters evaluate equivalent frame state for same engine`() {
        val engine = buildPreviewOverlayFrameEngine(
            textOverlays = listOf(textOverlay(id = "caption", startMs = 1_000L, endMs = 2_000L)),
            rasterizer = NoopRasterizer
        )
        val preview = PreviewOverlayFrameAdapter(engine)
        val export = ExportOverlayFrameAdapter(engine)

        assertEquals(preview.evaluateAtTimeUs(timelineMsToUs(1_500L)), export.evaluateAtTimeUs(timelineMsToUs(1_500L)))
        assertEquals(preview.evaluateFrame(45L), export.evaluateFrame(45L))
    }

    @Test
    fun `preview engine keeps stable z index and source ordering`() {
        val state = previewOverlayFrameStateAt(
            textOverlays = listOf(
                textOverlay(id = "z-high", startMs = 0L, endMs = 2_000L, zIndex = 10),
                textOverlay(id = "tie-late", startMs = 500L, endMs = 2_000L, zIndex = 5),
                textOverlay(id = "z-low", startMs = 0L, endMs = 2_000L, zIndex = 0),
                textOverlay(id = "tie-early", startMs = 0L, endMs = 2_000L, zIndex = 5)
            ),
            timelineTimeMs = 1_000L,
            rasterizer = NoopRasterizer
        )

        assertEquals(listOf("z-low", "tie-early", "tie-late", "z-high"), state.activeOverlays.map { it.id })
    }

    @Test
    fun `viewport transform is visual only and does not mutate text overlay data`() {
        val overlay = textOverlay(
            id = "caption",
            startMs = 0L,
            endMs = 2_000L,
            transform = OverlayTransform(
                xNorm = 0.25f,
                yNorm = 0.75f,
                scale = 1.4f,
                rotationDeg = 12f,
                alpha = 0.8f
            )
        )
        val originalTransform = overlay.transform
        val layer = previewOverlayFrameStateAt(listOf(overlay), 1_000L, NoopRasterizer).activeOverlays.single()

        val placement = previewOverlayPlacement(
            layer = layer,
            bitmapWidthPx = 100,
            bitmapHeightPx = 40,
            frameWidthPx = 1_000,
            frameHeightPx = 2_000,
            viewportTransform = PreviewOverlayViewportTransform(scale = 2f, panX = 15f, panY = -10f)
        )

        assertEquals(originalTransform, overlay.transform)
        assertEquals(415f, placement.translationX, EPSILON)
        assertEquals(2_950f, placement.translationY, EPSILON)
        assertEquals(2.8f, placement.scale, EPSILON)
        assertEquals(1f, placement.alpha, EPSILON)
        assertEquals(12f, placement.rotationDeg, EPSILON)
    }

    @Test
    fun `negative timeline time is clamped before engine evaluation`() {
        assertEquals(0L, timelineMsToUs(-10L))
    }

    private object NoopRasterizer : TextOverlayRasterizer {
        override fun rasterize(spec: TextRenderSpec, frameW: Int, frameH: Int): Bitmap =
            throw UnsupportedOperationException("Preview host unit tests do not rasterize")
    }

    private fun textOverlay(
        id: String,
        startMs: Long,
        endMs: Long,
        zIndex: Int = 0,
        transform: OverlayTransform = OverlayTransform(
            xNorm = 0.5f,
            yNorm = 0.5f,
            scale = 1f,
            rotationDeg = 0f,
            alpha = 1f
        )
    ): TextOverlay =
        TextOverlay(
            id = id,
            projectId = "project",
            windowStartMs = startMs,
            windowEndMs = endMs,
            layer = OverlayLayer.USER,
            zIndex = zIndex,
            transform = transform,
            renderSpec = TextRenderSpec(
                text = id,
                fontId = "default",
                fontSizeNorm = 0.08f,
                colorArgb = Color.WHITE,
                bgColorArgb = null,
                bold = false,
                italic = false,
                alignment = TextAlignment.CENTER
            )
        )

    private companion object {
        const val EPSILON = 0.0001f
    }
}
