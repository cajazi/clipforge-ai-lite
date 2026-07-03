package com.clipforge.ai.core.overlay

import android.graphics.Bitmap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OverlayFrameEngineTest {

    @Test
    fun `overlay is active exactly at inclusive start`() {
        val engine = engineFor(
            renderable("caption"),
            windowStartUs = 1_000_000L,
            windowEndUs = 2_000_000L
        )

        val state = engine.frameStateAtTimeUs(1_000_000L)

        assertEquals(listOf("caption"), state.activeOverlays.map { it.id })
        assertEquals(0f, state.activeOverlays.single().progress, EPSILON)
    }

    @Test
    fun `overlay is not active exactly at exclusive end`() {
        val engine = engineFor(
            renderable("caption"),
            windowStartUs = 1_000_000L,
            windowEndUs = 2_000_000L
        )

        val state = engine.frameStateAtTimeUs(2_000_000L)

        assertTrue(state.activeOverlays.isEmpty())
    }

    @Test
    fun `overlay is inactive before start and after end`() {
        val engine = engineFor(
            renderable("caption"),
            windowStartUs = 1_000_000L,
            windowEndUs = 2_000_000L
        )

        assertTrue(engine.frameStateAtTimeUs(999_999L).activeOverlays.isEmpty())
        assertTrue(engine.frameStateAtTimeUs(2_000_001L).activeOverlays.isEmpty())
    }

    @Test
    fun `active overlays sort by zIndex and preserve stable source order for ties`() {
        val engine = OverlayFrameEngine.fromCompositionOverlays(
            listOf(
                input(renderable("top", zIndex = 20), order = 0),
                input(renderable("tie-first", zIndex = 10), order = 1),
                input(renderable("bottom", zIndex = 0), order = 2),
                input(renderable("tie-second", zIndex = 10), order = 3)
            )
        )

        val state = engine.frameStateAtTimeUs(1_000_000L)

        assertEquals(
            listOf("bottom", "tie-first", "tie-second", "top"),
            state.activeOverlays.map { it.id }
        )
    }

    @Test
    fun `system overlays retain layer order after user overlays`() {
        val engine = OverlayFrameEngine.fromCompositionOverlays(
            listOf(
                input(renderable("system-low", layer = OverlayLayer.SYSTEM, zIndex = -10), order = 0),
                input(renderable("user-high", layer = OverlayLayer.USER, zIndex = 99), order = 1),
                input(renderable("system-high", layer = OverlayLayer.SYSTEM, zIndex = 10), order = 2)
            )
        )

        val state = engine.frameStateAtTimeUs(1_000_000L)

        assertEquals(listOf("user-high", "system-low", "system-high"), state.activeOverlays.map { it.id })
    }

    @Test
    fun `preview and export adapters evaluate the same frame state`() {
        val engine = engineFor(
            renderable("caption"),
            windowStartUs = 1_000_000L,
            windowEndUs = 2_000_000L
        )
        val preview = PreviewOverlayFrameAdapter(engine)
        val export = ExportOverlayFrameAdapter(engine)

        assertEquals(
            preview.evaluateAtTimeUs(1_500_000L),
            export.evaluateAtTimeUs(1_500_000L)
        )
        assertEquals(
            preview.evaluateFrame(45L),
            export.evaluateFrame(45L)
        )
    }

    @Test
    fun `timeline windows are mapped to composition windows before activation`() {
        val timeMap = TimelineToCompositionTimeMap.build(
            listOf(
                TimePiece(timelineMs = 1_000L, compositionMs = 1_000L),
                TimePiece(timelineMs = 1_000L, compositionMs = 500L),
                TimePiece(timelineMs = 3_000L, compositionMs = 3_000L)
            )
        )
        val engine = OverlayFrameEngine.fromTimelineRenderables(
            renderables = listOf(
                renderable(
                    id = "mapped",
                    windowStartMs = 2_000L,
                    windowEndMs = 3_000L
                )
            ),
            timeMap = timeMap
        )

        val overlay = engine.orderedOverlays.single()

        assertEquals(1_500_000L, overlay.windowStartUs)
        assertEquals(2_500_000L, overlay.windowEndUs)
        assertEquals(listOf("mapped"), engine.frameStateAtTimeUs(1_500_000L).activeOverlays.map { it.id })
        assertTrue(engine.frameStateAtTimeUs(2_500_000L).activeOverlays.isEmpty())
    }

    @Test
    fun `zero duration overlay is never active`() {
        val engine = engineFor(
            renderable("empty"),
            windowStartUs = 1_000_000L,
            windowEndUs = 1_000_000L
        )

        assertTrue(engine.orderedOverlays.isEmpty())
        assertTrue(engine.frameStateAtTimeUs(1_000_000L).activeOverlays.isEmpty())
    }

    private fun engineFor(
        renderable: RenderableOverlay,
        windowStartUs: Long,
        windowEndUs: Long
    ): OverlayFrameEngine =
        OverlayFrameEngine.fromCompositionOverlays(
            listOf(
                OverlayFrameInput(
                    renderable = renderable,
                    windowStartUs = windowStartUs,
                    windowEndUs = windowEndUs,
                    sourceOrder = 0
                )
            )
        )

    private fun input(
        renderable: RenderableOverlay,
        order: Int,
        windowStartUs: Long = 0L,
        windowEndUs: Long = 2_000_000L
    ): OverlayFrameInput =
        OverlayFrameInput(
            renderable = renderable,
            windowStartUs = windowStartUs,
            windowEndUs = windowEndUs,
            sourceOrder = order
        )

    private fun renderable(
        id: String,
        layer: OverlayLayer = OverlayLayer.USER,
        zIndex: Int = 0,
        windowStartMs: Long = 0L,
        windowEndMs: Long = 1_000L
    ): RenderableOverlay =
        object : RenderableOverlay {
            override val id: String = id
            override val windowStartMs: Long = windowStartMs
            override val windowEndMs: Long = windowEndMs
            override val layer: OverlayLayer = layer
            override val zIndex: Int = zIndex

            override fun transformAt(progress: Float): OverlayTransform =
                OverlayTransform(
                    xNorm = progress,
                    yNorm = 0.5f,
                    scale = 1f,
                    rotationDeg = 0f,
                    alpha = 1f
                )

            override fun frameAt(progress: Float, frameW: Int, frameH: Int): Bitmap =
                throw UnsupportedOperationException("Engine tests do not rasterize bitmaps")
        }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
