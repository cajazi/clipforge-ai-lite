package com.clipforge.ai.presentation.preview

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PreviewUiState(
    val isPlaying: Boolean = false,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val isMuted: Boolean = false
)

class PreviewViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(PreviewUiState())
    val uiState: StateFlow<PreviewUiState> = _uiState.asStateFlow()

    fun togglePlay() { _uiState.value = _uiState.value.copy(isPlaying = !_uiState.value.isPlaying) }
    fun toggleMute() { _uiState.value = _uiState.value.copy(isMuted = !_uiState.value.isMuted) }
    fun seekTo(ms: Long) { _uiState.value = _uiState.value.copy(positionMs = ms) }
}
