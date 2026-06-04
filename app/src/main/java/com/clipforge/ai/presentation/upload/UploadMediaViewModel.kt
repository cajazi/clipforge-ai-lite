package com.clipforge.ai.presentation.upload

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.core.storage.MediaPickerManager
import com.clipforge.ai.domain.model.MediaType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

private const val TAG = "UploadMediaVM"

data class MediaItem(
    val uri: Uri,
    val mediaType: MediaType,
    val mimeType: String?,
    val sizeBytes: Long,
    val displayName: String,
    val uploadedUrl: String? = null,
    val isUploading: Boolean = false,
    val uploadFailed: Boolean = false
)

data class UploadUiState(
    val mediaItems: List<MediaItem> = emptyList(),
    val isUploading: Boolean = false,
    val uploadProgress: Int = 0,
    val error: String? = null,
    val canProceed: Boolean = false,
    val allUploaded: Boolean = false
)

class UploadMediaViewModel(application: Application) : AndroidViewModel(application) {

    private val app           = application as ClipForgeApp
    private val storage       = app.supabaseStorage
    private val pickerManager = MediaPickerManager()

    private val _uiState = MutableStateFlow(UploadUiState())
    val uiState: StateFlow<UploadUiState> = _uiState.asStateFlow()

    fun onMediaPicked(uris: List<Uri>) {
        viewModelScope.launch {
            val context = getApplication<Application>()
            val newItems = uris.map { uri ->
                val mime = pickerManager.getMimeType(context, uri)
                val size = pickerManager.getFileSizeBytes(context, uri)
                val type = when {
                    mime?.startsWith("video") == true -> MediaType.VIDEO
                    mime?.startsWith("image") == true -> MediaType.IMAGE
                    mime?.startsWith("audio") == true -> MediaType.AUDIO
                    else -> MediaType.IMAGE
                }
                val name = uri.lastPathSegment?.substringAfterLast("/") ?: "media"
                MediaItem(uri = uri, mediaType = type, mimeType = mime, sizeBytes = size, displayName = name)
            }
            val updated = (_uiState.value.mediaItems + newItems).distinctBy { it.uri }
            _uiState.value = _uiState.value.copy(mediaItems = updated, canProceed = updated.isNotEmpty())
        }
    }

    fun removeItem(uri: Uri) {
        val updated = _uiState.value.mediaItems.filter { it.uri != uri }
        _uiState.value = _uiState.value.copy(
            mediaItems = updated,
            canProceed = updated.isNotEmpty(),
            allUploaded = updated.isNotEmpty() && updated.all { it.uploadedUrl != null }
        )
    }

    /** Upload all picked media to Supabase Storage under {userId}/filename */
    fun uploadAllToSupabase(onComplete: () -> Unit) {
        val items = _uiState.value.mediaItems
        if (items.isEmpty()) { onComplete(); return }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isUploading = true, error = null)
            var uploaded = 0

            items.forEachIndexed { index, item ->
                // Mark as uploading
                updateItem(item.uri) { it.copy(isUploading = true) }

                val result = when (item.mediaType) {
                    MediaType.AUDIO -> storage.uploadAudio(item.uri)
                    else            -> storage.uploadMedia(item.uri)
                }

                when (result) {
                    is NetworkResult.Success -> {
                        uploaded++
                        val progress = ((uploaded.toFloat() / items.size) * 100).toInt()
                        updateItem(item.uri) { it.copy(isUploading = false, uploadedUrl = result.data) }
                        _uiState.value = _uiState.value.copy(uploadProgress = progress)
                        Log.d(TAG, "Uploaded ${item.displayName}: ${result.data}")
                    }
                    is NetworkResult.Error -> {
                        updateItem(item.uri) { it.copy(isUploading = false, uploadFailed = true) }
                        Log.e(TAG, "Failed to upload ${item.displayName}: ${result.message}")
                    }
                    else -> {}
                }
            }

            val allDone = _uiState.value.mediaItems.all { it.uploadedUrl != null }
            _uiState.value = _uiState.value.copy(
                isUploading = false,
                allUploaded = allDone,
                canProceed  = true
            )
            onComplete()
        }
    }

    private fun updateItem(uri: Uri, transform: (MediaItem) -> MediaItem) {
        _uiState.value = _uiState.value.copy(
            mediaItems = _uiState.value.mediaItems.map { if (it.uri == uri) transform(it) else it }
        )
    }

    fun clearError() { _uiState.value = _uiState.value.copy(error = null) }
}
