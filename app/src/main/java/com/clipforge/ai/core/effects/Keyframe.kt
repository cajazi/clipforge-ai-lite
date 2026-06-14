package com.clipforge.ai.core.effects

/**
 * Easing applied to the segment FROM this keyframe TO the next one.
 *
 * LINEAR and SMOOTHSTEP are the original V13 values and keep their exact behavior. The
 * remaining curves (C8.1) are APPENDED so existing ordinals are unchanged; the overshoot
 * curves (BACK_OUT, ELASTIC_OUT, BOUNCE_OUT) intentionally drive the eased fraction outside
 * [0,1] mid-segment, letting the interpolated value overshoot past the target keyframe and
 * settle back — the source of CapCut-style bounce/elastic motion. All curves are exact
 * identity at fraction 0 and 1 so keyframe endpoints always land on their values.
 */
enum class KeyframeEasing {
    LINEAR,
    SMOOTHSTEP,
    CUBIC_IN,
    CUBIC_OUT,
    CUBIC_IN_OUT,
    BACK_OUT,
    ELASTIC_OUT,
    BOUNCE_OUT
}

/** One keyframe of a parameter track. Times are window-relative microseconds. */
data class Keyframe(
    val timeUs: Long,
    val value: Float,
    val easing: KeyframeEasing = KeyframeEasing.LINEAR
)
