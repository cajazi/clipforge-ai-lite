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

data class LoginUiState(
    val email: String          = "",
    val password: String       = "",
    val emailError: String?    = null,
    val passwordError: String? = null,
    val isLoading: Boolean     = false,
    val error: String?         = null,
    val isSuccess: Boolean     = false
)

class LoginViewModel(application: Application) : AndroidViewModel(application) {
    private val authManager = (application as ClipForgeApp).authManager
    private val _ui = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _ui.asStateFlow()

    fun onEmailChange(v: String)    { _ui.value = _ui.value.copy(email    = v, emailError    = null, error = null) }
    fun onPasswordChange(v: String) { _ui.value = _ui.value.copy(password = v, passwordError = null, error = null) }

    fun login() {
        val s = _ui.value
        var ok = true
        if (s.email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(s.email).matches()) {
            _ui.value = _ui.value.copy(emailError = "Enter a valid email"); ok = false }
        if (s.password.isBlank()) {
            _ui.value = _ui.value.copy(passwordError = "Enter your password"); ok = false }
        if (!ok) return
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true, error = null)
            when (val r = authManager.loginWithEmail(s.email.trim(), s.password)) {
                is NetworkResult.Success -> _ui.value = _ui.value.copy(isLoading = false, isSuccess = true)
                is NetworkResult.Error   -> _ui.value = _ui.value.copy(isLoading = false, error = r.message ?: "Login failed")
                else -> _ui.value = _ui.value.copy(isLoading = false)
            }
        }
    }

    fun onGoogleSignInStarted() {
        _ui.value = _ui.value.copy(isLoading = true, error = null)
    }

    fun onGoogleSignInCancelled() {
        _ui.value = _ui.value.copy(isLoading = false)
    }

    fun onGoogleSignInFailed(message: String) {
        _ui.value = _ui.value.copy(isLoading = false, error = message)
    }

    fun loginWithGoogleIdToken(idToken: String, nonce: String?) {
        viewModelScope.launch {
            _ui.value = _ui.value.copy(isLoading = true, error = null)
            when (val r = authManager.loginWithGoogleIdToken(idToken, nonce)) {
                is NetworkResult.Success -> _ui.value = _ui.value.copy(isLoading = false, isSuccess = true)
                is NetworkResult.Error -> _ui.value = _ui.value.copy(
                    isLoading = false,
                    error = r.message ?: "Google sign-in failed"
                )
                else -> _ui.value = _ui.value.copy(isLoading = false)
            }
        }
    }
}
