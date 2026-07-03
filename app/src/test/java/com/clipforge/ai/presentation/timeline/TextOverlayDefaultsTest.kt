package com.clipforge.ai.presentation.timeline

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class TextOverlayDefaultsTest {

    @Test
    fun `default text overlay trims text and anchors to playhead`() {
        val overlay = createDefaultTimelineTextOverlay(
            projectId = "project",
            text = "  Caption  ",
            timelineStartMs = 1_250L,
            totalDurationMs = 10_000L,
            zIndex = 3,
            id = "text-1"
        )

        assertNotNull(overlay)
        requireNotNull(overlay)
        assertEquals("text-1", overlay.id)
        assertEquals("Caption", overlay.renderSpec.text)
        assertEquals(1_250L, overlay.windowStartMs)
        assertEquals(4_250L, overlay.windowEndMs)
        assertEquals(3, overlay.zIndex)
    }

    @Test
    fun `default text overlay clamps end to project duration`() {
        val overlay = createDefaultTimelineTextOverlay(
            projectId = "project",
            text = "Caption",
            timelineStartMs = 9_500L,
            totalDurationMs = 10_000L,
            zIndex = 0,
            id = "text-1"
        )

        assertNotNull(overlay)
        requireNotNull(overlay)
        assertEquals(9_500L, overlay.windowStartMs)
        assertEquals(10_000L, overlay.windowEndMs)
    }

    @Test
    fun `blank text does not create overlay row`() {
        val overlay = createDefaultTimelineTextOverlay(
            projectId = "project",
            text = "   ",
            timelineStartMs = 0L,
            totalDurationMs = 10_000L,
            zIndex = 0
        )

        assertNull(overlay)
    }
}
