package com.clipforge.ai.presentation.animation

import com.clipforge.ai.core.animation.AnimationPresetIds
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.domain.history.HistoryRegistry
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import com.clipforge.ai.domain.repository.EffectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationPickerViewModelTest {

    @Test
    fun `build effect uses deterministic id and full project window`() {
        val effect = AnimationPickerViewModel.buildEffectForPreset(
            projectId = PROJECT_ID,
            presetId = AnimationPresetIds.ZOOM_IN,
            totalDurationMs = 4_000L
        )

        assertEquals("anim-global-$PROJECT_ID", effect.id)
        assertEquals(AnimationEffectRegistrations.TRANSFORM_ANIMATION, effect.effectId)
        assertEquals(EffectScope.GLOBAL, effect.scope)
        assertEquals(0L, effect.startMs)
        assertEquals(4_000L, effect.endMs)
    }

    @Test
    fun `fade out is shifted to project end while effect window stays global`() {
        val effect = AnimationPickerViewModel.buildEffectForPreset(
            projectId = PROJECT_ID,
            presetId = AnimationPresetIds.FADE_OUT,
            totalDurationMs = 4_000L
        )

        assertEquals(listOf(3_500_000L, 4_000_000L), frames(effect, AnimationPropertyKeys.OPACITY).map { it.timeUs })
    }

    @Test
    fun `slow zoom spans full project window`() {
        val effect = AnimationPickerViewModel.buildEffectForPreset(
            projectId = PROJECT_ID,
            presetId = AnimationPresetIds.SLOW_ZOOM,
            totalDurationMs = 4_000L
        )

        assertEquals(listOf(0L, 4_000_000L), frames(effect, AnimationPropertyKeys.SCALE_X).map { it.timeUs })
        assertEquals(listOf(1f, 1.12f), frames(effect, AnimationPropertyKeys.SCALE_X).map { it.value })
    }

    @Test
    fun `loop preset tiles across current project window`() {
        val effect = AnimationPickerViewModel.buildEffectForPreset(
            projectId = PROJECT_ID,
            presetId = AnimationPresetIds.PULSE,
            totalDurationMs = 3_000L
        )

        assertEquals(
            listOf(0L, 600_000L, 1_200_000L, 1_800_000L, 2_400_000L, 3_000_000L),
            frames(effect, AnimationPropertyKeys.SCALE_X).map { it.timeUs }
        )
    }

    @Test
    fun `apply preset replaces existing transform rows and keeps one deterministic row`() = runBlocking {
        val repository = FakeEffectRepository(
            listOf(
                transform("old-animation"),
                otherEffect("color"),
                transform("duplicate-animation")
            )
        )
        val picker = AnimationPickerViewModel(PROJECT_ID, repository, HistoryRegistry())

        picker.applyPreset(AnimationPresetIds.ZOOM_IN, totalDurationMs = 2_000L)

        val effects = repository.getEffectsForProject(PROJECT_ID)
        assertEquals(listOf("anim-global-$PROJECT_ID", "color"), effects.map { it.id }.sorted())
        assertEquals(1, effects.count { it.effectId == AnimationEffectRegistrations.TRANSFORM_ANIMATION })
    }

    @Test
    fun `remove animation deletes deterministic transform row`() = runBlocking {
        val repository = FakeEffectRepository(listOf(transform("anim-global-$PROJECT_ID"), otherEffect("color")))
        val picker = AnimationPickerViewModel(PROJECT_ID, repository, HistoryRegistry())

        picker.removeAnimation()

        assertEquals(listOf("color"), repository.getEffectsForProject(PROJECT_ID).map { it.id })
    }

    @Test
    fun `undo redo restores exact previous animation state`() = runBlocking {
        val previous = transform("old-animation")
        val repository = FakeEffectRepository(listOf(previous, otherEffect("color")))
        val registry = HistoryRegistry()
        val picker = AnimationPickerViewModel(PROJECT_ID, repository, registry)

        picker.applyPreset(AnimationPresetIds.SLOW_ZOOM, totalDurationMs = 2_000L)
        registry.undo()
        assertEquals(listOf("color", "old-animation"), repository.getEffectsForProject(PROJECT_ID).map { it.id }.sorted())

        registry.redo()
        assertEquals(listOf("anim-global-$PROJECT_ID", "color"), repository.getEffectsForProject(PROJECT_ID).map { it.id }.sorted())
    }

    @Test
    fun `undo redo remove restores and deletes previous animation`() = runBlocking {
        val previous = transform("anim-global-$PROJECT_ID")
        val repository = FakeEffectRepository(listOf(previous, otherEffect("color")))
        val registry = HistoryRegistry()
        val picker = AnimationPickerViewModel(PROJECT_ID, repository, registry)

        picker.removeAnimation()
        registry.undo()
        assertEquals(listOf("anim-global-$PROJECT_ID", "color"), repository.getEffectsForProject(PROJECT_ID).map { it.id }.sorted())

        registry.redo()
        assertEquals(listOf("color"), repository.getEffectsForProject(PROJECT_ID).map { it.id })
    }

    @Test
    fun `zero duration project is rejected`() {
        val thrown = runCatching {
            AnimationPickerViewModel.buildEffectForPreset(PROJECT_ID, AnimationPresetIds.FADE_IN, totalDurationMs = 0L)
        }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    private fun frames(effect: EffectItem, key: String) =
        (effect.params.getValue(key) as EffectParamValue.Keyframed).frames

    private fun transform(id: String) = EffectItem(
        id = id,
        projectId = PROJECT_ID,
        effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
        scope = EffectScope.GLOBAL,
        startMs = 0L,
        endMs = 1_000L,
        zOrder = 0,
        params = emptyMap()
    )

    private fun otherEffect(id: String) = EffectItem(
        id = id,
        projectId = PROJECT_ID,
        effectId = "color_adjust",
        scope = EffectScope.GLOBAL,
        startMs = 0L,
        endMs = 1_000L,
        zOrder = 0,
        params = emptyMap()
    )

    private class FakeEffectRepository(initial: List<EffectItem> = emptyList()) : EffectRepository {
        private val effects = MutableStateFlow(initial)

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
    }
}
