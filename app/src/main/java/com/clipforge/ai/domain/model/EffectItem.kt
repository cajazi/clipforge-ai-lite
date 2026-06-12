package com.clipforge.ai.domain.model

import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.Keyframe

data class EffectItem(
    val id: String,
    val projectId: String,
    val effectId: String,
    val scope: EffectScope,
    val startMs: Long,
    val endMs: Long,
    val zOrder: Int,
    val params: Map<String, EffectParamValue>
)

sealed class EffectParamValue {
    data class Constant(val value: Float) : EffectParamValue()
    data class Keyframed(val frames: List<Keyframe>) : EffectParamValue()
}
