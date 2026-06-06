package com.clipforge.ai.presentation.export

import android.app.Application
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.clipforge.ai.core.gl.CrossfadeExecutor
import com.clipforge.ai.core.gl.ProjectExporter
import com.clipforge.ai.core.network.NetworkMonitor
import com.clipforge.ai.core.storage.UserPreferencesManager
import com.clipforge.ai.domain.model.RenderJobStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

data class ExportUiState(
    val status: RenderJobStatus = RenderJobStatus.QUEUED,
    val progressPercent: Int = 0,
    val statusMessage: String = "Preparing export...",
    val outputUrl: String? = null,
    val errorMessage: String? = null,
    val quality: String = "720p",
    val hasWatermark: Boolean = true,
    val isOffline: Boolean = false,
    val jobId: String? = null
)

class ExportViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        // Guards against duplicate concurrent exports of the same project, even if
        // more than one ExportViewModel instance is created (e.g. screen recreated).
        private val activeExports = java.util.Collections.synchronizedSet(mutableSetOf<String>())
    }

    private val prefsManager   = UserPreferencesManager(application)
    private val networkMonitor = NetworkMonitor(application)

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        observeNetwork()
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
            }
        }
    }

    /** Called by the screen with the real projectId. Runs a real on-device export
     *  through the crossfade-aware timeline renderer (handles plain clips AND dissolves). */
    @OptIn(UnstableApi::class)
    fun startExport(projectId: String) {
        Log.d("EXPORT", "startExport project=$projectId active=${activeExports.contains(projectId)}")
        if (!activeExports.add(projectId)) {
            Log.d("EXPORT", "duplicate export ignored for $projectId")
            return
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                status = RenderJobStatus.PROCESSING,
                statusMessage = "Preparing clips...",
                progressPercent = 0
            )

            val ctx = getApplication<Application>()
            val paths = ProjectExporter.resolveAllVideoPaths(ctx, projectId)

            if (paths.isEmpty()) {
                activeExports.remove(projectId)
                _uiState.value = _uiState.value.copy(
                    status = RenderJobStatus.FAILED,
                    statusMessage = "No video clip found to export",
                    errorMessage = "This project has no video asset."
                )
                return@launch
            }

            // Preparing message during the (currently slow) crossfade frame pre-extraction,
            // so the UI shows activity instead of a frozen 0%.
            _uiState.value = _uiState.value.copy(
                status = RenderJobStatus.RENDERING,
                statusMessage = "Preparing transitions...",
                progressPercent = 0
            )

            CrossfadeExecutor.renderProjectTimeline(
                context = ctx,
                projectId = projectId,
                onProgress = { pct ->
                    _uiState.value = _uiState.value.copy(
                        progressPercent = pct,
                        statusMessage = "Rendering... $pct%"
                    )
                },
                onResult = { result ->
                    when (result) {
                        is CrossfadeExecutor.Result.Done -> {
                            activeExports.remove(projectId)
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            viewModelScope.launch { prefsManager.incrementDailyExport(today) }
                            _uiState.value = _uiState.value.copy(
                                status = RenderJobStatus.COMPLETED,
                                statusMessage = "Export complete!",
                                progressPercent = 100,
                                outputUrl = result.outputPath
                            )
                        }
                        is CrossfadeExecutor.Result.Error -> {
                            activeExports.remove(projectId)
                            Log.e("EXPORT", "Export failed: ${result.message}")
                            _uiState.value = _uiState.value.copy(
                                status = RenderJobStatus.FAILED,
                                statusMessage = "Export failed",
                                errorMessage = "Something went wrong while rendering. Please try again."
                            )
                        }
                    }
                }
            )
        }
    }

    fun cancelExport() {
        _uiState.value = _uiState.value.copy(status = RenderJobStatus.CANCELLED, statusMessage = "Export cancelled")
    }
}
