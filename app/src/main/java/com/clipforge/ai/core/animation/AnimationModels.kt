package com.clipforge.ai.core.animation

import com.clipforge.ai.core.effects.Keyframe

/**
 * Pure animation model foundation (C8.1).
 *
 * An animation is a specialization of the existing keyframe infrastructure, not a new engine:
 * an [AnimationProperty] is one animatable Float track (a list of [Keyframe]s, the same type a
 * future [com.clipforge.ai.core.effects.KeyframedParams] will consume), and an [AnimationTrack]
 * is the set of properties applied to one target layer.
 *
 * Pure Kotlin only: no Android, no Media3, no rendering/registration. [Keyframe]/[KeyframeEasing]
 * are themselves pure Kotlin, so importing them keeps this module unit-testable.
 */

/** The kind of layer an animation drives. Fixes the target taxonomy so ordering rules never change. */
enum class AnimationTargetType {
    CLIP,
    IMAGE,
    TEXT,
    STICKER,
    VIDEO
}

/** CapCut-style animation category. Foundational classification for future preset generators. */
enum class AnimationPresetType {
    IN,
    OUT,
    COMBO,
    LOOP
}

/**
 * One animatable property: a named [AnimationPropertyKeys] key and its keyframe track.
 * Times in [keyframes] are window-relative microseconds (same convention as [Keyframe]).
 */
data class AnimationProperty(
    val key: String,
    val keyframes: List<Keyframe>
)

/**
 * The animation applied to a single target layer: a target type plus its property tracks.
 * [presetType] is optional provenance for preset-generated tracks (null = manual/custom).
 */
data class AnimationTrack(
    val targetType: AnimationTargetType,
    val properties: List<AnimationProperty>,
    val presetType: AnimationPresetType? = null
)
