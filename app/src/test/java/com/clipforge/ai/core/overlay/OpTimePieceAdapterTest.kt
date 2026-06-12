package com.clipforge.ai.core.overlay

import com.clipforge.ai.core.gl.CrossfadeRenderPlan
import org.junit.Assert.assertEquals
import org.junit.Test

class OpTimePieceAdapterTest {

    @Test
    fun `plain clip maps source span one to one`() {
        val pieces = OpTimePieceAdapter.toTimePieces(
            listOf(CrossfadeRenderPlan.Op.PlainClip("a.mp4", 250L, 1_750L))
        )

        assertEquals(listOf(TimePiece(timelineMs = 1_500L, compositionMs = 1_500L)), pieces)
    }

    @Test
    fun `dip maps serial tails one to one`() {
        val pieces = OpTimePieceAdapter.toTimePieces(
            listOf(
                CrossfadeRenderPlan.Op.DipToColor(
                    pathA = "a.mp4",
                    aTailStartMs = 2_250L,
                    aEndMs = 2_500L,
                    pathB = "b.mp4",
                    bHeadStartMs = 0L,
                    bHeadEndMs = 250L,
                    halfDurationMs = 250L,
                    colorInt = 0
                )
            )
        )

        assertEquals(listOf(TimePiece(timelineMs = 500L, compositionMs = 500L)), pieces)
    }

    @Test
    fun `overlap families consume two timeline windows into one composition window`() {
        val ops = listOf(
            CrossfadeRenderPlan.Op.Crossfade("a", 2_000L, 2_500L, "b", 0L, 500L),
            CrossfadeRenderPlan.Op.Flash("a", 2_000L, 2_500L, "b", 0L, 500L, 0, "FLASH"),
            CrossfadeRenderPlan.Op.FilmBurn("a", 2_000L, 2_500L, "b", 0L, 500L, "FILM_BURN"),
            CrossfadeRenderPlan.Op.Slide("a", 2_000L, 2_500L, "b", 0L, 500L, "LEFT"),
            CrossfadeRenderPlan.Op.Push("a", 2_000L, 2_500L, "b", 0L, 500L, "LEFT"),
            CrossfadeRenderPlan.Op.Zoom("a", 2_000L, 2_500L, "b", 0L, 500L, "IN"),
            CrossfadeRenderPlan.Op.Rotation("a", 2_000L, 2_500L, "b", 0L, 500L, "SPIN"),
            CrossfadeRenderPlan.Op.Cube("a", 2_000L, 2_500L, "b", 0L, 500L, "LEFT"),
            CrossfadeRenderPlan.Op.Flip("a", 2_000L, 2_500L, "b", 0L, 500L, "LEFT"),
            CrossfadeRenderPlan.Op.PageTurn("a", 2_000L, 2_500L, "b", 0L, 500L, "LEFT"),
            CrossfadeRenderPlan.Op.Blur("a", 2_000L, 2_500L, "b", 0L, 500L, "BLUR"),
            CrossfadeRenderPlan.Op.WhipPan("a", 2_000L, 2_500L, "b", 0L, 500L, "LEFT"),
            CrossfadeRenderPlan.Op.MotionBlur("a", 2_000L, 2_500L, "b", 0L, 500L, "LEFT"),
            CrossfadeRenderPlan.Op.Wipe("a", 2_000L, 2_500L, "b", 0L, 500L, "LEFT"),
            CrossfadeRenderPlan.Op.GlitchPro("a", 2_000L, 2_500L, "b", 0L, 500L, "GLITCH")
        )

        val pieces = OpTimePieceAdapter.toTimePieces(ops)

        assertEquals(ops.size, pieces.size)
        pieces.forEach { piece ->
            assertEquals(TimePiece(timelineMs = 1_000L, compositionMs = 500L), piece)
        }
    }
}
