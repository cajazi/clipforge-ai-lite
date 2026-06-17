package com.clipforge.ai.domain.history

import com.clipforge.ai.core.animation.AnimationEffectId
import com.clipforge.ai.core.animation.AnimationRole
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

class ApplyInAnimationCommand(
    repository: EffectRepository,
    projectId: String,
    clipId: String,
    effect: EffectItem
) : ReplaceClipAnimationCommand(repository, projectId, clipId, AnimationRole.IN, effect)

class ApplyOutAnimationCommand(
    repository: EffectRepository,
    projectId: String,
    clipId: String,
    effect: EffectItem
) : ReplaceClipAnimationCommand(repository, projectId, clipId, AnimationRole.OUT, effect)

class ApplyComboAnimationCommand(
    repository: EffectRepository,
    projectId: String,
    clipId: String,
    effect: EffectItem
) : ReplaceClipAnimationCommand(repository, projectId, clipId, AnimationRole.COMBO, effect)

class RemoveAnimationCommand(
    private val repository: EffectRepository,
    private val projectId: String,
    private val clipId: String,
    private val role: AnimationRole
) : UndoableCommand {
    override val label: String = "Animation"

    private var capturedBefore: List<EffectItem>? = null

    override suspend fun execute() {
        if (capturedBefore == null) capturedBefore = currentClipAnimations()
        repository.deleteEffect(AnimationEffectId.of(clipId, role))
    }

    override suspend fun undo() {
        restore(capturedBefore.orEmpty())
    }

    private suspend fun restore(effects: List<EffectItem>) {
        currentClipAnimations().forEach { repository.deleteEffect(it.id) }
        effects.forEach { repository.upsertEffect(it) }
    }

    private suspend fun currentClipAnimations(): List<EffectItem> =
        repository.getEffectsForProject(projectId).filter { it.isClipAnimationFor(clipId) }
}

class CommitClipAnimationDraftCommand(
    private val repository: EffectRepository,
    private val projectId: String,
    private val clipId: String,
    private val priorItems: List<EffectItem>,
    private val draftItems: List<EffectItem>
) : UndoableCommand {
    override val label: String = "Animation"

    override suspend fun execute() {
        replaceWith(draftItems)
    }

    override suspend fun undo() {
        replaceWith(priorItems)
    }

    private suspend fun replaceWith(items: List<EffectItem>) {
        items.forEach(::validateEffect)
        val nextIds = items.map { it.id }.toSet()
        currentClipAnimations()
            .filterNot { it.id in nextIds }
            .forEach { repository.deleteEffect(it.id) }
        items.forEach { repository.upsertEffect(it) }
    }

    private suspend fun currentClipAnimations(): List<EffectItem> =
        repository.getEffectsForProject(projectId).filter { it.isClipAnimationFor(clipId) }

    private fun validateEffect(effect: EffectItem) {
        require(effect.projectId == projectId) { "Animation effect projectId must match command projectId" }
        require(effect.scope == EffectScope.CLIP) { "Clip animation draft commit supports CLIP scope only" }
        require(effect.effectId == AnimationEffectRegistrations.TRANSFORM_ANIMATION) {
            "Clip animation draft commit can only persist transform_animation"
        }
        val parsed = AnimationEffectId.parse(effect.id)
        require(parsed != null && parsed.clipId == clipId) {
            "Clip animation draft commit effect id must belong to clipId=$clipId"
        }
    }
}

abstract class ReplaceClipAnimationCommand(
    private val repository: EffectRepository,
    private val projectId: String,
    private val clipId: String,
    private val role: AnimationRole,
    private val effect: EffectItem
) : UndoableCommand {
    override val label: String = "Animation"

    private var capturedBefore: List<EffectItem>? = null

    override suspend fun execute() {
        if (capturedBefore == null) capturedBefore = currentClipAnimations()
        val conflicts = when (role) {
            AnimationRole.IN -> setOf(AnimationRole.IN, AnimationRole.COMBO)
            AnimationRole.OUT -> setOf(AnimationRole.OUT, AnimationRole.COMBO)
            AnimationRole.COMBO -> setOf(AnimationRole.IN, AnimationRole.OUT, AnimationRole.COMBO)
        }
        currentClipAnimations()
            .filter { AnimationEffectId.parse(it.id)?.role in conflicts }
            .forEach { repository.deleteEffect(it.id) }
        repository.upsertEffect(validateEffect(effect))
    }

    override suspend fun undo() {
        restore(capturedBefore.orEmpty())
    }

    private suspend fun restore(effects: List<EffectItem>) {
        currentClipAnimations().forEach { repository.deleteEffect(it.id) }
        effects.forEach { repository.upsertEffect(it) }
    }

    private suspend fun currentClipAnimations(): List<EffectItem> =
        repository.getEffectsForProject(projectId).filter { it.isClipAnimationFor(clipId) }

    private fun validateEffect(effect: EffectItem): EffectItem {
        require(effect.projectId == projectId) { "Animation effect projectId must match command projectId" }
        require(effect.scope == EffectScope.CLIP) { "Clip animation command supports CLIP scope only" }
        require(effect.effectId == AnimationEffectRegistrations.TRANSFORM_ANIMATION) {
            "Clip animation command can only replace transform_animation"
        }
        require(effect.id == AnimationEffectId.of(clipId, role)) {
            "Clip animation effect id must match clipId and role"
        }
        return effect
    }
}

private fun EffectItem.isClipAnimationFor(clipId: String): Boolean {
    val parsed = AnimationEffectId.parse(id) ?: return false
    return projectId.isNotBlank() &&
        parsed.clipId == clipId &&
        scope == EffectScope.CLIP &&
        effectId == AnimationEffectRegistrations.TRANSFORM_ANIMATION
}
