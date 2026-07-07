package com.clipforge.ai.presentation.timeline

import com.clipforge.ai.domain.model.TextOverlay

enum class TextToolPanel {
    Hidden,
    Row,
    Composer
}

data class TextToolUiState(
    val panel: TextToolPanel = TextToolPanel.Hidden,
    val draftText: String = "",
    val draftStartMs: Long = 0L
) {
    val confirmEnabled: Boolean
        get() = draftText.isNotBlank()
}

data class TextToolActionItem(
    val label: String,
    val icon: String,
    val enabled: Boolean
)

val textToolRowActions: List<TextToolActionItem> = listOf(
    TextToolActionItem("Back", "<", enabled = true),
    TextToolActionItem("Add text", "A+", enabled = true),
    TextToolActionItem("Auto Captions", "CC", enabled = false),
    TextToolActionItem("Stickers", "Stk", enabled = false),
    TextToolActionItem("Draw", "Pen", enabled = false),
    TextToolActionItem("Text template", "Tpl", enabled = false),
    TextToolActionItem("Text to audio", "T>A", enabled = false),
    TextToolActionItem("Auto lyrics", "Ly", enabled = false)
)

val textComposerTabs: List<String> = listOf(
    "Templates",
    "Fonts",
    "Styles",
    "Effects",
    "Animations"
)

val textTemplateShellItems: List<String> = listOf(
    "Default",
    "Subscribe",
    "Creative",
    "Pop",
    "Iconic",
    "Glow",
    "Strong",
    "Champion"
)

fun TextToolUiState.openRow(): TextToolUiState =
    copy(panel = TextToolPanel.Row, draftText = "")

fun TextToolUiState.closeTool(): TextToolUiState =
    TextToolUiState()

fun TextToolUiState.openComposer(startMs: Long): TextToolUiState =
    copy(
        panel = TextToolPanel.Composer,
        draftText = "",
        draftStartMs = startMs.coerceAtLeast(0L)
    )

fun TextToolUiState.updateDraftText(text: String): TextToolUiState =
    copy(draftText = text)

fun TextToolUiState.afterCommit(): TextToolUiState =
    copy(panel = TextToolPanel.Row, draftText = "")

fun createDraftTextOverlay(
    projectId: String,
    state: TextToolUiState,
    totalDurationMs: Long,
    zIndex: Int
): TextOverlay? {
    if (state.panel != TextToolPanel.Composer || state.draftText.isBlank()) return null
    return createDefaultTimelineTextOverlay(
        projectId = projectId,
        text = state.draftText,
        timelineStartMs = state.draftStartMs,
        totalDurationMs = totalDurationMs,
        zIndex = zIndex,
        id = TEXT_TOOL_DRAFT_OVERLAY_ID
    )
}

fun createCommittedTextOverlay(
    projectId: String,
    state: TextToolUiState,
    totalDurationMs: Long,
    zIndex: Int
): TextOverlay? {
    if (!state.confirmEnabled) return null
    return planDefaultTimelineTextOverlayCreation(
        projectId = projectId,
        text = state.draftText,
        timelineStartMs = state.draftStartMs,
        totalDurationMs = totalDurationMs,
        zIndex = zIndex
    ).overlay
}

const val TEXT_TOOL_DRAFT_OVERLAY_ID = "text-tool-draft-overlay"
