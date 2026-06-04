package com.clipforge.ai.presentation.music

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class MusicTrack(
    val id: String,
    val title: String,
    val artist: String,
    val durationMs: Long,
    val isPremium: Boolean = false
)

data class MusicUiState(
    val tracks: List<MusicTrack> = defaultTracks(),
    val selectedTrackId: String? = null,
    val isPlaying: Boolean = false,
    val isPro: Boolean = false
)

fun defaultTracks() = listOf(
    MusicTrack("1", "Upbeat Energy",    "ClipForge Audio", 120_000),
    MusicTrack("2", "Chill Vibes",      "ClipForge Audio", 95_000),
    MusicTrack("3", "Epic Cinematic",   "ClipForge Audio", 180_000),
    MusicTrack("4", "Happy Bounce",     "ClipForge Audio", 88_000),
    MusicTrack("5", "Dark Tension",     "ClipForge Audio", 145_000, isPremium = true),
    MusicTrack("6", "Retro Synth",      "ClipForge Audio", 110_000, isPremium = true),
    MusicTrack("7", "Acoustic Morning", "ClipForge Audio", 132_000, isPremium = true)
)

class MusicViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(MusicUiState())
    val uiState: StateFlow<MusicUiState> = _uiState.asStateFlow()

    fun selectTrack(id: String) {
        val track = _uiState.value.tracks.find { it.id == id } ?: return
        if (track.isPremium && !_uiState.value.isPro) return
        _uiState.value = _uiState.value.copy(
            selectedTrackId = if (_uiState.value.selectedTrackId == id) null else id,
            isPlaying = false
        )
    }

    fun togglePlay(id: String) {
        _uiState.value = _uiState.value.copy(
            isPlaying = !(_uiState.value.isPlaying && _uiState.value.selectedTrackId == id),
            selectedTrackId = id
        )
    }

    fun addToProject() { /* TODO: add selected music track to timeline */ }
}
