package com.clipforge.ai.core.effects

/**
 * Static metadata for one registered effect. Mirrors TransitionDescriptor's role for
 * transitions: the registry-side single source of truth for identity, picker placement,
 * and the parameter schema.
 */
data class EffectDescriptor(
    val id: String,
    val displayName: String,
    val category: EffectCategory,
    val paramSpecs: List<ParamSpec>,
    val isPremium: Boolean = false
) {
    init {
        require(id.isNotBlank()) { "EffectDescriptor id must not be blank" }
        val duplicateKeys = paramSpecs.groupBy { it.key }.filterValues { it.size > 1 }.keys
        require(duplicateKeys.isEmpty()) { "EffectDescriptor '$id': duplicate param keys $duplicateKeys" }
    }
}
