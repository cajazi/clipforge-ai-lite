package com.clipforge.ai.core.text

import android.graphics.Bitmap
import android.graphics.Color
import com.clipforge.ai.core.overlay.OverlayLayer
import com.clipforge.ai.core.overlay.OverlayTransform
import com.clipforge.ai.domain.model.TextOverlay
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextOverlayRenderableTest {

    @Test
    fun `maps domain overlay to renderable contract`() {
        val overlay = textOverlay(
            id = "text-1",
            windowStartMs = 100L,
            windowEndMs = 1_200L,
            layer = OverlayLayer.SYSTEM,
            zIndex = 7
        )
        val renderable = TextOverlayRenderable(overlay, RecordingRasterizer())

        assertEquals("text-1", renderable.id)
        assertEquals(100L, renderable.windowStartMs)
        assertEquals(1_200L, renderable.windowEndMs)
        assertEquals(OverlayLayer.USER, renderable.layer)
        assertEquals(7, renderable.zIndex)
    }

    @Test
    fun `progress parameter is accepted and ignored for transform`() {
        val overlay = textOverlay(
            transform = OverlayTransform(
                xNorm = 0.25f,
                yNorm = 0.75f,
                scale = 1.8f,
                rotationDeg = 30f,
                alpha = 0.2f
            )
        )
        val renderable = TextOverlayRenderable(overlay, RecordingRasterizer())

        val atStart = renderable.transformAt(0f)
        val atEnd = renderable.transformAt(0.9f)

        assertEquals(atStart, atEnd)
        assertEquals(0.25f, atStart.xNorm)
        assertEquals(0.75f, atStart.yNorm)
        assertEquals(1.8f, atStart.scale)
        assertEquals(30f, atStart.rotationDeg)
        assertEquals(1f, atStart.alpha)
    }

    @Test
    fun `frameAt delegates to rasterizer without changing spec or dimensions`() {
        val spec = renderSpec(text = "Delegated")
        val bitmap = Bitmap.createBitmap(3, 4, Bitmap.Config.ARGB_8888)
        val rasterizer = RecordingRasterizer(bitmap)
        val renderable = TextOverlayRenderable(textOverlay(renderSpec = spec), rasterizer)

        val output = renderable.frameAt(progress = 0.8f, frameW = 1080, frameH = 1920)

        assertSame(bitmap, output)
        assertEquals(listOf(Call(spec, 1080, 1920)), rasterizer.calls)
    }

    private class RecordingRasterizer(
        private val bitmap: Bitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    ) : TextOverlayRasterizer {
        val calls = mutableListOf<Call>()

        override fun rasterize(spec: TextRenderSpec, frameW: Int, frameH: Int): Bitmap {
            calls += Call(spec, frameW, frameH)
            return bitmap
        }
    }

    private data class Call(
        val spec: TextRenderSpec,
        val frameW: Int,
        val frameH: Int
    )

    private fun textOverlay(
        id: String = "text",
        projectId: String = "project",
        windowStartMs: Long = 0L,
        windowEndMs: Long = 1_000L,
        layer: OverlayLayer = OverlayLayer.USER,
        zIndex: Int = 0,
        transform: OverlayTransform = OverlayTransform(
            xNorm = 0.5f,
            yNorm = 0.5f,
            scale = 1f,
            rotationDeg = 0f,
            alpha = 1f
        ),
        renderSpec: TextRenderSpec = renderSpec()
    ): TextOverlay =
        TextOverlay(
            id = id,
            projectId = projectId,
            windowStartMs = windowStartMs,
            windowEndMs = windowEndMs,
            layer = layer,
            zIndex = zIndex,
            transform = transform,
            renderSpec = renderSpec
        )

    private fun renderSpec(text: String = "Caption"): TextRenderSpec =
        TextRenderSpec(
            text = text,
            fontId = "default",
            fontSizeNorm = 0.08f,
            colorArgb = Color.WHITE,
            bgColorArgb = null,
            bold = false,
            italic = false,
            alignment = TextAlignment.CENTER
        )
}
