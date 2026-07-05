package com.clipforge.ai.presentation.auth

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.core.auth.RegisterState
import com.clipforge.ai.core.network.NetworkResult
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class RegisterUiState(
    val name: String               = "",
    val email: String              = "",
    val password: String           = "",
    val confirmPassword: String    = "",
    val nameError: String?         = null,
    val emailError: String?        = null,
    val passwordError: String?     = null,
    val confirmError: String?      = null,
    val isLoading: Boolean         = false,
    val error: String?             = null,
    val isSuccess: Boolean         = false,
    val needsConfirmation: Boolean = false
)

class RegisterViewModel(application: Application) : AndroidViewModel(application) {
    private val authManager = (application as ClipForgeApp).authManager
    private val _ui = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _ui.asStateFlow()

    fun onNameChange(v: String)            { _ui.value = _ui.value.copy(name            = v.take(50), nameError    = null, error = null) }
    fun onEmailChange(v: String)           { _ui.value = _ui.value.copy(email           = v,          emailError   = null, error = null) }
    fun onPasswordChange(v: String)        { _ui.value = _ui.value.copy(password        = v,          passwordError = null, error = null) }
    fun onConfirmPasswordChange(v: String) { _ui.value = _ui.value.copy(confirmPassword = v,          confirmError  = null, error = null) }

    fun register() {
        val s = _ui.value; var ok = true
        if (s.name.isBlank()) { _ui.value = _ui.value.copy(nameError = "Name required"); ok = false }
        if (s.email.isBlank() || !android.util.Patterns.EMAIL_ADDRESS.matcher(s.email).matches()) { _ui.value = _ui.value.copy(emailError = "Enter a valid email"); ok = false }
        if (s.password.length < 8) { _ui.value = _ui.value.copy(passwordError = "At least 8 characters"); ok = false }
        if (s.password != s.confirmPassword) { _ui.value = _ui.value.copy(confirmError = "Passwords do not match"); ok = false }
        if (!ok) return
        viewModelScope.launch {
            _ui.value = s.copy(isLoading = true, error = null)
            when (val r = authManager.registerWithEmail(s.name.trim(), s.email.trim(), s.password)) {
                is RegisterState.Success              -> _ui.value = _ui.value.copy(isLoading = false, isSuccess = true)
                is RegisterState.NeedsEmailConfirmation -> _ui.value = _ui.value.copy(isLoading = false, needsConfirmation = true)
                is RegisterState.Error                -> _ui.value = _ui.value.copy(isLoading = false, error = r.message)
            }
        }
    }

    fun onGoogleSignInStarted() {
        _ui.value = _ui.value.copy(isLoading = true, error = null)
    }

    fun onGoogleSignInCancelled() {
        _ui.value = _ui.value.copy(
            isLoading = false,
            error = "Google account selection cancelled."
        )
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
