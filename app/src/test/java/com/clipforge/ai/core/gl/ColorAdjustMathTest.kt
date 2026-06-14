package com.clipforge.ai.core.gl

import com.clipforge.ai.core.effects.BRIGHTNESS
import com.clipforge.ai.core.effects.CONTRAST
import com.clipforge.ai.core.effects.ParamProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class ColorAdjustMathTest {

    @Test
    fun identityAtBrightnessOneContrastOne() {
        val result = applyColorAdjust(0.25f, 0.5f, 0.75f, 0.4f)

        assertPixel(result, red = 0.25f, green = 0.5f, blue = 0.75f, alpha = 0.4f)
    }

    @Test
    fun brightnessZeroProducesBlackRgb() {
        val result = applyColorAdjust(0.25f, 0.5f, 0.75f, 0.8f, brightness = 0f)

        assertPixel(result, red = 0f, green = 0f, blue = 0f, alpha = 0.8f)
    }

    @Test
    fun brightnessTwoBoostsAndClampsRgb() {
        val result = applyColorAdjust(0.2f, 0.5f, 0.8f, 0.9f, brightness = 2f)

        assertPixel(result, red = 0.4f, green = 1f, blue = 1f, alpha = 0.9f)
    }

    @Test
    fun contrastZeroProducesMidGreyBeforeBrightness() {
        val result = applyColorAdjust(0.1f, 0.5f, 0.9f, 0.6f, contrast = 0f)

        assertPixel(result, red = 0.5f, green = 0.5f, blue = 0.5f, alpha = 0.6f)
    }

    @Test
    fun contrastOneIsIdentity() {
        val result = applyColorAdjust(0.1f, 0.5f, 0.9f, 1f, contrast = 1f)

        assertPixel(result, red = 0.1f, green = 0.5f, blue = 0.9f, alpha = 1f)
    }

    @Test
    fun contrastTwoIncreasesContrastAroundMidpoint() {
        val result = applyColorAdjust(0.25f, 0.5f, 0.75f, 1f, contrast = 2f)

        assertPixel(result, red = 0f, green = 0.5f, blue = 1f, alpha = 1f)
    }

    @Test
    fun alphaIsPreserved() {
        val result = applyColorAdjust(0.2f, 0.3f, 0.4f, 0.37f, brightness = 2f, contrast = 2f)

        assertEquals(0.37f, result.alpha, EPSILON)
    }

    @Test
    fun clampKeepsRgbWithinDisplayRange() {
        val result = applyColorAdjust(-1f, 0.5f, 2f, 1f, brightness = 2f, contrast = 2f)

        assertPixel(result, red = 0f, green = 1f, blue = 1f, alpha = 1f)
    }

    @Test
    fun outsideTimeWindowReturnsIdentityParams() {
        val provider = RecordingProvider(brightness = 1.4f, contrast = 0.6f)

        val before = colorAdjustValuesAt(
            presentationTimeUs = 999L,
            windowStartUs = 1_000L,
            windowEndUs = 2_000L,
            provider = provider
        )
        val atEnd = colorAdjustValuesAt(
            presentationTimeUs = 2_000L,
            windowStartUs = 1_000L,
            windowEndUs = 2_000L,
            provider = provider
        )

        assertEquals(ColorAdjustValues.Identity, before)
        assertEquals(ColorAdjustValues.Identity, atEnd)
        assertEquals(emptyList<Pair<String, Long>>(), provider.calls)
    }

    @Test
    fun insideTimeWindowReadsProviderValues() {
        val provider = RecordingProvider(brightness = 1.4f, contrast = 0.6f)

        val values = colorAdjustValuesAt(
            presentationTimeUs = 1_500L,
            windowStartUs = 1_000L,
            windowEndUs = 2_000L,
            provider = provider
        )

        assertEquals(1.4f, values.brightness, EPSILON)
        assertEquals(0.6f, values.contrast, EPSILON)
        assertEquals(listOf(BRIGHTNESS to 1_500L, CONTRAST to 1_500L), provider.calls)
    }

    private fun assertPixel(
        pixel: ColorAdjustPixel,
        red: Float,
        green: Float,
        blue: Float,
        alpha: Float
    ) {
        assertEquals(red, pixel.red, EPSILON)
        assertEquals(green, pixel.green, EPSILON)
        assertEquals(blue, pixel.blue, EPSILON)
        assertEquals(alpha, pixel.alpha, EPSILON)
    }

    private class RecordingProvider(
        private val brightness: Float,
        private val contrast: Float
    ) : ParamProvider {
        val calls = mutableListOf<Pair<String, Long>>()

        override fun valueAt(key: String, presentationTimeUs: Long): Float {
            calls += key to presentationTimeUs
            return when (key) {
                BRIGHTNESS -> brightness
                CONTRAST -> contrast
                else -> error("Unknown key $key")
            }
        }
    }

    private companion object {
        const val EPSILON = 0.0001f
    }
}
