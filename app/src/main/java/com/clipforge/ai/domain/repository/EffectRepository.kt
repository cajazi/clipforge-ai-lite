package com.clipforge.ai.domain.repository

import com.clipforge.ai.domain.model.EffectItem
import kotlinx.coroutines.flow.Flow

interface EffectRepository {
    suspend fun getEffectsForProject(projectId: String): List<EffectItem>
    fun observeEffectsForProject(projectId: String): Flow<List<EffectItem>>
    suspend fun upsertEffect(effect: EffectItem)
    suspend fun deleteEffect(id: String)
    suspend fun deleteEffectsForProject(projectId: String)
}
