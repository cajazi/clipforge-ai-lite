package com.clipforge.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.clipforge.ai.data.local.entity.EffectItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface EffectItemDao {
    @Query("SELECT * FROM effect_items WHERE projectId = :projectId ORDER BY zOrder ASC, startMs ASC, id ASC")
    suspend fun getForProject(projectId: String): List<EffectItemEntity>

    @Query("SELECT * FROM effect_items WHERE projectId = :projectId ORDER BY zOrder ASC, startMs ASC, id ASC")
    fun observeForProject(projectId: String): Flow<List<EffectItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: EffectItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<EffectItemEntity>)

    @Query("DELETE FROM effect_items WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM effect_items WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}
