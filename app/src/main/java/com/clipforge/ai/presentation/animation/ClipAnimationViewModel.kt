package com.clipforge.ai.presentation.animation

import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.domain.history.CommitClipAnimationDraftCommand
import com.clipforge.ai.domain.history.HistoryRegistry
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.repository.EffectRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ClipAnimationTransientState(
    val selectedRole: AnimationRole = AnimationRole.IN,
    val inFlightDurationMs: Long? = null,
    val panelOpen: Boolean = false,
    val draft: ClipAnimationDraft? = null
)

/**
 * Owns the in-memory draft session for clip-scoped animation editing. All preset/duration
 * edits mutate [ClipAnimationDraft] only - no [EffectRepository] write and no
 * [HistoryRegistry] entry is produced until [confirmDraft] is called.
 */
class ClipAnimationViewModel(
    private val projectId: String,
    private val repository: EffectRepository,
    private val historyRegistry: HistoryRegistry
) {
    private val mutableState = MutableStateFlow(ClipAnimationTransientState())
    val state: StateFlow<ClipAnimationTransientState> = mutableState.asStateFlow()

    /** Opens the panel and snapshots [persistedEffects] into a fresh draft baseline. */
    fun openPanel(clipId: String?, persistedEffects: List<EffectItem>) {
        mutableState.update {
            ClipAnimationTransientState(
                panelOpen = true,
                draft = clipId?.let { ClipAnimationDraftReducer.baseline(it, persistedEffects) }
            )
        }
    }

    fun selectRole(role: AnimationRole) {
        mutableState.update { it.copy(selectedRole = role, inFlightDurationMs = null) }
    }

    fun selectPreset(
        clipId: String,
        presetId: String,
        role: AnimationRole,
        requestedDurationMs: Long,
        clipWindow: ClipAnimationWindowInput
    ) {
        mutableState.update { current ->
            val draft = current.draft?.takeIf { it.clipId == clipId }
                ?: ClipAnimationDraftReducer.baseline(clipId, emptyList())
            val nextDraft = ClipAnimationDraftReducer.applyPreset(
                draft = draft,
                projectId = projectId,
                role = role,
                presetId = presetId,
                requestedDurationMs = requestedDurationMs.roundToStep(),
                clipWindow = clipWindow
            )
            current.copy(selectedRole = role, draft = nextDraft, inFlightDurationMs = null)
        }
    }

    fun clearAnimation(clipId: String, role: AnimationRole) {
        mutableState.update { current ->
            val draft = current.draft?.takeIf { it.clipId == clipId } ?: return@update current
            current.copy(draft = ClipAnimationDraftReducer.clearRole(draft, role), inFlightDurationMs = null)
        }
    }

    /** Mutates the draft's resolved window/keyframes live as the duration slider moves. */
    fun adjustDuration(
        clipId: String,
        role: AnimationRole,
        durationMs: Long,
        clipWindow: ClipAnimationWindowInput
    ) {
        val rounded = durationMs.roundToStep()
        mutableState.update { current ->
            val draft = current.draft?.takeIf { it.clipId == clipId }
            val nextDraft = if (draft != null && draft.role(role) != null) {
                ClipAnimationDraftReducer.adjustDuration(draft, projectId, role, rounded, clipWindow)
            } else {
                draft
            }
            current.copy(draft = nextDraft, inFlightDurationMs = rounded)
        }
    }

    /** Re-resolves every drafted role's window after the edited clip's trim bounds change. */
    fun onClipWindowChanged(clipId: String, clipWindow: ClipAnimationWindowInput) {
        mutableState.update { current ->
            val draft = current.draft?.takeIf { it.clipId == clipId } ?: return@update current
            current.copy(draft = ClipAnimationDraftReducer.rewindow(draft, projectId, clipWindow))
        }
    }

    /** Discards the draft (no history entry) if the edited clip no longer exists. */
    fun discardDraftIfClipMissing(existingClipIds: Set<String>) {
        mutableState.update { current ->
            val draft = current.draft ?: return@update current
            if (draft.clipId in existingClipIds) return@update current
            ClipAnimationTransientState()
        }
    }

    /**
     * Commits the draft atomically as exactly one history entry, then closes the panel.
     * No-ops (no history entry) if the draft's resolved items are unchanged from the baseline.
     */
    suspend fun confirmDraft() {
        val draft = mutableState.value.draft
        if (draft != null && draft.resolvedItems() != draft.baselineItems) {
            historyRegistry.execute(
                CommitClipAnimationDraftCommand(
                    repository = repository,
                    projectId = projectId,
                    clipId = draft.clipId,
                    priorItems = draft.baselineItems,
                    draftItems = draft.resolvedItems()
                )
            )
        }
        mutableState.update { ClipAnimationTransientState() }
    }

    /** Discards the draft (no repository write, no history entry) and closes the panel. */
    fun discardDraft() {
        mutableState.update { ClipAnimationTransientState() }
    }

    private fun Long.roundToStep(): Long =
        ((this + CLIP_ANIMATION_DURATION_STEP_MS / 2L) / CLIP_ANIMATION_DURATION_STEP_MS)
            .coerceAtLeast(1L) * CLIP_ANIMATION_DURATION_STEP_MS
}
