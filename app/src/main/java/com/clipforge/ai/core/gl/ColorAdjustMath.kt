package com.clipforge.ai.core.gl

import com.clipforge.ai.core.effects.BRIGHTNESS
import com.clipforge.ai.core.effects.CONTRAST
import com.clipforge.ai.core.effects.ParamProvider

data class ColorAdjustValues(
    val brightness: Float = 1f,
    val contrast: Float = 1f
) {
    companion object {
        val Identity = ColorAdjustValues()
    }
}

data class ColorAdjustPixel(
    val red: Float,
    val green: Float,
    val blue: Float,
    val alpha: Float
)

fun applyColorAdjust(
    red: Float,
    green: Float,
    blue: Float,
    alpha: Float,
    brightness: Float = 1f,
    contrast: Float = 1f
): ColorAdjustPixel {
    fun adjust(channel: Float): Float =
        (((channel - 0.5f) * contrast) + 0.5f)
            .let { it * brightness }
            .coerceIn(0f, 1f)

    return ColorAdjustPixel(
        red = adjust(red),
        green = adjust(green),
        blue = adjust(blue),
        alpha = alpha
    )
}

fun colorAdjustValuesAt(
    presentationTimeUs: Long,
    windowStartUs: Long,
    windowEndUs: Long,
    provider: ParamProvider
): ColorAdjustValues {
    if (presentationTimeUs < windowStartUs || presentationTimeUs >= windowEndUs) {
        return ColorAdjustValues.Identity
    }
    return ColorAdjustValues(
        brightness = provider.valueAt(BRIGHTNESS, presentationTimeUs),
        contrast = provider.valueAt(CONTRAST, presentationTimeUs)
    )
}
