package com.clipforge.ai.presentation.timeline

import com.clipforge.ai.domain.model.TextOverlay
import java.util.UUID

enum class TextToolPanel {
    Hidden,
    Row,
    Composer
}

data class TextToolUiState(
    val panel: TextToolPanel = TextToolPanel.Hidden,
    val draftText: String = "",
    val draftStartMs: Long = 0L,
    val draftOverlayId: String? = null
) {
    val confirmEnabled: Boolean
        get() = draftText.isNotBlank() && draftOverlayId != null
}

data class TextToolActionItem(
    val label: String,
    val icon: String,
    val enabled: Boolean,
    val comingSoon: Boolean = false
)

val textToolRowActions: List<TextToolActionItem> = listOf(
    TextToolActionItem("Back", "<", enabled = true),
    TextToolActionItem("Add text", "A+", enabled = true),
    TextToolActionItem("Auto Captions", "CC", enabled = true, comingSoon = true),
    TextToolActionItem("Stickers", "Stk", enabled = true, comingSoon = true),
    TextToolActionItem("Draw", "Pen", enabled = true, comingSoon = true),
    TextToolActionItem("Text template", "Tpl", enabled = true, comingSoon = true),
    TextToolActionItem("Text to audio", "T>A", enabled = true, comingSoon = true),
    TextToolActionItem("Auto lyrics", "Ly", enabled = true, comingSoon = true)
)

val comingSoonTextToolLabels: List<String> =
    textToolRowActions.filter { it.comingSoon }.map { it.label }

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
    copy(panel = TextToolPanel.Row, draftText = "", draftOverlayId = null)

fun TextToolUiState.closeTool(): TextToolUiState =
    TextToolUiState()

fun TextToolUiState.openComposer(
    startMs: Long,
    overlayId: String = UUID.randomUUID().toString()
): TextToolUiState =
    copy(
        panel = TextToolPanel.Composer,
        draftText = "",
        draftStartMs = startMs.coerceAtLeast(0L),
        draftOverlayId = overlayId
    )

fun TextToolUiState.updateDraftText(text: String): TextToolUiState =
    copy(draftText = text)

fun TextToolUiState.afterCommit(): TextToolUiState =
    copy(panel = TextToolPanel.Row, draftText = "", draftOverlayId = null)

fun createDraftTextOverlay(
    projectId: String,
    state: TextToolUiState,
    totalDurationMs: Long,
    zIndex: Int
): TextOverlay? {
    if (state.panel != TextToolPanel.Composer || state.draftText.isBlank()) return null
    val overlayId = state.draftOverlayId ?: return null
    return createDefaultTimelineTextOverlay(
        projectId = projectId,
        text = state.draftText,
        timelineStartMs = state.draftStartMs,
        totalDurationMs = totalDurationMs,
        zIndex = zIndex,
        id = overlayId
    )
}

fun createCommittedTextOverlay(
    projectId: String,
    state: TextToolUiState,
    totalDurationMs: Long,
    zIndex: Int
): TextOverlay? {
    if (!state.confirmEnabled) return null
    val overlayId = state.draftOverlayId ?: return null
    return planDefaultTimelineTextOverlayCreation(
        projectId = projectId,
        text = state.draftText,
        timelineStartMs = state.draftStartMs,
        totalDurationMs = totalDurationMs,
        zIndex = zIndex,
        id = overlayId
    ).overlay
}
