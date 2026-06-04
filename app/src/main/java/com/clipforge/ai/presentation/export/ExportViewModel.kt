package com.clipforge.ai.presentation.export

import android.app.Application
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
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

    private val prefsManager   = UserPreferencesManager(application)
    private val networkMonitor = NetworkMonitor(application)

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    private var started = false

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

    /** Called once by the screen with the real projectId. Runs a real on-device export. */
    @OptIn(UnstableApi::class)
    fun startExport(projectId: String) {
        if (started) return
        started = true

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(
                status = RenderJobStatus.PROCESSING,
                statusMessage = "Preparing clips...",
                progressPercent = 0
            )

            val ctx = getApplication<Application>()
            val paths = ProjectExporter.resolveAllVideoPaths(ctx, projectId)

            if (paths.isEmpty()) {
                _uiState.value = _uiState.value.copy(
                    status = RenderJobStatus.FAILED,
                    statusMessage = "No video clip found to export",
                    errorMessage = "This project has no video asset."
                )
                return@launch
            }

            _uiState.value = _uiState.value.copy(
                status = RenderJobStatus.RENDERING,
                statusMessage = "Rendering video...",
                progressPercent = 0
            )

            ProjectExporter.exportProject(
                context = ctx,
                paths = paths,
                onProgress = { pct ->
                    _uiState.value = _uiState.value.copy(
                        progressPercent = pct,
                        statusMessage = "Rendering... $pct%"
                    )
                },
                onResult = { result ->
                    when (result) {
                        is ProjectExporter.Result.Done -> {
                            val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
                            viewModelScope.launch { prefsManager.incrementDailyExport(today) }
                            _uiState.value = _uiState.value.copy(
                                status = RenderJobStatus.COMPLETED,
                                statusMessage = "Export complete!",
                                progressPercent = 100,
                                outputUrl = result.outputPath
                            )
                        }
                        is ProjectExporter.Result.Error -> {
                            _uiState.value = _uiState.value.copy(
                                status = RenderJobStatus.FAILED,
                                statusMessage = "Export failed",
                                errorMessage = result.message
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
