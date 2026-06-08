package com.clipforge.ai.core.export

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import com.clipforge.ai.core.gl.CrossfadeExecutor
import com.clipforge.ai.core.gl.ProjectExporter
import com.clipforge.ai.core.storage.UserPreferencesManager
import com.clipforge.ai.domain.model.RenderJobStatus
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ExportManagerState(
    val projectId: String? = null,
    val status: RenderJobStatus = RenderJobStatus.QUEUED,
    val progressPercent: Int = 0,
    val statusMessage: String = "Preparing export...",
    val outputUrl: String? = null,
    val publicUri: String? = null,
    val isGalleryVisible: Boolean = false,
    val errorMessage: String? = null,
    val quality: String = "720p",
    val hasWatermark: Boolean = true,
    val isPreparingTransition: Boolean = false,
    val canCancel: Boolean = false
)

class ExportManager(
    private val application: Application,
    private val prefsManager: UserPreferencesManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val _state = MutableStateFlow(ExportManagerState())
    val state: StateFlow<ExportManagerState> = _state.asStateFlow()

    private var activeJob: Job? = null
    private var activeProjectId: String? = null
    private var userCancelRequested = false

    @OptIn(UnstableApi::class)
    fun startExport(projectId: String) {
        val current = activeJob
        if (activeProjectId == projectId && _state.value.status in ACTIVE_STATUSES) {
            Log.d(TAG, "EXPORT_START_IGNORED_ALREADY_ACTIVE project=$projectId status=${_state.value.status}")
            return
        }
        if (activeProjectId != null && _state.value.status in ACTIVE_STATUSES) {
            Log.d(TAG, "EXPORT_START_IGNORED_OTHER_ACTIVE requested=$projectId active=$activeProjectId status=${_state.value.status}")
            return
        }
        if (current?.isActive == true && activeProjectId == projectId) {
            Log.d(TAG, "EXPORT_START_IGNORED_ALREADY_ACTIVE project=$projectId")
            return
        }
        if (current?.isActive == true) {
            Log.d(TAG, "EXPORT_START_IGNORED_OTHER_ACTIVE requested=$projectId active=$activeProjectId")
            return
        }

        userCancelRequested = false
        activeProjectId = projectId
        _state.value = ExportManagerState(
            projectId = projectId,
            status = RenderJobStatus.PROCESSING,
            statusMessage = "Preparing clips...",
            progressPercent = 0,
            canCancel = false
        )

        activeJob = scope.launch {
            try {
                Log.d(
                    TAG,
                    "EXPORT_START project=$projectId device=${Build.MANUFACTURER}/${Build.MODEL} " +
                        "sdk=${Build.VERSION.SDK_INT} thread=${Thread.currentThread().name}"
                )

                Log.d(TAG, "EXPORT_RESOLVE_PATHS_BEFORE project=$projectId")
                val paths = ProjectExporter.resolveAllVideoPaths(application, projectId)
                Log.d(TAG, "EXPORT_RESOLVE_PATHS_AFTER project=$projectId count=${paths.size} paths=$paths")
                if (paths.isEmpty()) {
                    finishFailed(projectId, "This project has no video asset.")
                    return@launch
                }

                _state.value = _state.value.copy(
                    status = RenderJobStatus.RENDERING,
                    statusMessage = "Preparing transitions...",
                    progressPercent = 0,
                    isPreparingTransition = true,
                    canCancel = false
                )

                CrossfadeExecutor.renderProjectTimeline(
                    context = application,
                    projectId = projectId,
                    onStage = { message ->
                        Log.d(TAG, "EXPORT_STAGE project=$projectId message=$message")
                        _state.value = _state.value.copy(
                            statusMessage = message,
                            isPreparingTransition = message.startsWith("Preparing", ignoreCase = true),
                            canCancel = !message.startsWith("Preparing", ignoreCase = true)
                        )
                    },
                    onProgress = { pct ->
                        Log.d(TAG, "EXPORT_PROGRESS project=$projectId pct=$pct")
                        _state.value = _state.value.copy(
                            status = RenderJobStatus.RENDERING,
                            progressPercent = pct,
                            statusMessage = "Rendering... $pct%",
                            isPreparingTransition = false,
                            canCancel = true
                        )
                    },
                    onResult = { result ->
                        when (result) {
                            is CrossfadeExecutor.Result.Done -> finishComplete(projectId, result)
                            is CrossfadeExecutor.Result.Error -> {
                                if (result.message.contains("Job was cancelled", ignoreCase = true) ||
                                    result.message.contains("CancellationException", ignoreCase = true)
                                ) {
                                    finishCancelled(projectId, if (userCancelRequested) "user" else "system")
                                } else {
                                    finishFailed(projectId, result.message)
                                }
                            }
                        }
                    }
                )
            } catch (e: CancellationException) {
                val reason = if (userCancelRequested) "user" else "system"
                Log.w(TAG, "EXPORT_CANCELLED project=$projectId reason=$reason message=${e.message}", e)
                finishCancelled(projectId, reason)
            } catch (t: Throwable) {
                Log.e(TAG, "EXPORT_FAILED project=$projectId message=${t.message}", t)
                finishFailed(projectId, t.message ?: t::class.java.name)
            }
        }
    }

    fun cancelExport(projectId: String) {
        if (activeProjectId != projectId) {
            Log.d(TAG, "EXPORT_CANCEL_IGNORED requested=$projectId active=$activeProjectId")
            return
        }
        if (_state.value.isPreparingTransition) {
            Log.d(TAG, "EXPORT_CANCEL_IGNORED_PREPARING project=$projectId")
            return
        }
        userCancelRequested = true
        Log.d(TAG, "EXPORT_CANCEL_REQUEST project=$projectId")
        activeJob?.cancel(CancellationException("User cancelled export"))
        finishCancelled(projectId, "user")
    }

    private fun finishComplete(projectId: String, result: CrossfadeExecutor.Result.Done) {
        if (activeProjectId != projectId) return
        Log.d(TAG, "EXPORT_COMPLETE project=$projectId path=${result.outputPath} bytes=${result.bytes} durationMs=${result.durationMs}")
        val mediaStoreResult = publishExportToMediaStore(result.outputPath)
        activeProjectId = null
        activeJob = null
        scope.launch {
            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
            prefsManager.incrementDailyExport(today)
        }
        _state.value = _state.value.copy(
            status = RenderJobStatus.COMPLETED,
            statusMessage = if (mediaStoreResult.uri != null) "Saved to Gallery" else "Export complete",
            progressPercent = 100,
            outputUrl = result.outputPath,
            publicUri = mediaStoreResult.uri?.toString(),
            isGalleryVisible = mediaStoreResult.isGalleryVisible,
            errorMessage = null,
            isPreparingTransition = false,
            canCancel = false
        )
    }

    private data class MediaStoreSaveResult(
        val uri: Uri?,
        val isGalleryVisible: Boolean
    )

    private fun publishExportToMediaStore(outputPath: String): MediaStoreSaveResult {
        val source = File(outputPath)
        if (!source.exists() || source.length() <= 0L) {
            Log.e(TAG, "EXPORT_MEDIASTORE_SAVE_FAILED error=source_missing path=$outputPath")
            Log.d(TAG, "EXPORT_GALLERY_VISIBLE=false")
            return MediaStoreSaveResult(uri = null, isGalleryVisible = false)
        }

        val resolver = application.contentResolver
        val displayName = "ClipForge_${SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())}.mp4"
        val collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Video.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MOVIES}/ClipForge AI")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
        }

        Log.d(
            TAG,
            "EXPORT_MEDIASTORE_SAVE_BEFORE source=$outputPath bytes=${source.length()} displayName=$displayName " +
                "relativePath=${Environment.DIRECTORY_MOVIES}/ClipForge AI"
        )

        var uri: Uri? = null
        return try {
            uri = resolver.insert(collection, values)
                ?: throw IllegalStateException("ContentResolver.insert returned null")
            resolver.openOutputStream(uri)?.use { out ->
                source.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("openOutputStream returned null")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val completeValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                resolver.update(uri, completeValues, null, null)
            }

            val visible = isMediaStoreVideoVisible(uri)
            Log.d(TAG, "EXPORT_MEDIASTORE_SAVE_AFTER uri=$uri")
            Log.d(TAG, "EXPORT_GALLERY_VISIBLE=$visible")
            MediaStoreSaveResult(uri = uri, isGalleryVisible = visible)
        } catch (t: Throwable) {
            Log.e(TAG, "EXPORT_MEDIASTORE_SAVE_FAILED error=${t.message}", t)
            uri?.let { failedUri ->
                try { resolver.delete(failedUri, null, null) } catch (_: Throwable) {}
            }
            Log.d(TAG, "EXPORT_GALLERY_VISIBLE=false")
            MediaStoreSaveResult(uri = null, isGalleryVisible = false)
        }
    }

    private fun isMediaStoreVideoVisible(uri: Uri): Boolean {
        return try {
            application.contentResolver.query(
                uri,
                arrayOf(MediaStore.Video.Media._ID),
                null,
                null,
                null
            )?.use { cursor -> cursor.moveToFirst() } == true
        } catch (t: Throwable) {
            Log.d(TAG, "EXPORT_GALLERY_VISIBLE_CHECK_FAILED error=${t.message}")
            false
        }
    }

    private fun finishFailed(projectId: String, message: String) {
        if (activeProjectId != projectId) return
        Log.e(TAG, "EXPORT_FAILED project=$projectId message=$message")
        activeProjectId = null
        activeJob = null
        _state.value = _state.value.copy(
            status = RenderJobStatus.FAILED,
            statusMessage = "Export failed",
            errorMessage = message,
            isPreparingTransition = false,
            canCancel = false
        )
    }

    private fun finishCancelled(projectId: String, reason: String) {
        if (activeProjectId != projectId && _state.value.projectId != projectId) return
        Log.d(TAG, "EXPORT_CANCELLED project=$projectId reason=$reason")
        activeProjectId = null
        activeJob = null
        _state.value = _state.value.copy(
            status = RenderJobStatus.CANCELLED,
            statusMessage = if (reason == "user") "Export cancelled" else "Export stopped",
            errorMessage = null,
            isPreparingTransition = false,
            canCancel = false
        )
    }

    private companion object {
        const val TAG = "EXPORT_MANAGER"
        val ACTIVE_STATUSES = setOf(
            RenderJobStatus.QUEUED,
            RenderJobStatus.UPLOADING,
            RenderJobStatus.PROCESSING,
            RenderJobStatus.RENDERING
        )
    }
}
