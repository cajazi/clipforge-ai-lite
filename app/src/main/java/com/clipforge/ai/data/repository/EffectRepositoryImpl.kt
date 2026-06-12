package com.clipforge.ai.data.repository

import android.util.Log
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.data.local.dao.EffectItemDao
import com.clipforge.ai.data.local.entity.EffectItemEntity
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.repository.EffectRepository

private const val TAG = "EffectRepository"

class EffectRepositoryImpl(
    private val effectItemDao: EffectItemDao
) : EffectRepository {

    override suspend fun getEffectsForProject(projectId: String): List<EffectItem> {
        return effectItemDao.getForProject(projectId).mapNotNull { entity ->
            runCatching { entity.toDomain() }
                .onFailure { Log.w(TAG, "Skipping invalid effect row id=${entity.id} projectId=$projectId: ${it.message}") }
                .getOrNull()
        }
    }

    override suspend fun upsertEffect(effect: EffectItem) {
        require(effect.scope == EffectScope.GLOBAL) { "Only GLOBAL effect scope is writable in C2" }
        effectItemDao.upsert(effect.toEntity())
    }

    override suspend fun deleteEffect(id: String) {
        effectItemDao.deleteById(id)
    }

    override suspend fun deleteEffectsForProject(projectId: String) {
        effectItemDao.deleteForProject(projectId)
    }
}

fun EffectItem.toEntity(): EffectItemEntity {
    require(scope == EffectScope.GLOBAL) { "Only GLOBAL effect scope is writable in C2" }
    return EffectItemEntity(
        id = id,
        projectId = projectId,
        effectId = effectId,
        scope = scope.name,
        startMs = startMs,
        endMs = endMs,
        zOrder = zOrder,
        paramsJson = EffectParamsCodec.encode(params)
    )
}

fun EffectItemEntity.toDomain(): EffectItem {
    val parsedScope = runCatching { EffectScope.valueOf(scope) }
        .getOrElse { throw IllegalArgumentException("Unknown effect scope '$scope'", it) }
    require(parsedScope == EffectScope.GLOBAL) { "CLIP effect scope is reserved in C2" }
    return EffectItem(
        id = id,
        projectId = projectId,
        effectId = effectId,
        scope = parsedScope,
        startMs = startMs,
        endMs = endMs,
        zOrder = zOrder,
        params = EffectParamsCodec.decode(paramsJson)
    )
}
