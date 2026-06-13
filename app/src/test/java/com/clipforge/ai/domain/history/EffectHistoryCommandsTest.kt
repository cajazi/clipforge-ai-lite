package com.clipforge.ai.domain.history

import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.repository.EffectRepository
import com.clipforge.ai.domain.selection.SelectionController
import com.clipforge.ai.domain.selection.SelectionTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EffectHistoryCommandsTest {
    @Test
    fun `undo add effect removes effect`() = runBlocking {
        val repository = FakeEffectRepository()
        val registry = HistoryRegistry()
        val effect = effect("effect-1")

        registry.execute(AddEffectCommand(repository, effect))
        registry.undo()

        assertEquals(emptyList<EffectItem>(), repository.getEffectsForProject(PROJECT_ID))
    }

    @Test
    fun `redo add effect restores effect`() = runBlocking {
        val repository = FakeEffectRepository()
        val registry = HistoryRegistry()
        val effect = effect("effect-1")

        registry.execute(AddEffectCommand(repository, effect))
        registry.undo()
        registry.redo()

        assertEquals(listOf("effect-1"), repository.getEffectsForProject(PROJECT_ID).map { it.id })
    }

    @Test
    fun `undo delete effect restores effect and selection`() = runBlocking {
        val effect = effect("effect-1")
        val repository = FakeEffectRepository(listOf(effect))
        val controller = SelectionController(SelectionTarget.Effect(effect.id))
        val registry = HistoryRegistry()

        registry.execute(DeleteEffectCommand(repository, effect, controller))
        registry.undo()

        assertEquals(listOf("effect-1"), repository.getEffectsForProject(PROJECT_ID).map { it.id })
        assertEquals(SelectionTarget.Effect("effect-1"), controller.current)
    }

    @Test
    fun `redo delete effect removes effect again`() = runBlocking {
        val effect = effect("effect-1")
        val repository = FakeEffectRepository(listOf(effect))
        val controller = SelectionController(SelectionTarget.Effect(effect.id))
        val registry = HistoryRegistry()

        registry.execute(DeleteEffectCommand(repository, effect, controller))
        registry.undo()
        registry.redo()

        assertEquals(emptyList<EffectItem>(), repository.getEffectsForProject(PROJECT_ID))
        assertEquals(SelectionTarget.None, controller.current)
    }

    @Test
    fun `undo selection change restores previous selection`() = runBlocking {
        val controller = SelectionController(SelectionTarget.Clip("clip-1"))
        val registry = HistoryRegistry()

        registry.execute(SelectEffectCommand(controller, "effect-1"))
        registry.undo()

        assertEquals(SelectionTarget.Clip("clip-1"), controller.current)
    }

    @Test
    fun `redo selection change selects effect again`() = runBlocking {
        val controller = SelectionController(SelectionTarget.Clip("clip-1"))
        val registry = HistoryRegistry()

        registry.execute(SelectEffectCommand(controller, "effect-1"))
        registry.undo()
        registry.redo()

        assertEquals(SelectionTarget.Effect("effect-1"), controller.current)
    }

    private fun effect(id: String) = EffectItem(
        id = id,
        projectId = PROJECT_ID,
        effectId = "blur",
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
