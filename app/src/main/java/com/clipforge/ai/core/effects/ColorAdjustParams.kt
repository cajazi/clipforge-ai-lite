package com.clipforge.ai.core.effects

const val BRIGHTNESS = "brightness"
const val CONTRAST = "contrast"

fun colorAdjustParamSpecs(): List<ParamSpec> = listOf(
    ParamSpec(
        key = BRIGHTNESS,
        label = "Brightness",
        min = 0f,
        max = 2f,
        default = 1f
    ),
    ParamSpec(
        key = CONTRAST,
        label = "Contrast",
        min = 0f,
        max = 2f,
        default = 1f
    )
)
