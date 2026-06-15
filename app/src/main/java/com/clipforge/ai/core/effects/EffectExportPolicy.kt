package com.clipforge.ai.core.effects

object EffectExportPolicy {
    val current = EffectReleasePolicy(
        exportReadyIds = setOf(AnimationEffectRegistrations.TRANSFORM_ANIMATION),
        releasedIds = emptySet()
    )
}
