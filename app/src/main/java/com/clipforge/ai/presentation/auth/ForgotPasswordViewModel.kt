package com.clipforge.ai.presentation.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.core.network.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ForgotPasswordUiState(
    val email: String       = "",
    val emailError: String? = null,
    val isLoading: Boolean  = false,
    val isSent: Boolean     = false,
    val error: String?      = null
)

class ForgotPasswordViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = (application as ClipForgeApp).authManager
    private val _uiState = MutableStateFlow(ForgotPasswordUiState())
    val uiState: StateFlow<ForgotPasswordUiState> = _uiState.asStateFlow()

    fun onEmailChange(v: String) { _uiState.value = _uiState.value.copy(email = v, emailError = null, error = null) }

    fun sendReset() {
        val state = _uiState.value
        if (state.email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(state.email).matches()) {
            _uiState.value = state.copy(emailError = "Enter a valid email"); return
        }
        viewModelScope.launch {
            _uiState.value = state.copy(isLoading = true)
            when (val r = authManager.authRepo.sendPasswordReset(state.email.trim())) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(isLoading = false, isSent = true)
                is NetworkResult.Error   -> _uiState.value = _uiState.value.copy(isLoading = false, error = r.message ?: "Failed to send reset email")
                else -> _uiState.value = _uiState.value.copy(isLoading = false)
            }
        }
    }
}
