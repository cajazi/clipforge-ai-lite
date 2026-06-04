package com.clipforge.ai.domain.repository
import android.net.Uri
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.model.MediaAsset
import kotlinx.coroutines.flow.Flow
interface MediaRepository {
    fun getAssetsForProject(projectId: String): Flow<List<MediaAsset>>
    suspend fun addMediaAsset(asset: MediaAsset): NetworkResult<MediaAsset>
    suspend fun uploadMediaAsset(assetId: String, uri: Uri): NetworkResult<String>
    suspend fun deleteMediaAsset(assetId: String): NetworkResult<Unit>
}
