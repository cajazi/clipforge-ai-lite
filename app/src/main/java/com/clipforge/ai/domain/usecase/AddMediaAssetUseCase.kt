package com.clipforge.ai.domain.usecase
import android.net.Uri
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.model.MediaAsset
import com.clipforge.ai.domain.model.MediaType
import com.clipforge.ai.domain.repository.MediaRepository
import java.util.UUID
class AddMediaAssetUseCase(private val repository: MediaRepository) {
    suspend operator fun invoke(projectId: String, uri: Uri, mediaType: MediaType,
        fileSizeBytes: Long = 0L, mimeType: String? = null): NetworkResult<MediaAsset> =
        repository.addMediaAsset(MediaAsset(id = UUID.randomUUID().toString(),
            projectId = projectId, mediaType = mediaType, localUri = uri.toString(),
            fileSizeBytes = fileSizeBytes, mimeType = mimeType))
}
