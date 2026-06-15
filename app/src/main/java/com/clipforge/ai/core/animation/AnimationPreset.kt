package com.clipforge.ai.core.animation

/**
 * Pure metadata + keyframe tracks for one built-in transform animation preset.
 *
 * C8.5 is a foundation only: presets are not released to UI/catalog and do not register effects.
 * Consumers can convert a preset to an [AnimationTrack] for a supported target type.
 */
data class AnimationPreset(
    val id: String,
    val displayName: String,
    val presetType: AnimationPresetType,
    val defaultDurationUs: Long,
    val supportedTargets: Set<AnimationTargetType>,
    val properties: List<AnimationProperty>
) {
    init {
        require(id.isNotBlank()) { "AnimationPreset id must not be blank" }
        require(displayName.isNotBlank()) { "AnimationPreset displayName must not be blank" }
        require(defaultDurationUs > 0L) { "AnimationPreset '$id' duration must be positive" }
        require(supportedTargets.isNotEmpty()) { "AnimationPreset '$id' must support at least one target" }
        val track = AnimationTrack(
            targetType = supportedTargets.first(),
            properties = properties,
            presetType = presetType
        )
        val validation = AnimationValidation.validateTrack(track)
        require(validation.isValid) { "AnimationPreset '$id' has invalid properties: $validation" }
    }

    fun toTrack(targetType: AnimationTargetType): AnimationTrack {
        require(targetType in supportedTargets) {
            "AnimationPreset '$id' does not support target $targetType"
        }
        return AnimationTrack(
            targetType = targetType,
            properties = properties.map { property ->
                property.copy(keyframes = property.keyframes.toList())
            },
            presetType = presetType
        )
    }
}
