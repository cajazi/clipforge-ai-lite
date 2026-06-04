package com.clipforge.ai.presentation.text

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TextOverlayUiState(
    val text: String = "",
    val selectedFont: String = "Default",
    val selectedColor: String = "#FFFFFF",
    val fontSize: Float = 24f,
    val isBold: Boolean = false,
    val isItalic: Boolean = false
)

class TextOverlayViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(TextOverlayUiState())
    val uiState: StateFlow<TextOverlayUiState> = _uiState.asStateFlow()

    fun onTextChange(value: String) { _uiState.value = _uiState.value.copy(text = value) }
    fun toggleBold() { _uiState.value = _uiState.value.copy(isBold = !_uiState.value.isBold) }
    fun toggleItalic() { _uiState.value = _uiState.value.copy(isItalic = !_uiState.value.isItalic) }
    fun setColor(hex: String) { _uiState.value = _uiState.value.copy(selectedColor = hex) }
    fun setFontSize(size: Float) { _uiState.value = _uiState.value.copy(fontSize = size) }
    fun addToTimeline() { /* TODO: add TextOverlay to project */ }
}
