package com.clipforge.ai.presentation.timeline

import android.graphics.Color
import com.clipforge.ai.core.overlay.OverlayLayer
import com.clipforge.ai.core.overlay.OverlayTransform
import com.clipforge.ai.core.text.TextAlignment
import com.clipforge.ai.core.text.TextRenderSpec
import com.clipforge.ai.domain.model.TextOverlay
import com.clipforge.ai.domain.selection.SelectionTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    @Test
    fun `selected chip state is based on text overlay id equality`() {
        val chips = buildTextOverlayChipUiModels(
            listOf(
                textOverlay(id = "text-1", startMs = 0L, endMs = 1_000L),
                textOverlay(id = "text-2", startMs = 1_000L, endMs = 2_000L)
            ),
            selectedTextOverlayId = "text-2"
        )

        assertFalse(chips.first { it.id == "text-1" }.isSelected)
        assertTrue(chips.first { it.id == "text-2" }.isSelected)
    }

    @Test
    fun `chip selection emits correct text overlay id`() {
        val chip = buildTextOverlayChipUiModels(
            listOf(textOverlay(id = "text-1", startMs = 0L, endMs = 1_000L))
        ).single()

        assertEquals("text-1", textOverlayChipSelectionId(chip))
    }

    @Test
    fun `selected chip state does not mutate text overlay object`() {
        val overlay = textOverlay(id = "text-1", startMs = 0L, endMs = 1_000L)
        val original = overlay.copy()

        buildTextOverlayChipUiModels(listOf(overlay), selectedTextOverlayId = "text-1")

        assertEquals(original, overlay)
    }

    @Test
    fun `selected text overlay id is extracted only from text selection target`() {
        assertEquals("text-1", selectedTextOverlayId(SelectionTarget.TextOverlay("text-1")))
        assertEquals(null, selectedTextOverlayId(SelectionTarget.Clip("clip-1")))
        assertEquals(null, selectedTextOverlayId(SelectionTarget.Effect("effect-1")))
        assertEquals(null, selectedTextOverlayId(SelectionTarget.None))
    }

    @Test
    fun `stale selected text overlay clears only when selected overlay disappears`() {
        val selected = SelectionTarget.TextOverlay("text-1")
        val overlays = listOf(textOverlay(id = "text-1", startMs = 0L, endMs = 1_000L))

        assertFalse(shouldClearStaleSelectedTextOverlay(selected, overlays))
        assertTrue(shouldClearStaleSelectedTextOverlay(selected, emptyList()))
        assertFalse(shouldClearStaleSelectedTextOverlay(SelectionTarget.Clip("clip-1"), emptyList()))
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
