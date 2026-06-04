package com.clipforge.ai.presentation.splash

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.core.auth.AuthState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed class SplashDestination { object Home : SplashDestination(); object Login : SplashDestination() }
data class SplashUiState(val progress: Float = 0f, val destination: SplashDestination? = null)

class SplashViewModel(application: Application) : AndroidViewModel(application) {
    private val authManager = (application as ClipForgeApp).authManager
    private val _ui = MutableStateFlow(SplashUiState())
    val uiState: StateFlow<SplashUiState> = _ui.asStateFlow()
    init {
        viewModelScope.launch {
            launch { repeat(40) { i -> delay(40L); _ui.value = _ui.value.copy(progress = (i+1)/40f) } }
            authManager.initialize()
            delay(1_600L)
            val dest = if (authManager.authState.value is AuthState.LoggedIn) {
                Log.d("SplashVM", "Active session → Home"); SplashDestination.Home
            } else {
                Log.d("SplashVM", "No session → Login"); SplashDestination.Login
            }
            _ui.value = _ui.value.copy(destination = dest)
        }
    }
}
