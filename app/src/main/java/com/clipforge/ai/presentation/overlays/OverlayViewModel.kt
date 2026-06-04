package com.clipforge.ai.presentation.overlays

import androidx.lifecycle.ViewModel
import com.clipforge.ai.domain.model.OverlayType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class OverlayUiState(
    val selectedTab: OverlayType = OverlayType.IMAGE,
    val isPro: Boolean = false
)

class OverlayViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(OverlayUiState())
    val uiState: StateFlow<OverlayUiState> = _uiState.asStateFlow()

    fun selectTab(type: OverlayType) {
        _uiState.value = _uiState.value.copy(selectedTab = type)
    }
}
