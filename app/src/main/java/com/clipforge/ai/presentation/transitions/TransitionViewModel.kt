package com.clipforge.ai.presentation.transitions

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.data.repository.TransitionRepositoryImpl
import com.clipforge.ai.domain.model.Transition
import com.clipforge.ai.domain.model.TransitionType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val TAG = "TransitionPickerVM"

data class TransitionUiState(
    val selectedType: TransitionType = TransitionType.NONE,
    val isPro: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val applySucceeded: Boolean = false
)

class TransitionViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as ClipForgeApp
    private val repository = TransitionRepositoryImpl(
        app.database.timelineDao(),
        app.authManager.sessionManager
    )

    private val _uiState = MutableStateFlow(TransitionUiState())
    val uiState: StateFlow<TransitionUiState> = _uiState.asStateFlow()

    fun selectTransition(type: TransitionType) {
        if (type.isPremium && !_uiState.value.isPro) {
            _uiState.update { it.copy(errorMessage = "This transition requires Pro.") }
            return
        }
        _uiState.update { it.copy(selectedType = type, errorMessage = null) }
    }

    fun applyToClip(projectId: String, clipId: String) {
        Log.d(TAG, "Saving transition ${_uiState.value.selectedType.name} for clipId=$clipId projectId=$projectId")
        save {
            repository.saveTransition(projectId, clipId, selectedTransition())
        }
    }

    fun applyToAll(projectId: String) {
        Log.d(TAG, "Saving transition ${_uiState.value.selectedType.name} for all clips projectId=$projectId")
        save {
            repository.saveAllTransitions(projectId, selectedTransition())
        }
    }

    private fun save(action: suspend () -> NetworkResult<Unit>) {
        if (_uiState.value.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true, errorMessage = null, applySucceeded = false) }
            val result = runCatching { action() }.getOrElse { throwable ->
                NetworkResult.Error(message = throwable.message ?: "Failed to save transition.")
            }
            when (result) {
                is NetworkResult.Success -> {
                    Log.d(TAG, "Transition saved")
                    _uiState.update { it.copy(isSaving = false, applySucceeded = true) }
                }
                is NetworkResult.Error -> {
                    Log.d(TAG, "Transition save failed: ${result.message}")
                    _uiState.update {
                        it.copy(
                            isSaving = false,
                            errorMessage = result.message ?: "Failed to save transition."
                        )
                    }
                }
                NetworkResult.Loading -> {
                    _uiState.update { it.copy(isSaving = true) }
                }
            }
        }
    }

    private fun selectedTransition(): Transition {
        val type = _uiState.value.selectedType
        return Transition(type = type, durationMs = 500L, isPremium = type.isPremium)
    }
}
