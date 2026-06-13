package com.clipforge.ai.domain.effects

import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.repository.EffectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class EffectLaneTest {
    @Test
    fun `lane delegates add update remove to repository`() = runBlocking {
        val repository = FakeEffectRepository()
        val lane = EffectLane(PROJECT_ID, repository)
        val effect = effect("effect-1", startMs = 0L, endMs = 100L)

        lane.addEffect(effect)
        lane.updateEffect(effect.copy(endMs = 200L))
        lane.removeEffect(effect.id)

        assertEquals(emptyList<EffectItem>(), repository.getEffectsForProject(PROJECT_ID))
    }

    @Test
    fun `lane flow is project scoped and ordered`() = runBlocking {
        val lane = EffectLane(
            PROJECT_ID,
            FakeEffectRepository(
                listOf(
                    effect("b", startMs = 200L, endMs = 300L, zOrder = 1),
                    effect("other", projectId = OTHER_PROJECT, startMs = 0L, endMs = 100L, zOrder = 0),
                    effect("a", startMs = 100L, endMs = 200L, zOrder = 1),
                    effect("c", startMs = 0L, endMs = 100L, zOrder = 0)
                )
            )
        )

        assertEquals(listOf("c", "a", "b"), lane.effects.first().map { it.id })
    }

    @Test
    fun `lane rejects cross project upsert`() {
        val lane = EffectLane(PROJECT_ID, FakeEffectRepository())

        assertThrows(IllegalArgumentException::class.java) {
            runBlocking {
                lane.addEffect(effect("wrong", projectId = OTHER_PROJECT, startMs = 0L, endMs = 100L))
            }
        }
    }

    private fun effect(
        id: String,
        projectId: String = PROJECT_ID,
        startMs: Long,
        endMs: Long,
        zOrder: Int = 0
    ) = EffectItem(
        id = id,
        projectId = projectId,
        effectId = "tint",
        scope = EffectScope.GLOBAL,
        startMs = startMs,
        endMs = endMs,
        zOrder = zOrder,
        params = emptyMap()
    )

    private class FakeEffectRepository(initial: List<EffectItem> = emptyList()) : EffectRepository {
        private val rows = MutableStateFlow(initial)

        override suspend fun getEffectsForProject(projectId: String): List<EffectItem> =
            rows.value.filter { it.projectId == projectId }

        override fun observeEffectsForProject(projectId: String): Flow<List<EffectItem>> = rows

        override suspend fun upsertEffect(effect: EffectItem) {
            rows.value = rows.value.filterNot { it.id == effect.id } + effect
        }

        override suspend fun deleteEffect(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
        }

        override suspend fun deleteEffectsForProject(projectId: String) {
            rows.value = rows.value.filterNot { it.projectId == projectId }
        }
    }

    private companion object {
        const val PROJECT_ID = "project"
        const val OTHER_PROJECT = "other"
    }
}
