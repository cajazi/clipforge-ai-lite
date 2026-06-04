package com.clipforge.ai.presentation.project

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipforge.ai.data.local.database.ClipForgeDatabase
import com.clipforge.ai.data.repository.SupabaseProjectRepository
import com.clipforge.ai.core.supabase.SupabaseClient
import com.clipforge.ai.core.supabase.SupabaseProjectApi
import com.clipforge.ai.core.billing.PlanType
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.core.storage.UserPreferencesManager
import com.clipforge.ai.domain.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

enum class DurationOption(val label: String, val seconds: Int?) {
    AUTO("Auto", null),
    SEC_15("15 sec", 15),
    SEC_30("30 sec", 30),
    SEC_60("60 sec", 60),
    SEC_90("90 sec", 90),
    CUSTOM("Custom", null)
}

data class ProjectUiState(
    // Basic fields
    val title: String                   = "",
    val selectedRatio: AspectRatio      = AspectRatio.RATIO_9_16,
    val selectedDuration: DurationOption = DurationOption.AUTO,
    val customDurationSec: String       = "",
    val selectedQuality: ExportQuality  = ExportQuality.QUALITY_720P,
    val projectType: ProjectType        = ProjectType.MANUAL,

    // Auto Edit settings
    val autoFinalDuration: Int          = 30,
    val autoSecondsPerClip: Int         = 3,
    val autoTransition: TransitionType  = TransitionType.FADE,
    val autoMusicEnabled: Boolean       = false,
    val autoSecondsPerClipText: String  = "3",

    // Validation
    val titleError: String?             = null,
    val customDurationError: String?    = null,
    val autoSecondsError: String?       = null,

    // UX state
    val isCreating: Boolean             = false,
    val createdProjectId: String?       = null,
    val error: String?                  = null,
    val isPro: Boolean                  = false
)

class ProjectViewModel(application: Application) : AndroidViewModel(application) {

    private val db    = ClipForgeDatabase.getInstance(application)
    private val api   = SupabaseClient.create<SupabaseProjectApi>()
    private val repo  = SupabaseProjectRepository(db.projectDao(), api)
    private val prefs = UserPreferencesManager(application)

    private val _uiState = MutableStateFlow(ProjectUiState())
    val uiState: StateFlow<ProjectUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            prefs.userPrefs.collect { p ->
                _uiState.value = _uiState.value.copy(
                    isPro = p.isPro,
                    selectedQuality = if (p.isPro) ExportQuality.QUALITY_1080P else ExportQuality.QUALITY_720P
                )
            }
        }
    }

    // ── Field updaters ────────────────────────────────────────────────────

    fun onTitleChange(v: String) {
        if (v.length > 50) return
        _uiState.value = _uiState.value.copy(
            title = v,
            titleError = when {
                v.isBlank()  -> "Project name is required"
                v.length < 2 -> "Name must be at least 2 characters"
                else         -> null
            }
        )
    }

    fun onRatioSelected(r: AspectRatio) = run { _uiState.value = _uiState.value.copy(selectedRatio = r) }

    fun onDurationSelected(d: DurationOption) {
        _uiState.value = _uiState.value.copy(selectedDuration = d, customDurationError = null)
    }

    fun onCustomDurationChange(v: String) {
        val filtered = v.filter { it.isDigit() }.take(4)
        _uiState.value = _uiState.value.copy(
            customDurationSec = filtered,
            customDurationError = when {
                filtered.isBlank()                          -> "Enter a duration"
                (filtered.toIntOrNull() ?: 0) < 5          -> "Minimum 5 seconds"
                (filtered.toIntOrNull() ?: 0) > 600        -> "Maximum 600 seconds"
                else                                        -> null
            }
        )
    }

    fun onQualitySelected(q: ExportQuality) {
        if (q == ExportQuality.QUALITY_1080P && !_uiState.value.isPro) return
        _uiState.value = _uiState.value.copy(selectedQuality = q)
    }

    fun onProjectTypeSelected(t: ProjectType) {
        _uiState.value = _uiState.value.copy(projectType = t)
    }

    // ── Auto Edit settings ────────────────────────────────────────────────

    fun onAutoFinalDurationChange(seconds: Int) {
        _uiState.value = _uiState.value.copy(autoFinalDuration = seconds.coerceIn(5, 600))
    }

    fun onAutoSecondsPerClipChange(v: String) {
        val filtered = v.filter { it.isDigit() }.take(3)
        val secs = filtered.toIntOrNull() ?: 0
        val dur  = _uiState.value.autoFinalDuration
        _uiState.value = _uiState.value.copy(
            autoSecondsPerClipText = filtered,
            autoSecondsPerClip     = secs,
            autoSecondsError       = when {
                filtered.isBlank() -> "Required"
                secs <= 0          -> "Must be greater than 0"
                secs > dur         -> "Cannot exceed final duration (${dur}s)"
                else               -> null
            }
        )
    }

    fun onAutoTransitionChange(t: TransitionType) {
        _uiState.value = _uiState.value.copy(autoTransition = t)
    }

    fun onAutoMusicToggle(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(autoMusicEnabled = enabled)
    }

    // ── Create ────────────────────────────────────────────────────────────

    fun createProject() {
        val state = _uiState.value

        if (state.title.isBlank()) {
            _uiState.value = state.copy(titleError = "Project name is required"); return
        }
        if (state.selectedDuration == DurationOption.CUSTOM) {
            val secs = state.customDurationSec.toIntOrNull()
            if (secs == null || secs < 5) {
                _uiState.value = state.copy(customDurationError = "Enter a valid duration (min 5s)"); return
            }
        }
        if (state.projectType == ProjectType.AUTO_EDIT) {
            val autoSettings = buildAutoSettings(state)
            val err = autoSettings.validate()
            if (err != null) { _uiState.value = state.copy(autoSecondsError = err); return }
        }

        viewModelScope.launch {
            _uiState.value = state.copy(isCreating = true, error = null)

            val autoSettings = if (state.projectType == ProjectType.AUTO_EDIT)
                buildAutoSettings(state) else null

            val project = Project(
                id               = UUID.randomUUID().toString(),
                title            = state.title.trim(),
                aspectRatio      = state.selectedRatio,
                exportQuality    = state.selectedQuality,
                planType         = if (state.isPro) PlanType.PRO else PlanType.FREE,
                projectType      = state.projectType,
                autoEditSettings = autoSettings,
                createdAt        = System.currentTimeMillis(),
                updatedAt        = System.currentTimeMillis()
            )

            when (val result = repo.createProject(project)) {
                is NetworkResult.Success -> _uiState.value = _uiState.value.copy(
                    isCreating = false, createdProjectId = result.data.id
                )
                is NetworkResult.Error   -> _uiState.value = _uiState.value.copy(
                    isCreating = false, error = result.message ?: "Failed to create project"
                )
                else -> {}
            }
        }
    }

    private fun buildAutoSettings(state: ProjectUiState) = AutoEditSettings(
        finalDurationSeconds = state.autoFinalDuration,
        secondsPerClip       = state.autoSecondsPerClip.coerceAtLeast(1),
        transitionType       = state.autoTransition,
        musicEnabled         = state.autoMusicEnabled
    )
}
