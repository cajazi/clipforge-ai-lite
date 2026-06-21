@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.gl

import android.graphics.Bitmap
import androidx.media3.common.OverlaySettings
import com.clipforge.ai.core.overlay.OverlayLayer
import com.clipforge.ai.core.overlay.OverlayTransform
import com.clipforge.ai.core.overlay.RenderableOverlay
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.abs

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RenderableBitmapOverlayNdcTest {

    @Test
    fun `center maps to ndc origin`() {
        val settings = overlayAt(xNorm = 0.5f, yNorm = 0.5f).settingsInside()

        assertPair(settings.getBackgroundFrameAnchor(), 0f, 0f)
    }

    @Test
    fun `top left maps to expected ndc corner`() {
        val settings = overlayAt(xNorm = 0f, yNorm = 0f).settingsInside()

        assertPair(settings.getBackgroundFrameAnchor(), -1f, 1f)
    }

    @Test
    fun `top right maps to expected ndc corner`() {
        val settings = overlayAt(xNorm = 1f, yNorm = 0f).settingsInside()

        assertPair(settings.getBackgroundFrameAnchor(), 1f, 1f)
    }

    @Test
    fun `bottom left maps to expected ndc corner`() {
        val settings = overlayAt(xNorm = 0f, yNorm = 1f).settingsInside()

        assertPair(settings.getBackgroundFrameAnchor(), -1f, -1f)
    }

    @Test
    fun `bottom right maps to expected ndc corner`() {
        val settings = overlayAt(xNorm = 1f, yNorm = 1f).settingsInside()

        assertPair(settings.getBackgroundFrameAnchor(), 1f, -1f)
    }

    @Test
    fun `y axis sign maps top to positive media3 ndc y`() {
        val settings = overlayAt(xNorm = 0.5f, yNorm = 0f).settingsInside()

        assertEquals(1f, settings.getBackgroundFrameAnchor().second, EPSILON)
    }

    @Test
    fun `outside window has alpha zero and inside window uses renderable alpha`() {
        val overlay = overlayAt(xNorm = 0.5f, yNorm = 0.5f, alpha = 0.65f)

        assertEquals(0f, overlay.getOverlaySettings(999L).getAlphaScale(), EPSILON)
        assertEquals(0.65f, overlay.getOverlaySettings(1_500L).getAlphaScale(), EPSILON)
        assertEquals(0f, overlay.getOverlaySettings(2_000L).getAlphaScale(), EPSILON)
    }

    @Test
    fun `inside window uses media3 rotation support`() {
        val settings = overlayAt(xNorm = 0.5f, yNorm = 0.5f, rotationDeg = 30f).settingsInside()

        assertEquals(30f, settings.getRotationDegrees(), EPSILON)
    }

    private fun RenderableBitmapOverlay.settingsInside(): OverlaySettings =
        getOverlaySettings(1_500L)

    private fun overlayAt(
        xNorm: Float,
        yNorm: Float,
        alpha: Float = 1f,
        rotationDeg: Float = 0f
    ): RenderableBitmapOverlay =
        RenderableBitmapOverlay(
            renderable = renderable(xNorm, yNorm, alpha, rotationDeg),
            windowStartUs = 1_000L,
            windowEndUs = 2_000L,
            frameW = 1080,
            frameH = 1920
        )

    private fun renderable(
        xNorm: Float,
        yNorm: Float,
        alpha: Float,
        rotationDeg: Float
    ): RenderableOverlay =
        object : RenderableOverlay {
            override val id: String = "renderable"
            override val windowStartMs: Long = 0L
            override val windowEndMs: Long = 1_000L
            override val layer: OverlayLayer = OverlayLayer.USER
            override val zIndex: Int = 0

            override fun transformAt(progress: Float): OverlayTransform =
                OverlayTransform(
                    xNorm = xNorm,
                    yNorm = yNorm,
                    scale = 1f,
                    rotationDeg = rotationDeg,
                    alpha = alpha
                )

            override fun frameAt(progress: Float, frameW: Int, frameH: Int): Bitmap =
                Bitmap.createBitmap(2, 2, Bitmap.Config.ARGB_8888)
        }

    private fun assertPair(pair: android.util.Pair<Float, Float>, expectedX: Float, expectedY: Float) {
        assertEquals(expectedX, pair.first, EPSILON)
        assertEquals(expectedY, pair.second, EPSILON)
        assert(abs(pair.first - expectedX) <= EPSILON)
        assert(abs(pair.second - expectedY) <= EPSILON)
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
