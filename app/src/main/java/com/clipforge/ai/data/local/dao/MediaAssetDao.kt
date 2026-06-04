package com.clipforge.ai.data.local.dao

import androidx.room.*
import com.clipforge.ai.data.local.entity.MediaAssetEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MediaAssetDao {
    @Query("SELECT * FROM media_assets WHERE projectId = :projectId ORDER BY createdAt ASC")
    fun getAssetsForProject(projectId: String): Flow<List<MediaAssetEntity>>

    @Query("SELECT * FROM media_assets WHERE projectId = :projectId ORDER BY createdAt ASC")
    suspend fun getAssetsForProjectOnce(projectId: String): List<MediaAssetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAsset(asset: MediaAssetEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(assets: List<MediaAssetEntity>)

    @Query("DELETE FROM media_assets WHERE id = :assetId")
    suspend fun deleteAssetById(assetId: String)

    @Query("UPDATE media_assets SET remoteUrl = :remoteUrl WHERE id = :assetId")
    suspend fun updateRemoteUrl(assetId: String, remoteUrl: String)

    @Query("SELECT COUNT(*) FROM media_assets WHERE projectId = :projectId")
    suspend fun countForProject(projectId: String): Int
}
