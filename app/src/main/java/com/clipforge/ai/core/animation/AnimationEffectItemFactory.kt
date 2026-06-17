package com.clipforge.ai.core.animation

import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.domain.model.EffectItem

/** Creates persisted runtime effect items for transform animations. */
object AnimationEffectItemFactory {

    fun createTransformAnimationEffectItem(
        id: String,
        projectId: String,
        track: AnimationTrack,
        startMs: Long,
        endMs: Long,
        zOrder: Int = 0,
        scope: EffectScope = EffectScope.GLOBAL
    ): EffectItem {
        require(scope == EffectScope.GLOBAL || scope == EffectScope.CLIP) {
            "Transform animation persistence supports GLOBAL or CLIP scope only"
        }
        require(startMs < endMs) { "Animation effect window must have positive duration" }

        return EffectItem(
            id = id,
            projectId = projectId,
            effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
            scope = scope,
            startMs = startMs,
            endMs = endMs,
            zOrder = zOrder,
            params = AnimationTrackMapper.toParams(track)
        )
    }
}
