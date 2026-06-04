package com.clipforge.ai.data.local.dao

import androidx.room.*
import com.clipforge.ai.data.local.entity.TimelineItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface TimelineDao {
    @Query("SELECT * FROM timeline_items WHERE projectId = :projectId ORDER BY trackIndex ASC, orderIndex ASC")
    fun getTimelineForProject(projectId: String): Flow<List<TimelineItemEntity>>

    @Query("SELECT * FROM timeline_items WHERE projectId = :projectId ORDER BY trackIndex ASC, orderIndex ASC")
    suspend fun getTimelineForProjectOnce(projectId: String): List<TimelineItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTimelineItem(item: TimelineItemEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<TimelineItemEntity>)

    @Query("DELETE FROM timeline_items WHERE id = :itemId")
    suspend fun deleteItemById(itemId: String)

    @Query("DELETE FROM timeline_items WHERE projectId = :projectId")
    suspend fun deleteAllForProject(projectId: String)

    @Query("UPDATE timeline_items SET transitionType = :type, transitionDurationMs = :durationMs WHERE id = :itemId")
    suspend fun updateTransition(itemId: String, type: String?, durationMs: Long?)

    @Query("UPDATE timeline_items SET transitionType = :type, transitionDurationMs = :durationMs WHERE projectId = :projectId")
    suspend fun updateAllTransitions(projectId: String, type: String?, durationMs: Long?)

    @Query("SELECT COUNT(*) FROM timeline_items WHERE projectId = :projectId")
    suspend fun countForProject(projectId: String): Int

    @Query("SELECT * FROM media_assets WHERE id = :assetId LIMIT 1")
    suspend fun getMediaAssetForItem(assetId: String): com.clipforge.ai.data.local.entity.MediaAssetEntity?
}
