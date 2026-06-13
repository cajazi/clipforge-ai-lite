package com.clipforge.ai.domain.effects

import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.repository.EffectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class EffectLane(
    private val projectId: String,
    private val repository: EffectRepository
) {
    val effects: Flow<List<EffectItem>> =
        repository.observeEffectsForProject(projectId).map { effects ->
            effects
                .filter { it.projectId == projectId }
                .sortedWith(effectOrdering)
        }

    suspend fun addEffect(effect: EffectItem) {
        require(effect.projectId == projectId) { "Effect belongs to a different project" }
        repository.upsertEffect(effect)
    }

    suspend fun updateEffect(effect: EffectItem) {
        require(effect.projectId == projectId) { "Effect belongs to a different project" }
        repository.upsertEffect(effect)
    }

    suspend fun removeEffect(effectId: String) {
        repository.deleteEffect(effectId)
    }

    private companion object {
        val effectOrdering: Comparator<EffectItem> =
            compareBy<EffectItem> { it.zOrder }
                .thenBy { it.startMs }
                .thenBy { it.id }
    }
}
