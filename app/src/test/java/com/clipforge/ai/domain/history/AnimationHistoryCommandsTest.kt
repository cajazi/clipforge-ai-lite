package com.clipforge.ai.domain.history

import com.clipforge.ai.core.animation.AnimationEffectId
import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.repository.EffectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AnimationHistoryCommandsTest {
    @Test
    fun `in and out coexist`() = runBlocking {
        val repository = FakeEffectRepository()
        val registry = HistoryRegistry()

        registry.execute(ApplyInAnimationCommand(repository, PROJECT_ID, CLIP_ID, animation(AnimationRole.IN)))
        registry.execute(ApplyOutAnimationCommand(repository, PROJECT_ID, CLIP_ID, animation(AnimationRole.OUT)))

        assertEquals(
            listOf(AnimationRole.IN, AnimationRole.OUT),
            repository.roles()
        )
    }

    @Test
    fun `combo removes in and out`() = runBlocking {
        val repository = FakeEffectRepository(
            listOf(animation(AnimationRole.IN), animation(AnimationRole.OUT))
        )
        val registry = HistoryRegistry()

        registry.execute(ApplyComboAnimationCommand(repository, PROJECT_ID, CLIP_ID, animation(AnimationRole.COMBO)))

        assertEquals(listOf(AnimationRole.COMBO), repository.roles())
    }

    @Test
    fun `applying in removes combo only and preserves out`() = runBlocking {
        val repository = FakeEffectRepository(
            listOf(animation(AnimationRole.COMBO), animation(AnimationRole.OUT))
        )
        val registry = HistoryRegistry()

        registry.execute(ApplyInAnimationCommand(repository, PROJECT_ID, CLIP_ID, animation(AnimationRole.IN)))

        assertEquals(listOf(AnimationRole.IN, AnimationRole.OUT), repository.roles())
    }

    @Test
    fun `applying out removes combo only and preserves in`() = runBlocking {
        val repository = FakeEffectRepository(
            listOf(animation(AnimationRole.COMBO), animation(AnimationRole.IN))
        )
        val registry = HistoryRegistry()

        registry.execute(ApplyOutAnimationCommand(repository, PROJECT_ID, CLIP_ID, animation(AnimationRole.OUT)))

        assertEquals(listOf(AnimationRole.IN, AnimationRole.OUT), repository.roles())
    }

    @Test
    fun `undo redo restores exact previous rows`() = runBlocking {
        val out = animation(AnimationRole.OUT, startMs = 2_000L, endMs = 2_500L)
        val repository = FakeEffectRepository(listOf(out))
        val registry = HistoryRegistry()

        registry.execute(ApplyComboAnimationCommand(repository, PROJECT_ID, CLIP_ID, animation(AnimationRole.COMBO)))
        registry.undo()
        assertEquals(listOf(out), repository.getEffectsForProject(PROJECT_ID))

        registry.redo()
        assertEquals(listOf(AnimationRole.COMBO), repository.roles())
    }

    @Test
    fun `removing combo does not restore previous in out`() = runBlocking {
        val repository = FakeEffectRepository(listOf(animation(AnimationRole.COMBO)))
        val registry = HistoryRegistry()

        registry.execute(RemoveAnimationCommand(repository, PROJECT_ID, CLIP_ID, AnimationRole.COMBO))

        assertEquals(emptyList<EffectItem>(), repository.getEffectsForProject(PROJECT_ID))
    }

    private fun FakeEffectRepository.roles(): List<AnimationRole> =
        effects.value
            .mapNotNull { AnimationEffectId.parse(it.id)?.role }
            .sortedBy { it.ordinal }

    private fun animation(
        role: AnimationRole,
        startMs: Long = 0L,
        endMs: Long = 500L
    ) = EffectItem(
        id = AnimationEffectId.of(CLIP_ID, role),
        projectId = PROJECT_ID,
        effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
        scope = EffectScope.CLIP,
        startMs = startMs,
        endMs = endMs,
        zOrder = 0,
        params = emptyMap()
    )

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
