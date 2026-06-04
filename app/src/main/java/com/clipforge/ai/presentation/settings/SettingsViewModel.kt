package com.clipforge.ai.presentation.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.core.storage.UserPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isPro: Boolean = false,
    val watermarkEnabled: Boolean = true,
    val defaultQuality: String = "720p",
    val notificationsEnabled: Boolean = true,
    val dailyExportCount: Int = 0,
    val appVersion: String = "1.0.0",
    val isLoggingOut: Boolean = false
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val authManager = (application as ClipForgeApp).authManager
    private val prefsManager = UserPreferencesManager(application)
    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState: StateFlow<SettingsUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefsManager.userPrefs.collect { prefs ->
                _uiState.value = _uiState.value.copy(
                    isPro                = prefs.isPro,
                    watermarkEnabled     = !prefs.isPro,
                    defaultQuality       = prefs.defaultQuality,
                    notificationsEnabled = prefs.notificationsEnabled,
                    dailyExportCount     = prefs.dailyExportCount
                )
            }
        }
    }

    fun toggleNotifications(enabled: Boolean) {
        viewModelScope.launch {
            prefsManager.setNotificationsEnabled(enabled)
        }
    }

    fun setDefaultQuality(quality: String) {
        viewModelScope.launch {
            prefsManager.setDefaultQuality(quality)
        }
    }

    fun logout(onLoggedOut: () -> Unit) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoggingOut = true)
            authManager.logout()
            _uiState.value = _uiState.value.copy(isLoggingOut = false)
            onLoggedOut()
        }
    }
}
