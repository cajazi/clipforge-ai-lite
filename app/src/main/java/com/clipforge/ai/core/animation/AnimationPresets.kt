package com.clipforge.ai.core.animation

import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.KeyframeEasing

object AnimationPresets {
    private const val SHORT_US = 500_000L
    private const val MEDIUM_US = 650_000L
    private const val BOUNCE_US = 700_000L
    private const val SLOW_US = 2_000_000L
    private const val LOOP_US = 1_200_000L
    private const val SHAKE_US = 400_000L

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
            id = AnimationPresetIds.SLIDE_IN_RIGHT,
            displayName = "Slide In Right",
            type = AnimationPresetType.IN,
            durationUs = MEDIUM_US,
            property(
                AnimationPropertyKeys.POSITION_X,
                2f,
                identity(AnimationPropertyKeys.POSITION_X),
                MEDIUM_US,
                KeyframeEasing.CUBIC_OUT
            )
        ),
        preset(
            id = AnimationPresetIds.SLIDE_IN_UP,
            displayName = "Slide In Up",
            type = AnimationPresetType.IN,
            durationUs = MEDIUM_US,
            property(
                AnimationPropertyKeys.POSITION_Y,
                -2f,
                identity(AnimationPropertyKeys.POSITION_Y),
                MEDIUM_US,
                KeyframeEasing.CUBIC_OUT
            )
        ),
        preset(
            id = AnimationPresetIds.SLIDE_IN_DOWN,
            displayName = "Slide In Down",
            type = AnimationPresetType.IN,
            durationUs = MEDIUM_US,
            property(
                AnimationPropertyKeys.POSITION_Y,
                2f,
                identity(AnimationPropertyKeys.POSITION_Y),
                MEDIUM_US,
                KeyframeEasing.CUBIC_OUT
            )
        ),
        preset(
            id = AnimationPresetIds.ROTATE_IN,
            displayName = "Rotate In",
            type = AnimationPresetType.IN,
            durationUs = MEDIUM_US,
            property(
                AnimationPropertyKeys.ROTATION,
                -180f,
                identity(AnimationPropertyKeys.ROTATION),
                MEDIUM_US,
                KeyframeEasing.CUBIC_OUT
            ),
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
            id = AnimationPresetIds.POP_IN,
            displayName = "Pop In",
            type = AnimationPresetType.IN,
            durationUs = SHORT_US,
            property(
                AnimationPropertyKeys.SCALE_X,
                0f,
                identity(AnimationPropertyKeys.SCALE_X),
                SHORT_US,
                KeyframeEasing.BACK_OUT
            ),
            property(
                AnimationPropertyKeys.SCALE_Y,
                0f,
                identity(AnimationPropertyKeys.SCALE_Y),
                SHORT_US,
                KeyframeEasing.BACK_OUT
            ),
            property(
                AnimationPropertyKeys.OPACITY,
                0f,
                identity(AnimationPropertyKeys.OPACITY),
                SHORT_US,
                KeyframeEasing.SMOOTHSTEP
            )
        ),
        preset(
            id = AnimationPresetIds.BOUNCE_IN,
            displayName = "Bounce In",
            type = AnimationPresetType.IN,
            durationUs = BOUNCE_US,
            property(
                AnimationPropertyKeys.SCALE_X,
                0.3f,
                identity(AnimationPropertyKeys.SCALE_X),
                BOUNCE_US,
                KeyframeEasing.BOUNCE_OUT
            ),
            property(
                AnimationPropertyKeys.SCALE_Y,
                0.3f,
                identity(AnimationPropertyKeys.SCALE_Y),
                BOUNCE_US,
                KeyframeEasing.BOUNCE_OUT
            ),
            property(
                AnimationPropertyKeys.OPACITY,
                0f,
                identity(AnimationPropertyKeys.OPACITY),
                BOUNCE_US,
                KeyframeEasing.SMOOTHSTEP
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
            id = AnimationPresetIds.SLIDE_OUT_LEFT,
            displayName = "Slide Out Left",
            type = AnimationPresetType.OUT,
            durationUs = MEDIUM_US,
            property(
                AnimationPropertyKeys.POSITION_X,
                identity(AnimationPropertyKeys.POSITION_X),
                -2f,
                MEDIUM_US,
                KeyframeEasing.CUBIC_IN
            )
        ),
        preset(
            id = AnimationPresetIds.SLIDE_OUT_UP,
            displayName = "Slide Out Up",
            type = AnimationPresetType.OUT,
            durationUs = MEDIUM_US,
            property(
                AnimationPropertyKeys.POSITION_Y,
                identity(AnimationPropertyKeys.POSITION_Y),
                -2f,
                MEDIUM_US,
                KeyframeEasing.CUBIC_IN
            )
        ),
        preset(
            id = AnimationPresetIds.SLIDE_OUT_DOWN,
            displayName = "Slide Out Down",
            type = AnimationPresetType.OUT,
            durationUs = MEDIUM_US,
            property(
                AnimationPropertyKeys.POSITION_Y,
                identity(AnimationPropertyKeys.POSITION_Y),
                2f,
                MEDIUM_US,
                KeyframeEasing.CUBIC_IN
            )
        ),
        preset(
            id = AnimationPresetIds.ROTATE_OUT,
            displayName = "Rotate Out",
            type = AnimationPresetType.OUT,
            durationUs = MEDIUM_US,
            property(
                AnimationPropertyKeys.ROTATION,
                identity(AnimationPropertyKeys.ROTATION),
                180f,
                MEDIUM_US,
                KeyframeEasing.CUBIC_IN
            ),
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
            id = AnimationPresetIds.POP_OUT,
            displayName = "Pop Out",
            type = AnimationPresetType.OUT,
            durationUs = SHORT_US,
            property(
                AnimationPropertyKeys.SCALE_X,
                identity(AnimationPropertyKeys.SCALE_X),
                0f,
                SHORT_US,
                // fallback: no BACK_IN/BOUNCE_IN in KeyframeEasing; CUBIC_IN used
                KeyframeEasing.CUBIC_IN
            ),
            property(
                AnimationPropertyKeys.SCALE_Y,
                identity(AnimationPropertyKeys.SCALE_Y),
                0f,
                SHORT_US,
                // fallback: no BACK_IN/BOUNCE_IN in KeyframeEasing; CUBIC_IN used
                KeyframeEasing.CUBIC_IN
            ),
            property(
                AnimationPropertyKeys.OPACITY,
                identity(AnimationPropertyKeys.OPACITY),
                0f,
                SHORT_US,
                KeyframeEasing.SMOOTHSTEP
            )
        ),
        preset(
            id = AnimationPresetIds.BOUNCE_OUT,
            displayName = "Bounce Out",
            type = AnimationPresetType.OUT,
            durationUs = BOUNCE_US,
            property(
                AnimationPropertyKeys.SCALE_X,
                identity(AnimationPropertyKeys.SCALE_X),
                0.3f,
                BOUNCE_US,
                // fallback: no BACK_IN/BOUNCE_IN in KeyframeEasing; CUBIC_IN used
                KeyframeEasing.CUBIC_IN
            ),
            property(
                AnimationPropertyKeys.SCALE_Y,
                identity(AnimationPropertyKeys.SCALE_Y),
                0.3f,
                BOUNCE_US,
                // fallback: no BACK_IN/BOUNCE_IN in KeyframeEasing; CUBIC_IN used
                KeyframeEasing.CUBIC_IN
            ),
            property(
                AnimationPropertyKeys.OPACITY,
                identity(AnimationPropertyKeys.OPACITY),
                0f,
                BOUNCE_US,
                KeyframeEasing.SMOOTHSTEP
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
        ),
        preset(
            id = AnimationPresetIds.FLOAT,
            displayName = "Float",
            type = AnimationPresetType.LOOP,
            durationUs = LOOP_US,
            property(
                AnimationPropertyKeys.POSITION_Y,
                listOf(
                    Keyframe(0L, identity(AnimationPropertyKeys.POSITION_Y), KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US / 2L, -0.05f, KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US, identity(AnimationPropertyKeys.POSITION_Y))
                )
            )
        ),
        preset(
            id = AnimationPresetIds.BOB,
            displayName = "Bob",
            type = AnimationPresetType.LOOP,
            durationUs = LOOP_US,
            property(
                AnimationPropertyKeys.POSITION_Y,
                listOf(
                    Keyframe(0L, identity(AnimationPropertyKeys.POSITION_Y), KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US / 2L, 0.05f, KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US, identity(AnimationPropertyKeys.POSITION_Y))
                )
            )
        ),
        preset(
            id = AnimationPresetIds.WOBBLE,
            displayName = "Wobble",
            type = AnimationPresetType.LOOP,
            durationUs = LOOP_US,
            // Springy rotation that tiles cleanly (start == end), mirroring SWAY's
            // non-identity-but-tiling pattern; ELASTIC_OUT (no IN-overshoot exists).
            property(
                AnimationPropertyKeys.ROTATION,
                listOf(
                    Keyframe(0L, -2f, KeyframeEasing.ELASTIC_OUT),
                    Keyframe(LOOP_US / 2L, 2f, KeyframeEasing.ELASTIC_OUT),
                    Keyframe(LOOP_US, -2f)
                )
            )
        ),
        preset(
            id = AnimationPresetIds.HEARTBEAT,
            displayName = "Heartbeat",
            type = AnimationPresetType.LOOP,
            durationUs = LOOP_US,
            // Double-pulse: identity -> 1.08 -> identity -> 1.05 -> identity (second beat smaller).
            property(
                AnimationPropertyKeys.SCALE_X,
                listOf(
                    Keyframe(0L, identity(AnimationPropertyKeys.SCALE_X), KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US / 4L, 1.08f, KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US / 2L, identity(AnimationPropertyKeys.SCALE_X), KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US * 3L / 4L, 1.05f, KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US, identity(AnimationPropertyKeys.SCALE_X))
                )
            ),
            property(
                AnimationPropertyKeys.SCALE_Y,
                listOf(
                    Keyframe(0L, identity(AnimationPropertyKeys.SCALE_Y), KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US / 4L, 1.08f, KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US / 2L, identity(AnimationPropertyKeys.SCALE_Y), KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US * 3L / 4L, 1.05f, KeyframeEasing.SMOOTHSTEP),
                    Keyframe(LOOP_US, identity(AnimationPropertyKeys.SCALE_Y))
                )
            )
        ),
        preset(
            id = AnimationPresetIds.BREATHE,
            displayName = "Breathe",
            type = AnimationPresetType.LOOP,
            durationUs = SLOW_US,
            property(
                AnimationPropertyKeys.SCALE_X,
                listOf(
                    Keyframe(0L, identity(AnimationPropertyKeys.SCALE_X), KeyframeEasing.SMOOTHSTEP),
                    Keyframe(SLOW_US / 2L, 1.04f, KeyframeEasing.SMOOTHSTEP),
                    Keyframe(SLOW_US, identity(AnimationPropertyKeys.SCALE_X))
                )
            ),
            property(
                AnimationPropertyKeys.SCALE_Y,
                listOf(
                    Keyframe(0L, identity(AnimationPropertyKeys.SCALE_Y), KeyframeEasing.SMOOTHSTEP),
                    Keyframe(SLOW_US / 2L, 1.04f, KeyframeEasing.SMOOTHSTEP),
                    Keyframe(SLOW_US, identity(AnimationPropertyKeys.SCALE_Y))
                )
            )
        ),
        preset(
            id = AnimationPresetIds.SHAKE_LIGHT,
            displayName = "Shake Light",
            type = AnimationPresetType.LOOP,
            durationUs = SHAKE_US,
            // Fast small horizontal jitter; LINEAR for a nervous, mechanical feel.
            property(
                AnimationPropertyKeys.POSITION_X,
                listOf(
                    Keyframe(0L, identity(AnimationPropertyKeys.POSITION_X), KeyframeEasing.LINEAR),
                    Keyframe(SHAKE_US / 3L, 0.02f, KeyframeEasing.LINEAR),
                    Keyframe(SHAKE_US * 2L / 3L, -0.02f, KeyframeEasing.LINEAR),
                    Keyframe(SHAKE_US, identity(AnimationPropertyKeys.POSITION_X))
                )
            )
        ),
        preset(
            id = AnimationPresetIds.DRIFT_ZOOM,
            displayName = "Drift Zoom",
            type = AnimationPresetType.COMBO,
            durationUs = SLOW_US,
            // Same single-preset / multi-track shape as SLOW_ZOOM, plus a tiny horizontal drift.
            property(
                AnimationPropertyKeys.SCALE_X,
                identity(AnimationPropertyKeys.SCALE_X),
                1.08f,
                SLOW_US,
                KeyframeEasing.SMOOTHSTEP
            ),
            property(
                AnimationPropertyKeys.SCALE_Y,
                identity(AnimationPropertyKeys.SCALE_Y),
                1.08f,
                SLOW_US,
                KeyframeEasing.SMOOTHSTEP
            ),
            property(
                AnimationPropertyKeys.POSITION_X,
                identity(AnimationPropertyKeys.POSITION_X),
                0.03f,
                SLOW_US,
                KeyframeEasing.SMOOTHSTEP
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
