package com.clipforge.ai.domain.model
data class Overlay(
    val id: String, val projectId: String, val type: OverlayType,
    val mediaAssetId: String? = null, val text: String? = null,
    val positionX: Float = 0.5f, val positionY: Float = 0.5f,
    val scaleX: Float = 1.0f, val scaleY: Float = 1.0f,
    val rotation: Float = 0.0f, val startMs: Long = 0L, val endMs: Long = 0L,
    val opacity: Float = 1.0f, val fontStyle: TextFontStyle? = null
)
enum class OverlayType { IMAGE, VIDEO, TEXT, LOGO }
data class TextFontStyle(
    val fontName: String = "Default", val fontSize: Float = 24f,
    val colorHex: String = "#FFFFFF", val isBold: Boolean = false, val isItalic: Boolean = false
)
