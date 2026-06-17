package com.clipforge.ai.presentation.animation

import com.clipforge.ai.core.animation.AnimationEffectId
import com.clipforge.ai.core.animation.AnimationPresetIds
import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.domain.history.HistoryRegistry
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.repository.EffectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipAnimationViewModelTest {
    @Test
    fun `role tab switching is UI only`() {
        val registry = HistoryRegistry()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, FakeEffectRepository(), registry)

        viewModel.selectRole(AnimationRole.OUT)

        assertEquals(AnimationRole.OUT, viewModel.state.value.selectedRole)
        assertEquals(0, registry.state.value.undoCount)
    }

    @Test
    fun `opening the panel snapshots persisted rows into a baseline draft`() {
        val registry = HistoryRegistry()
        val persisted = effect(AnimationRole.IN, AnimationPresetIds.ZOOM_IN, endMs = 500L)
        val repository = FakeEffectRepository(listOf(persisted))
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)

        viewModel.openPanel(CLIP_ID, repository.effects.value)

        val draft = viewModel.state.value.draft
        assertEquals(CLIP_ID, draft?.clipId)
        assertEquals(listOf(persisted), draft?.baselineItems)
        assertEquals(persisted, draft?.inAnimation?.resolvedItem)
        assertNull(draft?.outAnimation)
    }

    @Test
    fun `selecting a preset mutates the draft only and writes nothing to the repository`() {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.openPanel(CLIP_ID, repository.effects.value)

        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())

        assertEquals(AnimationPresetIds.ZOOM_IN, viewModel.state.value.draft?.inAnimation?.presetId)
        assertTrue(repository.effects.value.isEmpty())
        assertEquals(0, registry.state.value.undoCount)
    }

    @Test
    fun `dragging duration mutates the draft live and writes nothing to history per tick`() {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.openPanel(CLIP_ID, repository.effects.value)
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())

        viewModel.adjustDuration(CLIP_ID, AnimationRole.IN, 600L, window())
        viewModel.adjustDuration(CLIP_ID, AnimationRole.IN, 700L, window())
        viewModel.adjustDuration(CLIP_ID, AnimationRole.IN, 800L, window())

        assertEquals(800L, viewModel.state.value.draft?.inAnimation?.requestedDurationMs)
        assertTrue(repository.effects.value.isEmpty())
        assertEquals(0, registry.state.value.undoCount)
    }

    @Test
    fun `confirming the draft creates exactly one history entry and persists the draft items`() = runBlocking {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.openPanel(CLIP_ID, repository.effects.value)
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())
        viewModel.adjustDuration(CLIP_ID, AnimationRole.IN, 800L, window())

        viewModel.confirmDraft()

        assertEquals(1, registry.state.value.undoCount)
        assertEquals(800L, repository.effects.value.single().endMs)
        assertNull(viewModel.state.value.draft)
        assertEquals(false, viewModel.state.value.panelOpen)
    }

    @Test
    fun `confirming with no edits made and no prior persisted rows creates no history entry`() = runBlocking {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.openPanel(CLIP_ID, repository.effects.value)

        viewModel.confirmDraft()

        assertEquals(0, registry.state.value.undoCount)
        assertTrue(repository.effects.value.isEmpty())
    }

    @Test
    fun `discarding the draft creates no history entry and leaves the repository untouched`() = runBlocking {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.openPanel(CLIP_ID, repository.effects.value)
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())

        viewModel.discardDraft()

        assertEquals(0, registry.state.value.undoCount)
        assertTrue(repository.effects.value.isEmpty())
        assertNull(viewModel.state.value.draft)
        assertEquals(false, viewModel.state.value.panelOpen)
    }

    @Test
    fun `discarding after a confirmed session does not touch the persisted commit`() = runBlocking {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.openPanel(CLIP_ID, repository.effects.value)
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())
        viewModel.confirmDraft()

        viewModel.openPanel(CLIP_ID, repository.effects.value)
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.FADE_OUT, AnimationRole.OUT, 400L, window())
        viewModel.discardDraft()

        assertEquals(1, registry.state.value.undoCount)
        assertEquals(listOf(AnimationRole.IN), repository.roles())
    }

    @Test
    fun `applying in preserves a drafted out and clears a drafted combo`() {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.openPanel(CLIP_ID, repository.effects.value)
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.SLOW_ZOOM, AnimationRole.COMBO, 1_000L, window())
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.FADE_OUT, AnimationRole.OUT, 400L, window())

        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())

        val draft = viewModel.state.value.draft
        assertEquals(AnimationPresetIds.ZOOM_IN, draft?.inAnimation?.presetId)
        assertEquals(AnimationPresetIds.FADE_OUT, draft?.outAnimation?.presetId)
        assertNull(draft?.comboAnimation)
    }

    @Test
    fun `applying combo clears a drafted in and out`() {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.openPanel(CLIP_ID, repository.effects.value)
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.FADE_OUT, AnimationRole.OUT, 400L, window())

        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.SLOW_ZOOM, AnimationRole.COMBO, 1_000L, window())

        val draft = viewModel.state.value.draft
        assertEquals(AnimationPresetIds.SLOW_ZOOM, draft?.comboAnimation?.presetId)
        assertNull(draft?.inAnimation)
        assertNull(draft?.outAnimation)
    }

    @Test
    fun `clearing a drafted combo does not restore the prior in and out`() {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.openPanel(CLIP_ID, repository.effects.value)
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.FADE_OUT, AnimationRole.OUT, 400L, window())
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.SLOW_ZOOM, AnimationRole.COMBO, 1_000L, window())

        viewModel.clearAnimation(CLIP_ID, AnimationRole.COMBO)

        val draft = viewModel.state.value.draft
        assertNull(draft?.comboAnimation)
        assertNull(draft?.inAnimation)
        assertNull(draft?.outAnimation)
    }

    @Test
    fun `switching the selected clip while the panel is open discards the prior draft`() {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.openPanel(CLIP_ID, repository.effects.value)
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())

        viewModel.openPanel(OTHER_CLIP_ID, repository.effects.value)

        val draft = viewModel.state.value.draft
        assertEquals(OTHER_CLIP_ID, draft?.clipId)
        assertNull(draft?.inAnimation)
        assertTrue(repository.effects.value.isEmpty())
        assertEquals(0, registry.state.value.undoCount)
    }

    @Test
    fun `deleting the edited clip discards the draft and closes the panel`() {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.openPanel(CLIP_ID, repository.effects.value)
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())

        viewModel.discardDraftIfClipMissing(setOf(OTHER_CLIP_ID))

        assertNull(viewModel.state.value.draft)
        assertEquals(false, viewModel.state.value.panelOpen)
    }

    @Test
    fun `trimming the edited clip re-resolves the drafted window`() {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.openPanel(CLIP_ID, repository.effects.value)
        viewModel.selectPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window(endMs = 2_000L))

        viewModel.onClipWindowChanged(CLIP_ID, window(endMs = 1_000L))

        val resolved = viewModel.state.value.draft?.inAnimation?.resolvedItem
        assertTrue((resolved?.endMs ?: 0L) <= 1_000L)
    }

    private fun window(startMs: Long = 0L, endMs: Long = 2_000L) = ClipAnimationWindowInput(
        clipId = CLIP_ID,
        startMs = startMs,
        endMs = endMs
    )

    private fun effect(role: AnimationRole, presetId: String, startMs: Long = 0L, endMs: Long = 500L) =
        ClipAnimationEffectBuilder.buildEffect(
            projectId = PROJECT_ID,
            clipId = CLIP_ID,
            presetId = presetId,
            role = role,
            requestedDurationMs = endMs - startMs,
            clipWindow = ClipAnimationWindowInput(clipId = CLIP_ID, startMs = startMs, endMs = 2_000L)
        )!!

    private fun FakeEffectRepository.roles(): List<AnimationRole> =
        effects.value
            .mapNotNull { AnimationEffectId.parse(it.id)?.role }
            .sortedBy { it.ordinal }

    private class FakeEffectRepository(initial: List<EffectItem> = emptyList()) : EffectRepository {
        val effects = MutableStateFlow(initial)

        override suspend fun getEffectsForProject(projectId: String): List<EffectItem> =
            effects.value.filter { it.projectId == projectId }

        override fun observeEffectsForProject(projectId: String): Flow<List<EffectItem>> = effects

        override suspend fun upsertEffect(effect: EffectItem) {
            effects.value = effects.value.filterNot { it.id == effect.id } + effect
        }

        override suspend fun deleteEffect(id: String) {
            effects.value = effects.value.filterNot { it.id == id }
        }

        override suspend fun deleteEffectsForProject(projectId: String) {
            effects.value = effects.value.filterNot { it.projectId == projectId }
        }
    }

    private companion object {
        const val PROJECT_ID = "project"
        const val CLIP_ID = "clip-1"
        const val OTHER_CLIP_ID = "clip-2"
    }
}
