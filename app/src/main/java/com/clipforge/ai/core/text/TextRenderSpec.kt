package com.clipforge.ai.core.text

data class TextRenderSpec(
    val text: String,
    val fontId: String,
    val fontSizeNorm: Float,
    val colorArgb: Int,
    val bgColorArgb: Int?,
    val bold: Boolean,
    val italic: Boolean,
    val alignment: TextAlignment,
    val highlightRange: IntRange? = null
)

enum class TextAlignment {
    LEFT,
    CENTER,
    RIGHT
}
