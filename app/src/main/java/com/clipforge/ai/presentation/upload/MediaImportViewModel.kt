package com.clipforge.ai.presentation.upload

import android.app.Application
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipforge.ai.data.local.database.ClipForgeDatabase
import com.clipforge.ai.data.local.entity.MediaAssetEntity
import com.clipforge.ai.data.local.entity.TimelineItemEntity
import com.clipforge.ai.core.storage.MediaPickerManager
import com.clipforge.ai.domain.model.MediaType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID

private const val TAG = "MediaImportVM"

data class ImportedAsset(
    val id: String,
    val uri: Uri,
    val mediaType: MediaType,
    val mimeType: String?,
    val displayName: String,
    val sizeBytes: Long,
    val durationMs: Long? = null
)

data class MediaImportUiState(
    val assets: List<ImportedAsset> = emptyList(),
    val isLoading: Boolean          = false,
    val isSaving: Boolean           = false,
    val savedCount: Int             = 0,
    val error: String?              = null
) {
    val canProceed get() = assets.isNotEmpty()
}

class MediaImportViewModel(application: Application) : AndroidViewModel(application) {

    private val db      = ClipForgeDatabase.getInstance(application)
    private val picker  = MediaPickerManager()

    private val _uiState = MutableStateFlow(MediaImportUiState())
    val uiState: StateFlow<MediaImportUiState> = _uiState.asStateFlow()

