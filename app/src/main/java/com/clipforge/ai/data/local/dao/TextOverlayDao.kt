package com.clipforge.ai.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.clipforge.ai.data.local.entity.TextOverlayEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TextOverlayDao {
    @Query("SELECT * FROM text_overlays WHERE projectId = :projectId ORDER BY zIndex ASC, windowStartMs ASC, id ASC")
    suspend fun getForProject(projectId: String): List<TextOverlayEntity>

    @Query("SELECT * FROM text_overlays WHERE projectId = :projectId ORDER BY zIndex ASC, windowStartMs ASC, id ASC")
    fun observeForProject(projectId: String): Flow<List<TextOverlayEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: TextOverlayEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<TextOverlayEntity>)

    @Query("DELETE FROM text_overlays WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("DELETE FROM text_overlays WHERE projectId = :projectId")
    suspend fun deleteForProject(projectId: String)
}
