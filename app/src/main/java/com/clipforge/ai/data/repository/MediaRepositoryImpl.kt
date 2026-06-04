package com.clipforge.ai.data.repository
import android.net.Uri
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.data.local.dao.MediaAssetDao
import com.clipforge.ai.data.local.entity.MediaAssetEntity
import com.clipforge.ai.data.remote.api.MediaApi
import com.clipforge.ai.domain.model.MediaAsset
import com.clipforge.ai.domain.model.MediaType
import com.clipforge.ai.domain.repository.MediaRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
class MediaRepositoryImpl(private val mediaAssetDao: MediaAssetDao, private val mediaApi: MediaApi) : MediaRepository {
    override fun getAssetsForProject(projectId: String): Flow<List<MediaAsset>> = mediaAssetDao.getAssetsForProject(projectId).map { it.map { e -> e.toDomain() } }
    override suspend fun addMediaAsset(asset: MediaAsset): NetworkResult<MediaAsset> { mediaAssetDao.upsertAsset(asset.toEntity()); return NetworkResult.Success(asset) }
    override suspend fun uploadMediaAsset(assetId: String, uri: Uri): NetworkResult<String> = NetworkResult.Error(message = "Not yet implemented")
    override suspend fun deleteMediaAsset(assetId: String): NetworkResult<Unit> { mediaAssetDao.deleteAssetById(assetId); return NetworkResult.Success(Unit) }
    private fun MediaAssetEntity.toDomain() = MediaAsset(id, projectId, MediaType.valueOf(mediaType), localUri, remoteUrl, durationMs, fileSizeBytes, mimeType, createdAt)
    private fun MediaAsset.toEntity() = MediaAssetEntity(id, projectId, mediaType.name, localUri, remoteUrl, durationMs, fileSizeBytes, mimeType, createdAt)
}
