@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.overlay

import android.graphics.Bitmap
import androidx.media3.effect.OverlayEffect
import com.clipforge.ai.core.gl.RenderableBitmapOverlay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OverlayExportStageTest {

    @Test
    fun `empty sources returns null`() = runBlocking {
        val effect = OverlayExportStage.build(
            projectId = PROJECT_ID,
            sources = emptyList(),
            timeMap = identityTimeMap(),
            frameW = 1080,
            frameH = 1920
        )

        assertNull(effect)
    }

    @Test
    fun `source with no renderables returns null`() = runBlocking {
        val source = FakeSource(emptyList())

        val effect = OverlayExportStage.build(
            projectId = PROJECT_ID,
            sources = listOf(source),
            timeMap = identityTimeMap(),
            frameW = 1080,
            frameH = 1920
        )

        assertNull(effect)
        assertEquals(listOf(PROJECT_ID), source.loadedProjectIds)
    }

    @Test
    fun `projectId is forwarded to every source`() = runBlocking {
        val first = FakeSource(emptyList())
        val second = FakeSource(emptyList())

        OverlayExportStage.build(
            projectId = PROJECT_ID,
            sources = listOf(first, second),
            timeMap = identityTimeMap(),
            frameW = 1080,
            frameH = 1920
        )

        assertEquals(listOf(PROJECT_ID), first.loadedProjectIds)
        assertEquals(listOf(PROJECT_ID), second.loadedProjectIds)
    }

    @Test
    fun `mixed user and system overlays are ordered user zIndex ascending then system`() = runBlocking {
        val overlays = OverlayExportStage.buildOverlays(
            projectId = PROJECT_ID,
            sources = listOf(
                FakeSource(
                    listOf(
                        renderable("system-low", OverlayLayer.SYSTEM, zIndex = -10),
                        renderable("user-high", OverlayLayer.USER, zIndex = 20),
                        renderable("user-low", OverlayLayer.USER, zIndex = 0),
                        renderable("system-high", OverlayLayer.SYSTEM, zIndex = 99)
                    )
                )
            ),
            timeMap = identityTimeMap(),
            frameW = 1080,
            frameH = 1920
        )

        assertEquals(listOf("user-low", "user-high", "system-low", "system-high"), overlays.map { it.renderableId })
    }

    @Test
    fun `build returns overlay effect when renderables exist`() = runBlocking {
        val effect = OverlayExportStage.build(
            projectId = PROJECT_ID,
            sources = listOf(FakeSource(listOf(renderable("overlay", OverlayLayer.USER, zIndex = 0)))),
            timeMap = identityTimeMap(),
            frameW = 1080,
            frameH = 1920
        )

        assertTrue(effect is OverlayEffect)
    }

    @Test
    fun `overlay window uses mapped composition time not raw timeline time`() = runBlocking {
        val map = TimelineToCompositionTimeMap.build(
            listOf(
                TimePiece(timelineMs = 1_000L, compositionMs = 1_000L),
                TimePiece(timelineMs = 1_000L, compositionMs = 500L),
                TimePiece(timelineMs = 3_000L, compositionMs = 3_000L)
            )
        )

        val overlays = OverlayExportStage.buildOverlays(
            projectId = PROJECT_ID,
            sources = listOf(
                FakeSource(
                    listOf(
                        renderable(
                            id = "offset-overlay",
                            layer = OverlayLayer.USER,
                            zIndex = 0,
                            windowStartMs = 2_000L,
                            windowEndMs = 3_000L
                        )
                    )
                )
            ),
            timeMap = map,
            frameW = 1080,
            frameH = 1920
        )

        assertEquals(1_500_000L, overlays.single().compositionWindowStartUs)
        assertEquals(2_500_000L, overlays.single().compositionWindowEndUs)
    }

    private class FakeSource(private val overlays: List<RenderableOverlay>) : OverlaySource {
        val loadedProjectIds = mutableListOf<String>()

        override suspend fun load(projectId: String): List<RenderableOverlay> {
            loadedProjectIds += projectId
            return overlays
        }
    }

    private fun renderable(
        id: String,
        layer: OverlayLayer,
        zIndex: Int,
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
                    xNorm = 0.5f,
                    yNorm = 0.5f,
                    scale = 1f,
                    rotationDeg = 0f,
                    alpha = 1f
                )

            override fun frameAt(progress: Float, frameW: Int, frameH: Int): Bitmap =
                Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
        }

    private fun identityTimeMap(): TimelineToCompositionTimeMap =
        TimelineToCompositionTimeMap.build(listOf(TimePiece(5_000L, 5_000L)))

    private companion object {
        const val PROJECT_ID = "project-1"
    }
}
