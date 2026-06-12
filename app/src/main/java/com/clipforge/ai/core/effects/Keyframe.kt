package com.clipforge.ai.core.effects

/** Easing applied to the segment FROM this keyframe TO the next one. */
enum class KeyframeEasing {
    LINEAR,
    SMOOTHSTEP
}

/** One keyframe of a parameter track. Times are window-relative microseconds. */
data class Keyframe(
    val timeUs: Long,
    val value: Float,
    val easing: KeyframeEasing = KeyframeEasing.LINEAR
)
