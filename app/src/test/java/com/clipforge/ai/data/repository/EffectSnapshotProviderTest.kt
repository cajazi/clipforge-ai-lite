package com.clipforge.ai.data.repository

import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import com.clipforge.ai.domain.repository.EffectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class EffectSnapshotProviderTest {
    @Test
    fun `capture empty effects`() = runBlocking {
        val provider = EffectSnapshotProvider(FakeEffectRepository())

        assertEquals("effect_items", provider.providerId)
        assertEquals(emptyList<EffectItem>(), provider.capture(PROJECT_ID).effects)
    }

    @Test
    fun `capture populated effects preserves deterministic ordering`() = runBlocking {
        val provider = EffectSnapshotProvider(
            FakeEffectRepository(
                listOf(
                    effect(id = "b", startMs = 500L, endMs = 900L, zOrder = 1),
                    effect(id = "a", startMs = 100L, endMs = 400L, zOrder = 1),
                    effect(id = "z", projectId = OTHER_PROJECT, startMs = 0L, endMs = 1_000L, zOrder = 0),
                    effect(id = "c", startMs = 0L, endMs = 100L, zOrder = 0)
                )
            )
        )

        assertEquals(listOf("c", "a", "b"), provider.capture(PROJECT_ID).effects.map { it.id })
    }

    @Test
    fun `restore replaces existing project effects only`() = runBlocking {
        val repository = FakeEffectRepository(
            listOf(
                effect(id = "stale", startMs = 0L, endMs = 100L, zOrder = 0),
                effect(id = "other", projectId = OTHER_PROJECT, startMs = 0L, endMs = 100L, zOrder = 0)
            )
        )
        val provider = EffectSnapshotProvider(repository)
        val snapshot = EffectSnapshot(
            effects = listOf(
                effect(id = "fresh-2", startMs = 300L, endMs = 600L, zOrder = 1),
                effect(id = "fresh-1", startMs = 100L, endMs = 300L, zOrder = 0)
            )
        )

        provider.restore(PROJECT_ID, snapshot)

        assertEquals(listOf("fresh-1", "fresh-2"), repository.getEffectsForProject(PROJECT_ID).map { it.id })
        assertEquals(listOf("other"), repository.getEffectsForProject(OTHER_PROJECT).map { it.id })
    }

    @Test
    fun `restore preserves params json effect id window and z order`() = runBlocking {
        val repository = FakeEffectRepository()
        val provider = EffectSnapshotProvider(repository)
        val expected = effect(
            id = "effect-item-1",
            effectId = "vhs",
            startMs = 120L,
            endMs = 980L,
            zOrder = 7,
            params = mapOf("intensity" to EffectParamValue.Constant(0.75f))
        )

        provider.restore(PROJECT_ID, EffectSnapshot(listOf(expected)))

        val restored = repository.getEffectsForProject(PROJECT_ID).single()
        assertEquals(expected.effectId, restored.effectId)
        assertEquals(expected.startMs, restored.startMs)
        assertEquals(expected.endMs, restored.endMs)
        assertEquals(expected.zOrder, restored.zOrder)
        assertEquals(expected.toEntity().paramsJson, restored.toEntity().paramsJson)
    }

    @Test
    fun `provider owns no runtime state`() = runBlocking {
        val repository = FakeEffectRepository()
        val provider = EffectSnapshotProvider(repository)

        repository.upsertEffect(effect(id = "first", startMs = 0L, endMs = 100L, zOrder = 0))
        assertEquals(listOf("first"), provider.capture(PROJECT_ID).effects.map { it.id })

        repository.upsertEffect(effect(id = "second", startMs = 100L, endMs = 200L, zOrder = 1))
        assertEquals(listOf("first", "second"), provider.capture(PROJECT_ID).effects.map { it.id })
    }

    private fun effect(
        id: String,
        projectId: String = PROJECT_ID,
        effectId: String = "tint",
        startMs: Long,
        endMs: Long,
        zOrder: Int,
        params: Map<String, EffectParamValue> = emptyMap()
    ) = EffectItem(
        id = id,
        projectId = projectId,
        effectId = effectId,
        scope = EffectScope.GLOBAL,
        startMs = startMs,
        endMs = endMs,
        zOrder = zOrder,
        params = params
    )

    private class FakeEffectRepository(initial: List<EffectItem> = emptyList()) : EffectRepository {
        private val rows = MutableStateFlow(initial)

        override suspend fun getEffectsForProject(projectId: String): List<EffectItem> =
            rows.value
                .filter { it.projectId == projectId }
                .sortedWith(compareBy<EffectItem> { it.zOrder }.thenBy { it.startMs }.thenBy { it.id })

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
