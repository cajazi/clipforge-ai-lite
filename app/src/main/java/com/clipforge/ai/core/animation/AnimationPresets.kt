package com.clipforge.ai.core.animation

import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.KeyframeEasing

object AnimationPresets {
    private const val SHORT_US = 500_000L
    private const val MEDIUM_US = 650_000L
    private const val SLOW_US = 2_000_000L
    private const val LOOP_US = 1_200_000L

    private val visualTargets = setOf(
        AnimationTargetType.CLIP,
        AnimationTargetType.IMAGE,
        AnimationTargetType.TEXT,
        AnimationTargetType.STICKER,
        AnimationTargetType.VIDEO
    )

    val all: List<AnimationPreset> = listOf(
        preset(
            id = AnimationPresetIds.FADE_IN,
            displayName = "Fade In",
            type = AnimationPresetType.IN,
            durationUs = SHORT_US,
            property(
                AnimationPropertyKeys.OPACITY,
                0f,
                identity(AnimationPropertyKeys.OPACITY),
                SHORT_US,
                KeyframeEasing.SMOOTHSTEP
            )
        ),
        preset(
            id = AnimationPresetIds.ZOOM_IN,
            displayName = "Zoom In",
            type = AnimationPresetType.IN,
            durationUs = MEDIUM_US,
            property(
                AnimationPropertyKeys.SCALE_X,
                0.8f,
                identity(AnimationPropertyKeys.SCALE_X),
                MEDIUM_US,
                KeyframeEasing.CUBIC_OUT
            ),
            property(
                AnimationPropertyKeys.SCALE_Y,
                0.8f,
                identity(AnimationPropertyKeys.SCALE_Y),
                MEDIUM_US,
                KeyframeEasing.CUBIC_OUT
            ),
            property(
                AnimationPropertyKeys.OPACITY,
                0f,
                identity(AnimationPropertyKeys.OPACITY),
                MEDIUM_US,
                KeyframeEasing.SMOOTHSTEP
            )
        ),
        preset(
            id = AnimationPresetIds.SLIDE_IN_LEFT,
            displayName = "Slide In Left",
            type = AnimationPresetType.IN,
            durationUs = MEDIUM_US,
            property(
                AnimationPropertyKeys.POSITION_X,
                -2f,
                identity(AnimationPropertyKeys.POSITION_X),
                MEDIUM_US,
                KeyframeEasing.CUBIC_OUT
            )
        ),
        preset(
            id = AnimationPresetIds.FADE_OUT,
            displayName = "Fade Out",
            type = AnimationPresetType.OUT,
            durationUs = SHORT_US,
            property(
                AnimationPropertyKeys.OPACITY,
                identity(AnimationPropertyKeys.OPACITY),
                0f,
                SHORT_US,
                KeyframeEasing.SMOOTHSTEP
            )
        ),
        preset(
            id = AnimationPresetIds.ZOOM_OUT,
            displayName = "Zoom Out",
            type = AnimationPresetType.OUT,
            durationUs = MEDIUM_US,
            property(
                AnimationPropertyKeys.SCALE_X,
                identity(AnimationPropertyKeys.SCALE_X),
                0.8f,
                MEDIUM_US,
                KeyframeEasing.CUBIC_IN
            ),
            property(
                AnimationPropertyKeys.SCALE_Y,
                identity(AnimationPropertyKeys.SCALE_Y),
                0.8f,
                MEDIUM_US,
                KeyframeEasing.CUBIC_IN
            ),
            property(
                AnimationPropertyKeys.OPACITY,
                identity(AnimationPropertyKeys.OPACITY),
                0f,
                MEDIUM_US,
                KeyframeEasing.SMOOTHSTEP
            )
        ),
        preset(
            id = AnimationPresetIds.SLIDE_OUT_RIGHT,
            displayName = "Slide Out Right",
            type = AnimationPresetType.OUT,
            durationUs = MEDIUM_US,
            property(
                AnimationPropertyKeys.POSITION_X,
                identity(AnimationPropertyKeys.POSITION_X),
                2f,
                MEDIUM_US,
                KeyframeEasing.CUBIC_IN
            )
        ),
        preset(
            id = AnimationPresetIds.SLOW_ZOOM,
            displayName = "Slow Zoom",
            type = AnimationPresetType.COMBO,
            durationUs = SLOW_US,
            property(
                AnimationPropertyKeys.SCALE_X,
                identity(AnimationPropertyKeys.SCALE_X),
                1.12f,
                SLOW_US,
                KeyframeEasing.SMOOTHSTEP
            ),
            property(
                AnimationPropertyKeys.SCALE_Y,
                identity(AnimationPropertyKeys.SCALE_Y),
                1.12f,
                SLOW_US,
                KeyframeEasing.SMOOTHSTEP
            )
        ),
        preset(
            id = AnimationPresetIds.PULSE,
            displayName = "Pulse",
            type = AnimationPresetType.LOOP,
            durationUs = LOOP_US,
            property(
                AnimationPropertyKeys.SCALE_X,
                listOf(
                    Keyframe(0L, identity(AnimationPropertyKeys.SCALE_X), KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US / 2L, 1.06f, KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US, identity(AnimationPropertyKeys.SCALE_X))
                )
            ),
            property(
                AnimationPropertyKeys.SCALE_Y,
                listOf(
                    Keyframe(0L, identity(AnimationPropertyKeys.SCALE_Y), KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US / 2L, 1.06f, KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US, identity(AnimationPropertyKeys.SCALE_Y))
                )
            )
        ),
        preset(
            id = AnimationPresetIds.SWAY,
            displayName = "Sway",
            type = AnimationPresetType.LOOP,
            durationUs = LOOP_US,
            property(
                AnimationPropertyKeys.ROTATION,
                listOf(
                    Keyframe(0L, -3f, KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US / 2L, 3f, KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US, -3f)
                )
            )
        )
    )

    private val byId: Map<String, AnimationPreset> = all.associateBy { it.id }

    fun get(id: String): AnimationPreset? = byId[id]

    fun byType(type: AnimationPresetType): List<AnimationPreset> =
        all.filter { it.presetType == type }

    fun forTarget(targetType: AnimationTargetType): List<AnimationPreset> =
        all.filter { targetType in it.supportedTargets }

    private fun preset(
        id: String,
        displayName: String,
        type: AnimationPresetType,
        durationUs: Long,
        vararg properties: AnimationProperty
    ) = AnimationPreset(
        id = id,
        displayName = displayName,
        presetType = type,
        defaultDurationUs = durationUs,
        supportedTargets = visualTargets,
        properties = properties.toList()
    )

    private fun property(
        key: String,
        startValue: Float,
        endValue: Float,
        endUs: Long,
        easing: KeyframeEasing
    ) = property(
        key,
        listOf(
            Keyframe(0L, startValue, easing),
            Keyframe(endUs, endValue)
        )
    )

    private fun property(key: String, keyframes: List<Keyframe>) =
        AnimationProperty(key = key, keyframes = keyframes)

    private fun identity(key: String): Float =
        requireNotNull(AnimationPropertyKeys.defaultValue(key)) { "No identity value for '$key'" }
}
