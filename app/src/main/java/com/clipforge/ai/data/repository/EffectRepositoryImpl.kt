package com.clipforge.ai.data.repository

import android.util.Log
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.data.local.dao.EffectItemDao
import com.clipforge.ai.data.local.entity.EffectItemEntity
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.repository.EffectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val TAG = "EffectRepository"

class EffectRepositoryImpl(
    private val effectItemDao: EffectItemDao
) : EffectRepository {

    override suspend fun getEffectsForProject(projectId: String): List<EffectItem> {
        return effectItemDao.getForProject(projectId).toDomainList(projectId)
    }

    override fun observeEffectsForProject(projectId: String): Flow<List<EffectItem>> =
        effectItemDao.observeForProject(projectId).map { entities -> entities.toDomainList(projectId) }

    override suspend fun upsertEffect(effect: EffectItem) {
        require(effect.isWritableScope()) { "Only GLOBAL effects and CLIP transform_animation are writable" }
        effectItemDao.upsert(effect.toEntity())
    }

    override suspend fun deleteEffect(id: String) {
        effectItemDao.deleteById(id)
    }

    override suspend fun deleteEffectsForProject(projectId: String) {
        effectItemDao.deleteForProject(projectId)
    }
}

private fun List<EffectItemEntity>.toDomainList(projectId: String): List<EffectItem> =
    mapNotNull { entity ->
        runCatching { entity.toDomain() }
            .onFailure { Log.w(TAG, "Skipping invalid effect row id=${entity.id} projectId=$projectId: ${it.message}") }
            .getOrNull()
    }

fun EffectItem.toEntity(): EffectItemEntity {
    require(isWritableScope()) { "Only GLOBAL effects and CLIP transform_animation are writable" }
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
    require(parsedScope == EffectScope.GLOBAL || (parsedScope == EffectScope.CLIP && effectId == AnimationEffectRegistrations.TRANSFORM_ANIMATION)) {
        "Only GLOBAL effects and CLIP transform_animation are readable"
    }
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

private fun EffectItem.isWritableScope(): Boolean =
    scope == EffectScope.GLOBAL || (scope == EffectScope.CLIP && effectId == AnimationEffectRegistrations.TRANSFORM_ANIMATION)
