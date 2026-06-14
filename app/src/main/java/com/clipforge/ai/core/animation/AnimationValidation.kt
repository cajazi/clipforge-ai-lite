package com.clipforge.ai.core.animation

import com.clipforge.ai.core.effects.Keyframe

/**
 * Pure, non-throwing validation for the animation model (C8.1).
 *
 * Mirrors the strictly-increasing keyframe rule enforced at render time by
 * [com.clipforge.ai.core.effects.KeyframeSampler.requireSorted], but returns a result instead
 * of throwing so authoring/UI layers can surface problems. No Android, no Media3.
 */
sealed interface AnimationValidationResult {
    data object Valid : AnimationValidationResult
    data class Invalid(val reasons: List<String>) : AnimationValidationResult

    val isValid: Boolean get() = this is Valid
}

object AnimationValidation {

    /** True if [key] is a recognized animatable property. */
    fun isValidPropertyKey(key: String): Boolean = AnimationPropertyKeys.isKnown(key)

    /** Keyframes must be non-empty and STRICTLY increasing in time (render-path contract). */
    fun validateKeyframeOrdering(keyframes: List<Keyframe>): AnimationValidationResult {
        if (keyframes.isEmpty()) return AnimationValidationResult.Invalid(listOf("keyframe track must not be empty"))
        val reasons = mutableListOf<String>()
        for (i in 1 until keyframes.size) {
            if (keyframes[i].timeUs <= keyframes[i - 1].timeUs) {
                reasons += "keyframes must strictly increase in time: " +
                    "frame[$i].timeUs=${keyframes[i].timeUs} <= frame[${i - 1}].timeUs=${keyframes[i - 1].timeUs}"
            }
        }
        return if (reasons.isEmpty()) AnimationValidationResult.Valid else AnimationValidationResult.Invalid(reasons)
    }

    /**
     * Easing compatibility: every keyframe's easing must be a recognized [KeyframeEasing].
     * (All easings are currently compatible with all properties; this is the extension point
     * for any future per-property restriction.) Always Valid for a well-typed track today.
     */
    fun validateEasingCompatibility(@Suppress("UNUSED_PARAMETER") property: AnimationProperty): AnimationValidationResult =
        AnimationValidationResult.Valid

    /** Validate one property: known key + ordered keyframes + easing compatibility. */
    fun validateProperty(property: AnimationProperty): AnimationValidationResult {
        val reasons = mutableListOf<String>()
        if (!isValidPropertyKey(property.key)) reasons += "unknown property key '${property.key}'"
        (validateKeyframeOrdering(property.keyframes) as? AnimationValidationResult.Invalid)?.let { reasons += it.reasons }
        return if (reasons.isEmpty()) AnimationValidationResult.Valid else AnimationValidationResult.Invalid(reasons)
    }

    /** Validate a whole track: each property valid + no duplicate property keys. */
    fun validateTrack(track: AnimationTrack): AnimationValidationResult {
        val reasons = mutableListOf<String>()

        val duplicateKeys = track.properties
            .groupingBy { it.key }
            .eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicateKeys.isNotEmpty()) reasons += "duplicate property keys: $duplicateKeys"

        track.properties.forEach { property ->
            (validateProperty(property) as? AnimationValidationResult.Invalid)?.let { reasons += it.reasons }
        }

        return if (reasons.isEmpty()) AnimationValidationResult.Valid else AnimationValidationResult.Invalid(reasons)
    }
}
