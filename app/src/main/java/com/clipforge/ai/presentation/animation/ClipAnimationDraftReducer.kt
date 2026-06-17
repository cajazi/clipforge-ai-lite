package com.clipforge.ai.presentation.animation

import com.clipforge.ai.core.animation.AnimationEffectId
import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.domain.model.EffectItem

/**
 * Pure draft-session reducer for clip-scoped animation editing. Never touches
 * [com.clipforge.ai.domain.repository.EffectRepository] or [com.clipforge.ai.domain.history.HistoryRegistry] -
 * all functions here compute the next in-memory [ClipAnimationDraft] only.
 */
object ClipAnimationDraftReducer {

    fun baseline(clipId: String, persistedEffects: List<EffectItem>): ClipAnimationDraft {
        val baselineItems = persistedEffects.filter { it.isClipAnimationFor(clipId) }
        val byRole = baselineItems.mapNotNull { item ->
            AnimationEffectId.parse(item.id)?.role?.let { role -> role to item }
        }.toMap()

        fun roleDraft(role: AnimationRole): DraftRole? = byRole[role]?.let { item ->
            DraftRole(
                presetId = null,
                requestedDurationMs = item.requestedDurationMsFromParams().takeIf { it > 0L }
                    ?: (item.endMs - item.startMs).coerceAtLeast(0L),
                resolvedItem = item
            )
        }

        return ClipAnimationDraft(
            clipId = clipId,
            inAnimation = roleDraft(AnimationRole.IN),
            outAnimation = roleDraft(AnimationRole.OUT),
            comboAnimation = roleDraft(AnimationRole.COMBO),
            baselineItems = baselineItems
        )
    }

    fun applyPreset(
        draft: ClipAnimationDraft,
        projectId: String,
        role: AnimationRole,
        presetId: String,
        requestedDurationMs: Long,
        clipWindow: ClipAnimationWindowInput
    ): ClipAnimationDraft {
        val resolvedItem = ClipAnimationEffectBuilder.buildEffect(
            projectId = projectId,
            clipId = draft.clipId,
            presetId = presetId,
            role = role,
            requestedDurationMs = requestedDurationMs,
            clipWindow = clipWindow
        )
        val nextDraft = draft.withRole(role, DraftRole(presetId, requestedDurationMs, resolvedItem))
        return applyConflictRules(nextDraft, role)
    }

    fun clearRole(draft: ClipAnimationDraft, role: AnimationRole): ClipAnimationDraft =
        draft.withRole(role, null)

    fun adjustDuration(
        draft: ClipAnimationDraft,
        projectId: String,
        role: AnimationRole,
        requestedDurationMs: Long,
        clipWindow: ClipAnimationWindowInput
    ): ClipAnimationDraft {
        val current = draft.role(role) ?: return draft
        val resolvedItem = resolveForRole(
            projectId = projectId,
            clipId = draft.clipId,
            role = role,
            presetId = current.presetId,
            existing = current.resolvedItem,
            requestedDurationMs = requestedDurationMs,
            clipWindow = clipWindow
        )
        return draft.withRole(role, current.copy(requestedDurationMs = requestedDurationMs, resolvedItem = resolvedItem))
    }

    /** Re-resolves every drafted role's window against an updated clip window (e.g. after a trim). */
    fun rewindow(draft: ClipAnimationDraft, projectId: String, clipWindow: ClipAnimationWindowInput): ClipAnimationDraft {
        var next = draft
        AnimationRole.entries.forEach { role ->
            val current = next.role(role) ?: return@forEach
            val resolvedItem = resolveForRole(
                projectId = projectId,
                clipId = next.clipId,
                role = role,
                presetId = current.presetId,
                existing = current.resolvedItem,
                requestedDurationMs = current.requestedDurationMs,
                clipWindow = clipWindow
            )
            next = next.withRole(role, current.copy(resolvedItem = resolvedItem))
        }
        return next
    }

    private fun resolveForRole(
        projectId: String,
        clipId: String,
        role: AnimationRole,
        presetId: String?,
        existing: EffectItem?,
        requestedDurationMs: Long,
        clipWindow: ClipAnimationWindowInput
    ): EffectItem? = if (presetId != null) {
        ClipAnimationEffectBuilder.buildEffect(
            projectId = projectId,
            clipId = clipId,
            presetId = presetId,
            role = role,
            requestedDurationMs = requestedDurationMs,
            clipWindow = clipWindow
        )
    } else {
        existing?.let {
            ClipAnimationEffectBuilder.rescale(
                existing = it,
                role = role,
                requestedDurationMs = requestedDurationMs,
                clipWindow = clipWindow
            )
        }
    }

    private fun applyConflictRules(draft: ClipAnimationDraft, justApplied: AnimationRole): ClipAnimationDraft =
        when (justApplied) {
            AnimationRole.IN -> draft.copy(comboAnimation = null)
            AnimationRole.OUT -> draft.copy(comboAnimation = null)
            AnimationRole.COMBO -> draft.copy(inAnimation = null, outAnimation = null)
        }

    private fun EffectItem.isClipAnimationFor(clipId: String): Boolean {
        val parsed = AnimationEffectId.parse(id) ?: return false
        return projectId.isNotBlank() &&
            parsed.clipId == clipId &&
            scope == EffectScope.CLIP &&
            effectId == AnimationEffectRegistrations.TRANSFORM_ANIMATION
    }
}
