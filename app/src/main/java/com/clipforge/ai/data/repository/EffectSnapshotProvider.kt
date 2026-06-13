package com.clipforge.ai.data.repository

import com.clipforge.ai.domain.history.SnapshotProvider
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.repository.EffectRepository

data class EffectSnapshot(
    val effects: List<EffectItem>
)

class EffectSnapshotProvider(
    private val repository: EffectRepository
) : SnapshotProvider<EffectSnapshot> {
    override val providerId: String = PROVIDER_ID

    override suspend fun capture(projectId: String): EffectSnapshot =
        EffectSnapshot(
            effects = repository.getEffectsForProject(projectId)
                .filter { it.projectId == projectId }
                .sortedWith(effectOrdering)
        )

    override suspend fun restore(projectId: String, snapshot: EffectSnapshot) {
        repository.deleteEffectsForProject(projectId)
        snapshot.effects
            .filter { it.projectId == projectId }
            .sortedWith(effectOrdering)
            .forEach { repository.upsertEffect(it) }
    }

    private companion object {
        const val PROVIDER_ID = "effect_items"

        val effectOrdering: Comparator<EffectItem> =
            compareBy<EffectItem> { it.zOrder }
                .thenBy { it.startMs }
                .thenBy { it.id }
    }
}
