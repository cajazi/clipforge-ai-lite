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
    fun `duration drag does not create history per tick`() = runBlocking {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.applyPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())
        val undoCountAfterApply = registry.state.value.undoCount

        viewModel.setInFlightDuration(600L)
        viewModel.setInFlightDuration(700L)
        viewModel.setInFlightDuration(800L)

        assertEquals(undoCountAfterApply, registry.state.value.undoCount)
        assertEquals(800L, viewModel.state.value.inFlightDurationMs)
    }

    @Test
    fun `duration release creates one history entry`() = runBlocking {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.applyPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())
        viewModel.setInFlightDuration(800L)

        viewModel.commitDuration(CLIP_ID, AnimationRole.IN, 800L, window())

        assertEquals(2, registry.state.value.undoCount)
        assertEquals(800L, repository.effects.value.single().endMs)
    }

    @Test
    fun `apply in preserves out and removes combo through command`() = runBlocking {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.applyPreset(CLIP_ID, AnimationPresetIds.SLOW_ZOOM, AnimationRole.COMBO, 1_000L, window())
        viewModel.applyPreset(CLIP_ID, AnimationPresetIds.FADE_OUT, AnimationRole.OUT, 400L, window())

        viewModel.applyPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())

        assertEquals(listOf(AnimationRole.IN, AnimationRole.OUT), repository.roles())
    }

    @Test
    fun `apply out preserves in and removes combo through command`() = runBlocking {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.applyPreset(CLIP_ID, AnimationPresetIds.SLOW_ZOOM, AnimationRole.COMBO, 1_000L, window())
        viewModel.applyPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())

        viewModel.applyPreset(CLIP_ID, AnimationPresetIds.FADE_OUT, AnimationRole.OUT, 400L, window())

        assertEquals(listOf(AnimationRole.IN, AnimationRole.OUT), repository.roles())
    }

    @Test
    fun `apply combo removes in and out through command`() = runBlocking {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.applyPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())
        viewModel.applyPreset(CLIP_ID, AnimationPresetIds.FADE_OUT, AnimationRole.OUT, 400L, window())

        viewModel.applyPreset(CLIP_ID, AnimationPresetIds.SLOW_ZOOM, AnimationRole.COMBO, 1_000L, window())

        assertEquals(listOf(AnimationRole.COMBO), repository.roles())
    }

    @Test
    fun `remove combo does not restore in out through command`() = runBlocking {
        val registry = HistoryRegistry()
        val repository = FakeEffectRepository()
        val viewModel = ClipAnimationViewModel(PROJECT_ID, repository, registry)
        viewModel.applyPreset(CLIP_ID, AnimationPresetIds.ZOOM_IN, AnimationRole.IN, 500L, window())
        viewModel.applyPreset(CLIP_ID, AnimationPresetIds.FADE_OUT, AnimationRole.OUT, 400L, window())
        viewModel.applyPreset(CLIP_ID, AnimationPresetIds.SLOW_ZOOM, AnimationRole.COMBO, 1_000L, window())

        viewModel.removeAnimation(CLIP_ID, AnimationRole.COMBO)

        assertEquals(emptyList<AnimationRole>(), repository.roles())
    }

    private fun window() = ClipAnimationWindowInput(
        clipId = CLIP_ID,
        startMs = 0L,
        endMs = 2_000L
    )

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
    }
}
