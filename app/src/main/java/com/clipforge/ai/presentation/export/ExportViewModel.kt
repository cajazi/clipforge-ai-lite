package com.clipforge.ai.presentation.export

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.core.network.NetworkMonitor
import com.clipforge.ai.domain.model.RenderJobStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ExportUiState(
    val status: RenderJobStatus = RenderJobStatus.QUEUED,
    val progressPercent: Int = 0,
    val statusMessage: String = "Preparing export...",
    val outputUrl: String? = null,
    val publicUri: String? = null,
    val isGalleryVisible: Boolean = false,
    val errorMessage: String? = null,
    val quality: String = "720p",
    val hasWatermark: Boolean = true,
    val isOffline: Boolean = false,
    val jobId: String? = null,
    val isPreparingTransition: Boolean = false,
    val canCancel: Boolean = false
)

class ExportViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as ClipForgeApp
    private val exportManager = app.exportManager
    private val networkMonitor = NetworkMonitor(application)

    private val _uiState = MutableStateFlow(ExportUiState())
    val uiState: StateFlow<ExportUiState> = _uiState.asStateFlow()

    init {
        observeExport()
        observeNetwork()
    }

    private fun observeExport() {
        viewModelScope.launch {
            exportManager.state.collect { state ->
                _uiState.value = _uiState.value.copy(
                    status = state.status,
                    progressPercent = state.progressPercent,
                    statusMessage = state.statusMessage,
                    outputUrl = state.outputUrl,
                    publicUri = state.publicUri,
                    isGalleryVisible = state.isGalleryVisible,
                    errorMessage = state.errorMessage,
                    quality = state.quality,
                    hasWatermark = state.hasWatermark,
                    jobId = state.projectId,
                    isPreparingTransition = state.isPreparingTransition,
                    canCancel = state.canCancel
                )
            }
        }
    }

    private fun observeNetwork() {
        viewModelScope.launch {
            networkMonitor.isOnline.collect { online ->
                _uiState.value = _uiState.value.copy(isOffline = !online)
            }
        }
    }

    fun startExport(projectId: String) {
        Log.d("EXPORT", "VIEWMODEL_START_DELEGATE project=$projectId")
        exportManager.startExport(projectId)
    }

    fun cancelExport(projectId: String) {
        Log.d("EXPORT", "VIEWMODEL_CANCEL_DELEGATE project=$projectId")
        exportManager.cancelExport(projectId)
    }
}
