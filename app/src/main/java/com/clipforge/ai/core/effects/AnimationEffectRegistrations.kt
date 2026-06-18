@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.effects

import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.gl.AnimationEffectFactory

object AnimationEffectRegistrations {
    const val TRANSFORM_ANIMATION = "transform_animation"

    val transformAnimationDescriptor = EffectDescriptor(
        id = TRANSFORM_ANIMATION,
        displayName = "Transform Animation",
        category = EffectCategory.TRENDY,
        paramSpecs = listOf(
            ParamSpec(
                key = AnimationPropertyKeys.POSITION_X,
                label = "Position X",
                min = -2f,
                max = 2f,
                default = requireNotNull(AnimationPropertyKeys.defaultValue(AnimationPropertyKeys.POSITION_X))
            ),
            ParamSpec(
                key = AnimationPropertyKeys.POSITION_Y,
                label = "Position Y",
                min = -2f,
                max = 2f,
                default = requireNotNull(AnimationPropertyKeys.defaultValue(AnimationPropertyKeys.POSITION_Y))
            ),
            ParamSpec(
                key = AnimationPropertyKeys.SCALE_X,
                label = "Scale X",
                min = 0f,
                max = 4f,
                default = requireNotNull(AnimationPropertyKeys.defaultValue(AnimationPropertyKeys.SCALE_X))
            ),
            ParamSpec(
                key = AnimationPropertyKeys.SCALE_Y,
                label = "Scale Y",
                min = 0f,
                max = 4f,
                default = requireNotNull(AnimationPropertyKeys.defaultValue(AnimationPropertyKeys.SCALE_Y))
            ),
            ParamSpec(
                key = AnimationPropertyKeys.ROTATION,
                label = "Rotation",
                min = -360f,
                max = 360f,
                default = requireNotNull(AnimationPropertyKeys.defaultValue(AnimationPropertyKeys.ROTATION))
            ),
            ParamSpec(
                key = AnimationPropertyKeys.OPACITY,
                label = "Opacity",
                min = 0f,
                max = 1f,
                default = requireNotNull(AnimationPropertyKeys.defaultValue(AnimationPropertyKeys.OPACITY))
            ),
            ParamSpec(
                key = AnimationPropertyKeys.ANCHOR_X,
                label = "Pivot X",
                min = 0f,
                max = 1f,
                default = requireNotNull(AnimationPropertyKeys.defaultValue(AnimationPropertyKeys.ANCHOR_X))
            ),
            ParamSpec(
                key = AnimationPropertyKeys.ANCHOR_Y,
                label = "Pivot Y",
                min = 0f,
                max = 1f,
                default = requireNotNull(AnimationPropertyKeys.defaultValue(AnimationPropertyKeys.ANCHOR_Y))
            )
        )
    )
}

fun EffectRegistry.registerTransformAnimationEffect() {
    register(
        EffectRegistration(
            descriptor = AnimationEffectRegistrations.transformAnimationDescriptor,
            factory = AnimationEffectFactory
        )
    )
}