    // ── Add picked visual media ───────────────────────────────────────────
    fun onMediaPicked(uris: List<Uri>, targetType: MediaType) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val ctx = getApplication<Application>()
            val newAssets = uris.mapNotNull { uri -> buildAsset(ctx, uri, targetType) }
            Log.d(TAG, "Picked ${newAssets.size} assets (type=$targetType)")
            val merged = (_uiState.value.assets + newAssets).distinctBy { it.uri.toString() }
            _uiState.value = _uiState.value.copy(assets = merged, isLoading = false)
        }
    }

    // ── Add audio from document picker ────────────────────────────────────
    fun onAudioPicked(uris: List<Uri>) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val ctx = getApplication<Application>()
            val newAssets = uris.mapNotNull { uri -> buildAsset(ctx, uri, MediaType.AUDIO) }
            Log.d(TAG, "Picked ${newAssets.size} audio assets")
            val merged = (_uiState.value.assets + newAssets).distinctBy { it.uri.toString() }
            _uiState.value = _uiState.value.copy(assets = merged, isLoading = false)
        }
    }

    fun removeAsset(id: String) {
        _uiState.value = _uiState.value.copy(
            assets = _uiState.value.assets.filter { it.id != id }
        )
    }

    fun moveUp(id: String) {
        val list = _uiState.value.assets.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx > 0) { val t = list[idx-1]; list[idx-1] = list[idx]; list[idx] = t }
        _uiState.value = _uiState.value.copy(assets = list)
    }

    fun moveDown(id: String) {
        val list = _uiState.value.assets.toMutableList()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < list.size - 1) { val t = list[idx+1]; list[idx+1] = list[idx]; list[idx] = t }
        _uiState.value = _uiState.value.copy(assets = list)
    }

    // ── Save assets + timeline items to Room then navigate ────────────────
    fun saveAndContinue(projectId: String, onDone: () -> Unit) {
        val assets = _uiState.value.assets
        Log.d(TAG, "MediaImport Continue clicked projectId=$projectId assets=${assets.size}")
        if (assets.isEmpty()) { onDone(); return }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isSaving = true)
            Log.d(TAG, "Saving ${assets.size} imported assets for project $projectId")

            withContext(Dispatchers.IO) {
                // Clear previous items for this project
                db.timelineDao().deleteAllForProject(projectId)

                val mediaEntities   = mutableListOf<MediaAssetEntity>()
                val timelineEntities = mutableListOf<TimelineItemEntity>()

                var primaryTrackEndMs = 0L

                assets.forEachIndexed { index, asset ->
                    val assetEntity = MediaAssetEntity(
                        id            = asset.id,
                        projectId     = projectId,
                        mediaType     = asset.mediaType.name,
                        localUri      = picker.copyToAppStorage(getApplication(), asset.uri, asset.id, asset.mimeType) ?: asset.uri.toString(),
                        remoteUrl     = null,
                        durationMs    = asset.durationMs,
                        fileSizeBytes = asset.sizeBytes,
                        mimeType      = asset.mimeType,
                        createdAt     = System.currentTimeMillis()
                    )
                    mediaEntities.add(assetEntity)

                    // Default clip duration: use actual duration or 3000ms
                    val clipDur = asset.durationMs ?: 3_000L
                    val isPrimaryVisual = asset.mediaType == MediaType.VIDEO || asset.mediaType == MediaType.IMAGE
                    val startMs = if (isPrimaryVisual) primaryTrackEndMs else 0L
                    val trackIndex = when (asset.mediaType) {
                        MediaType.AUDIO -> 1
                        MediaType.LOGO, MediaType.OVERLAY_IMAGE, MediaType.OVERLAY_VIDEO -> 2
                        else -> 0
                    }

                    val timelineEntity = TimelineItemEntity(
                        id               = UUID.randomUUID().toString(),
                        projectId        = projectId,
                        mediaAssetId     = asset.id,
                        trackIndex       = trackIndex,
                        orderIndex       = index,
                        startMs          = startMs,
                        endMs            = startMs + clipDur,
                        trimStartMs      = 0L,
                        trimEndMs        = 0L,
                        fitMode          = "FIT",
                        transitionType   = null,
                        transitionDurationMs = null,
                        volume           = 1.0f,
                        opacity          = 1.0f
                    )
                    timelineEntities.add(timelineEntity)
                    if (isPrimaryVisual) primaryTrackEndMs += clipDur
                }

                // Batch save
                db.mediaAssetDao().upsertAll(mediaEntities)
                Log.d(TAG, "Saved ${mediaEntities.size} media assets to Room")

                db.timelineDao().upsertAll(timelineEntities)
                Log.d(TAG, "Saved ${timelineEntities.size} timeline items to Room")

                val verify = db.timelineDao().countForProject(projectId)
                Log.d(TAG, "Verified timeline items in DB for project $projectId: $verify")
            }

            _uiState.value = _uiState.value.copy(isSaving = false, savedCount = assets.size)
            onDone()
        }
    }

    // ── Build an ImportedAsset from a Uri ─────────────────────────────────
    private suspend fun buildAsset(ctx: Context, uri: Uri, hint: MediaType): ImportedAsset? =
        withContext(Dispatchers.IO) {
            try {
                val mime = ctx.contentResolver.getType(uri) ?: return@withContext null
                val size = picker.getFileSizeBytes(ctx, uri)
                val name = queryDisplayName(ctx, uri)
                val type = resolveType(mime, hint)
                val dur  = if (type == MediaType.VIDEO || type == MediaType.AUDIO ||
                    type == MediaType.OVERLAY_VIDEO) queryDuration(ctx, uri) else null
                ImportedAsset(
                    id          = UUID.randomUUID().toString(),
                    uri         = uri,
                    mediaType   = type,
                    mimeType    = mime,
                    displayName = name,
                    sizeBytes   = size,
                    durationMs  = dur
                )
            } catch (e: Exception) {
                Log.e(TAG, "Failed to build asset from $uri: ${e.message}")
                null
            }
        }

    private fun resolveType(mime: String, hint: MediaType): MediaType = when {
        hint == MediaType.LOGO           -> MediaType.LOGO
        hint == MediaType.OVERLAY_IMAGE  -> MediaType.OVERLAY_IMAGE
        hint == MediaType.OVERLAY_VIDEO  -> MediaType.OVERLAY_VIDEO
        mime.startsWith("video")         -> MediaType.VIDEO
        mime.startsWith("image")         -> MediaType.IMAGE
        mime.startsWith("audio")         -> MediaType.AUDIO
        else                             -> MediaType.IMAGE
    }

    private fun queryDisplayName(ctx: Context, uri: Uri): String =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
            ?: uri.lastPathSegment ?: "file"

    private fun queryDuration(ctx: Context, uri: Uri): Long? = try {
        val mmr = MediaMetadataRetriever()
        mmr.setDataSource(ctx, uri)
        val ms = mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        mmr.release()
        ms
    } catch (e: Exception) { null }
}
