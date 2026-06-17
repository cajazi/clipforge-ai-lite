package com.clipforge.ai.presentation.animation

import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.domain.model.EffectItem

data class ClipAnimationDraft(
    val clipId: String,
    val inAnimation: DraftRole?,
    val outAnimation: DraftRole?,
    val comboAnimation: DraftRole?,
    val baselineItems: List<EffectItem>
) {
    fun role(role: AnimationRole): DraftRole? = when (role) {
        AnimationRole.IN -> inAnimation
        AnimationRole.OUT -> outAnimation
        AnimationRole.COMBO -> comboAnimation
    }

    fun withRole(role: AnimationRole, draftRole: DraftRole?): ClipAnimationDraft = when (role) {
        AnimationRole.IN -> copy(inAnimation = draftRole)
        AnimationRole.OUT -> copy(outAnimation = draftRole)
        AnimationRole.COMBO -> copy(comboAnimation = draftRole)
    }

    fun resolvedItems(): List<EffectItem> =
        listOfNotNull(inAnimation?.resolvedItem, outAnimation?.resolvedItem, comboAnimation?.resolvedItem)
}

data class DraftRole(
    val presetId: String?,
    val requestedDurationMs: Long,
    val resolvedItem: EffectItem?
)
