package com.clipforge.ai.core.animation

import com.clipforge.ai.domain.model.EffectParamValue

/**
 * Pure bridge between the authoring animation model and persisted effect params.
 *
 * Persistence stores only keyframe tracks. Target type and preset provenance are in-memory
 * authoring metadata supplied by callers when reconstructing an [AnimationTrack].
 */
object AnimationTrackMapper {

    fun toParams(track: AnimationTrack): Map<String, EffectParamValue> {
        val validation = AnimationValidation.validateTrack(track)
        require(validation.isValid) { "Invalid animation track: $validation" }

        return track.properties.associate { property ->
            property.key to EffectParamValue.Keyframed(property.keyframes.map { it.copy() })
        }
    }

    fun toTrack(
        params: Map<String, EffectParamValue>,
        targetType: AnimationTargetType,
        presetType: AnimationPresetType? = null
    ): AnimationTrack {
        val properties = params.map { (key, value) ->
            require(AnimationValidation.isValidPropertyKey(key)) { "Unknown animation property key '$key'" }
            require(value is EffectParamValue.Keyframed) {
                "Animation property '$key' must be persisted as keyframes"
            }
            AnimationProperty(key, value.frames.map { it.copy() })
        }

        val track = AnimationTrack(
            targetType = targetType,
            properties = properties,
            presetType = presetType
        )
        val validation = AnimationValidation.validateTrack(track)
        require(validation.isValid) { "Invalid animation track params: $validation" }
        return track
    }
}
