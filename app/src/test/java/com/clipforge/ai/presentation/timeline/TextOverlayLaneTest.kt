package com.clipforge.ai.presentation.timeline

import android.graphics.Color
import com.clipforge.ai.core.overlay.OverlayLayer
import com.clipforge.ai.core.overlay.OverlayTransform
import com.clipforge.ai.core.text.TextAlignment
import com.clipforge.ai.core.text.TextRenderSpec
import com.clipforge.ai.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Test

class TextOverlayLaneTest {

    @Test
    fun `chip models are ordered by z index start and id`() {
        val chips = buildTextOverlayChipUiModels(
            listOf(
                textOverlay(id = "b", startMs = 400L, endMs = 800L, zIndex = 1),
                textOverlay(id = "a", startMs = 200L, endMs = 500L, zIndex = 1),
                textOverlay(id = "c", startMs = 100L, endMs = 300L, zIndex = 0)
            )
        )

        assertEquals(listOf("c", "a", "b"), chips.map { it.id })
        assertEquals(listOf(200L, 300L, 400L), chips.map { it.durationMs })
    }

    @Test
    fun `chip placement follows px per ms lane contract`() {
        val chip = buildTextOverlayChipUiModels(
            listOf(textOverlay(id = "caption", startMs = 2_000L, endMs = 5_000L))
        ).single()

        val placement = textOverlayChipPlacement(chip, pxPerMs = 0.028f)

        assertEquals(56, placement.offsetPx)
        assertEquals(84, placement.widthPx)
    }

    @Test
    fun `short overlays keep minimum readable chip width`() {
        val chip = buildTextOverlayChipUiModels(
            listOf(textOverlay(id = "caption", startMs = 0L, endMs = 50L))
        ).single()

        val placement = textOverlayChipPlacement(chip, pxPerMs = 0.028f)

        assertEquals(0, placement.offsetPx)
        assertEquals(28, placement.widthPx)
    }

    private fun textOverlay(
        id: String,
        startMs: Long,
        endMs: Long,
        zIndex: Int = 0
    ): TextOverlay =
        TextOverlay(
            id = id,
            projectId = "project",
            windowStartMs = startMs,
            windowEndMs = endMs,
            layer = OverlayLayer.USER,
            zIndex = zIndex,
            transform = OverlayTransform(
                xNorm = 0.5f,
                yNorm = 0.5f,
                scale = 1f,
                rotationDeg = 0f,
                alpha = 1f
            ),
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
}
