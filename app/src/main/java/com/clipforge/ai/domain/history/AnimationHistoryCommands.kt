package com.clipforge.ai.domain.history

import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.repository.EffectRepository

class ReplaceGlobalAnimationCommand(
    private val repository: EffectRepository,
    private val projectId: String,
    private val nextEffect: EffectItem?
) : UndoableCommand {
    override val label: String = "Animation"

    private var capturedBefore: List<EffectItem>? = null

    override suspend fun execute() {
        if (capturedBefore == null) {
            capturedBefore = currentGlobalAnimations()
        }
        replaceWith(nextEffect?.let(::validateNextEffect)?.let(::listOf).orEmpty())
    }

    override suspend fun undo() {
        replaceWith(capturedBefore.orEmpty())
    }

    private suspend fun replaceWith(effects: List<EffectItem>) {
        currentGlobalAnimations().forEach { repository.deleteEffect(it.id) }
        effects.forEach { repository.upsertEffect(it) }
    }

    private suspend fun currentGlobalAnimations(): List<EffectItem> =
        repository.getEffectsForProject(projectId)
            .filter { it.projectId == projectId }
            .filter { it.scope == EffectScope.GLOBAL }
            .filter { it.effectId == AnimationEffectRegistrations.TRANSFORM_ANIMATION }

    private fun validateNextEffect(effect: EffectItem): EffectItem {
        require(effect.projectId == projectId) { "Animation effect projectId must match command projectId" }
        require(effect.scope == EffectScope.GLOBAL) { "Animation picker supports GLOBAL scope only" }
        require(effect.effectId == AnimationEffectRegistrations.TRANSFORM_ANIMATION) {
            "Animation picker can only replace transform_animation"
        }
        return effect
    }
}
