package com.clipforge.ai.core.text

import android.graphics.Bitmap
import android.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class TextOverlayRasterizerTest {

    @Test
    fun `identical spec uses cache hit`() {
        val rasterizer = CanvasTextOverlayRasterizer()
        val spec = baseSpec()

        val first = rasterizer.rasterize(spec, FRAME_W, FRAME_H)
        val second = rasterizer.rasterize(spec, FRAME_W, FRAME_H)

        assertSame(first, second)
        assertEquals(1, rasterizer.cacheMissCount)
        assertEquals(1, rasterizer.cacheHitCount)
    }

    @Test
    fun `style change uses cache miss`() {
        val rasterizer = CanvasTextOverlayRasterizer()
        val spec = baseSpec()

        val regular = rasterizer.rasterize(spec, FRAME_W, FRAME_H)
        val bold = rasterizer.rasterize(spec.copy(bold = true), FRAME_W, FRAME_H)

        assertNotSame(regular, bold)
        assertEquals(2, rasterizer.cacheMissCount)
        assertEquals(0, rasterizer.cacheHitCount)
    }

    @Test
    fun `multiline rendering produces non-empty bitmap`() {
        val bitmap = CanvasTextOverlayRasterizer().rasterize(
            baseSpec(text = "First line\nSecond line\nThird line"),
            FRAME_W,
            FRAME_H
        )

        assertTrue(bitmap.width > 1)
        assertTrue(bitmap.height > 1)
        assertTrue(bitmap.nonTransparentPixelCount() > 0)
    }

    @Test
    fun `emoji rendering produces non-empty bitmap`() {
        val bitmap = CanvasTextOverlayRasterizer().rasterize(
            baseSpec(text = "Hello \uD83D\uDE80"),
            FRAME_W,
            FRAME_H
        )

        assertTrue(bitmap.width > 1)
        assertTrue(bitmap.height > 1)
        assertTrue(bitmap.nonTransparentPixelCount() > 0)
    }

    @Test
    fun `background rendering draws rounded pill behind text`() {
        val bitmap = CanvasTextOverlayRasterizer().rasterize(
            baseSpec(bgColorArgb = Color.argb(170, 12, 34, 56)),
            FRAME_W,
            FRAME_H
        )

        assertNotEquals(Color.TRANSPARENT, bitmap.getPixel(bitmap.width / 2, 2))
        assertEquals(Color.TRANSPARENT, bitmap.getPixel(0, 0))
    }

    @Test
    fun `raster output is deterministic`() {
        val spec = baseSpec(text = "Deterministic\nText")
        val first = CanvasTextOverlayRasterizer().rasterize(spec, FRAME_W, FRAME_H)
        val second = CanvasTextOverlayRasterizer().rasterize(spec, FRAME_W, FRAME_H)

        assertTrue(first.sameAs(second))
        assertEquals(first.pixelHash(), second.pixelHash())
    }

    @Test
    fun `alignment moves shorter multiline rows`() {
        val left = CanvasTextOverlayRasterizer().rasterize(
            baseSpec(text = "WWWWWWWW\nHi", alignment = TextAlignment.LEFT),
            FRAME_W,
            FRAME_H
        )
        val center = CanvasTextOverlayRasterizer().rasterize(
            baseSpec(text = "WWWWWWWW\nHi", alignment = TextAlignment.CENTER),
            FRAME_W,
            FRAME_H
        )
        val right = CanvasTextOverlayRasterizer().rasterize(
            baseSpec(text = "WWWWWWWW\nHi", alignment = TextAlignment.RIGHT),
            FRAME_W,
            FRAME_H
        )

        val leftBounds = left.nonTransparentBoundsInBottomHalf()
        val centerBounds = center.nonTransparentBoundsInBottomHalf()
        val rightBounds = right.nonTransparentBoundsInBottomHalf()

        assertTrue(leftBounds.centerX < centerBounds.centerX)
        assertTrue(centerBounds.centerX < rightBounds.centerX)
    }

    private fun baseSpec(
        text: String = "Caption",
        bgColorArgb: Int? = null,
        alignment: TextAlignment = TextAlignment.CENTER
    ): TextRenderSpec =
        TextRenderSpec(
            text = text,
            fontId = "default",
            fontSizeNorm = 0.08f,
            colorArgb = Color.WHITE,
            bgColorArgb = bgColorArgb,
            bold = false,
            italic = false,
            alignment = alignment
        )

    private fun Bitmap.nonTransparentPixelCount(): Int {
        var count = 0
        for (y in 0 until height) {
            for (x in 0 until width) {
                if (Color.alpha(getPixel(x, y)) != 0) count += 1
            }
        }
        return count
    }

    private fun Bitmap.pixelHash(): Int {
        var result = 1
        for (y in 0 until height) {
            for (x in 0 until width) {
                result = 31 * result + getPixel(x, y)
            }
        }
        return result
    }

    private fun Bitmap.nonTransparentBoundsInBottomHalf(): Bounds {
        var minX = width
        var maxX = -1
        for (y in height / 2 until height) {
            for (x in 0 until width) {
                if (Color.alpha(getPixel(x, y)) != 0) {
                    minX = minOf(minX, x)
                    maxX = maxOf(maxX, x)
                }
            }
        }
        assertTrue("expected visible pixels in bottom half", maxX >= minX)
        return Bounds(minX, maxX)
    }

    private data class Bounds(val minX: Int, val maxX: Int) {
        val centerX: Int get() = (minX + maxX) / 2
    }

    private companion object {
        const val FRAME_W = 1080
        const val FRAME_H = 1920
    }
}
