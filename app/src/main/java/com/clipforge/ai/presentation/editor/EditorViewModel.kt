package com.clipforge.ai.presentation.editor

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EditorUiState(
    val projectTitle: String      = "My Project",
    val isPlaying: Boolean        = false,
    val currentPositionMs: Long   = 0L,
    val totalDurationMs: Long     = 0L,
    val aspectRatioLabel: String  = "9:16",
    val isSaving: Boolean         = false,
    val selectedClipId: String?   = null,
    val activeMode: EditorMode    = EditorMode.NONE,
    val activeAction: EditAction? = null,
    val mainToolbarItems: List<EditorToolbarItem> = emptyList(),
    val secondaryToolbarItems: List<EditorToolbarItem> = emptyList(),
    val tracks: List<EditorTrackState> = emptyList(),
    val lastAppliedAction: AppliedEditAction? = null,
    // First asset URI for thumbnail preview (set after media import)
    val firstAssetUri: String?    = null
)

class EditorViewModel : ViewModel() {
    private val toolbarCatalog = EditorToolbarCatalog()

    private val _uiState = MutableStateFlow(
        EditorUiState(
            mainToolbarItems = toolbarCatalog.mainToolbar(EditorMode.NONE),
            tracks = defaultTracks()
        )
    )
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    fun togglePlayback() {
        _uiState.value = _uiState.value.copy(isPlaying = !_uiState.value.isPlaying)
    }

    /** Called after media import to show first thumbnail in preview */
    fun setFirstAsset(uri: String?, durationMs: Long = 0L) {
        _uiState.value = _uiState.value.copy(
            firstAssetUri   = uri,
            totalDurationMs = durationMs,
            tracks = defaultTracks(uri, selectedClipId = _uiState.value.selectedClipId)
        )
    }

    fun setProjectInfo(title: String, aspectRatio: String) {
        _uiState.value = _uiState.value.copy(
            projectTitle     = title,
            aspectRatioLabel = aspectRatio
        )
    }

    fun selectClip(clipId: String) {
        _uiState.value = _uiState.value.copy(
            selectedClipId = clipId,
            activeMode = EditorMode.NONE,
            activeAction = null,
            mainToolbarItems = toolbarCatalog.mainToolbar(EditorMode.NONE),
            secondaryToolbarItems = emptyList(),
            tracks = defaultTracks(_uiState.value.firstAssetUri, selectedClipId = clipId)
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedClipId = null,
            activeMode = EditorMode.NONE,
            activeAction = null,
            mainToolbarItems = toolbarCatalog.mainToolbar(EditorMode.NONE),
            secondaryToolbarItems = emptyList(),
            tracks = defaultTracks(_uiState.value.firstAssetUri, selectedClipId = null)
        )
    }

    fun selectToolbarItem(itemId: String) {
        val state = _uiState.value
        val item = (state.mainToolbarItems + state.secondaryToolbarItems)
            .firstOrNull { it.id == itemId && it.enabled }
            ?: return

        when {
            item.mode != null -> selectMode(item.mode)
            item.action != null -> chooseAction(item.action)
        }
    }

    fun selectMode(mode: EditorMode) {
        _uiState.value = _uiState.value.copy(
            activeMode = mode,
            activeAction = null,
            mainToolbarItems = toolbarCatalog.mainToolbar(mode),
            secondaryToolbarItems = toolbarCatalog.secondaryToolbar(mode)
        )
    }

    fun chooseAction(action: EditAction) {
        val state = _uiState.value
        if (state.selectedClipId == null) return
        _uiState.value = state.copy(activeAction = action)
    }

    fun applyActiveAction() {
        val state = _uiState.value
        val clipId = state.selectedClipId ?: return
        val action = state.activeAction ?: return
        _uiState.value = state.copy(
            activeMode = EditorMode.NONE,
            activeAction = null,
            mainToolbarItems = toolbarCatalog.mainToolbar(EditorMode.NONE),
            secondaryToolbarItems = emptyList(),
            lastAppliedAction = AppliedEditAction(
                action = action,
                clipId = clipId,
                mode = state.activeMode
            )
        )
    }

    fun returnToTimeline() {
        _uiState.value = _uiState.value.copy(
            activeMode = EditorMode.NONE,
            activeAction = null,
            mainToolbarItems = toolbarCatalog.mainToolbar(EditorMode.NONE),
            secondaryToolbarItems = emptyList()
        )
    }

    private fun defaultTracks(
        thumbnailUri: String? = null,
        selectedClipId: String? = null
    ): List<EditorTrackState> = listOf(
        EditorTrackState(
            type = EditorTrackType.PRIMARY_VIDEO,
            label = "Main video",
            itemCount = 1,
            clips = listOf(
                EditorTimelineClipState(
                    id = "primary_clip_1",
                    label = "Clip 1",
                    durationLabel = "15.5s",
                    thumbnailUri = thumbnailUri,
                    isSelected = selectedClipId == "primary_clip_1"
                )
            )
        ),
        EditorTrackState(EditorTrackType.AUDIO, "Audio Track", addLabel = "+ Add audio"),
        EditorTrackState(EditorTrackType.TEXT, "Text Track", addLabel = "+ Add text"),
        EditorTrackState(EditorTrackType.OVERLAY, "Overlay Track", addLabel = "+ Add overlay"),
        EditorTrackState(EditorTrackType.STICKER, "Sticker Track", addLabel = "+ Add sticker"),
        EditorTrackState(EditorTrackType.EFFECTS, "Effects Track", addLabel = "+ Add effect")
    )
}

private class EditorToolbarCatalog {
    fun mainToolbar(activeMode: EditorMode): List<EditorToolbarItem> = listOf(
        EditorToolbarItem("mode_edit", "Edit", "✂", mode = EditorMode.EDIT),
        EditorToolbarItem("mode_audio", "Audio", "♪", mode = EditorMode.AUDIO),
        EditorToolbarItem("mode_text", "Text", "T", mode = EditorMode.TEXT),
        EditorToolbarItem("mode_effects", "Effects", "✦", mode = EditorMode.EFFECTS),
        EditorToolbarItem("mode_overlay", "Overlay", "▣", mode = EditorMode.OVERLAY),
        EditorToolbarItem("mode_captions", "Captions", "CC", mode = EditorMode.CAPTIONS),
        EditorToolbarItem("mode_filters", "Filters", "◌", mode = EditorMode.FILTERS),
        EditorToolbarItem("mode_adjust", "Adjust", "☼", mode = EditorMode.ADJUST),
        EditorToolbarItem("mode_stickers", "Stickers", "☆", mode = EditorMode.STICKERS),
        EditorToolbarItem("mode_ai_avatar", "AI Avatar", "AI", mode = EditorMode.AI_AVATAR),
        EditorToolbarItem("mode_ai_media", "AI Media", "AI", mode = EditorMode.AI_MEDIA),
        EditorToolbarItem("mode_aspect_ratio", "Aspect Ratio", "□", mode = EditorMode.ASPECT_RATIO),
        EditorToolbarItem("mode_background", "Background", "▥", mode = EditorMode.BACKGROUND)
    ).map { item ->
        item.copy(enabled = activeMode == EditorMode.NONE)
    }

    fun secondaryToolbar(mode: EditorMode): List<EditorToolbarItem> = when (mode) {
        EditorMode.EDIT -> editTools()
        EditorMode.AUDIO -> audioTools()
        EditorMode.FILTERS -> listOf(tool("filters_apply", "Filters", "◌", EditAction.FILTERS))
        EditorMode.ADJUST -> listOf(
            tool("adjust_opacity", "Opacity", "◐", EditAction.OPACITY),
            tool("adjust_transform", "Transform", "⌖", EditAction.TRANSFORM)
        )
        else -> emptyList()
    }

    private fun editTools(): List<EditorToolbarItem> = listOf(
        tool("edit_split", "Split", "✂", EditAction.SPLIT),
        tool("edit_volume", "Volume", "▱", EditAction.VOLUME),
        tool("edit_animation", "Animation", "◌", EditAction.ANIMATION),
        tool("edit_effects", "Effects", "✦", EditAction.FILTERS),
        tool("edit_delete", "Delete", "⌫", EditAction.DELETE),
        tool("edit_speed", "Speed", "1x", EditAction.SPEED),
        tool("edit_beats", "Beats", "♬", EditAction.BEATS),
        tool("edit_crop", "Crop", "⌗", EditAction.CROP),
        tool("edit_duplicate", "Duplicate", "⧉", EditAction.DUPLICATE),
        tool("edit_replace", "Replace", "⇄", EditAction.REPLACE),
        tool("edit_overlay", "Overlay", "▣", EditAction.OVERLAY),
        tool("edit_adjust", "Adjust", "☼", EditAction.ADJUST),
        tool("edit_filters", "Filters", "◌", EditAction.FILTERS),
        tool("edit_retouch", "Retouch", "♙", EditAction.RETOUCH),
        tool("edit_video_quality", "Video Quality", "HD", EditAction.VIDEO_QUALITY),
        tool("edit_remove_bg", "Remove BG", "♟", EditAction.REMOVE_BG),
        tool("edit_ai_remove", "AI Remove", "◇", EditAction.AI_REMOVE, "Try"),
        tool("edit_ai_expand", "AI Expand", "□", EditAction.AI_EXPAND),
        tool("edit_ai_remix", "AI Remix", "AI", EditAction.AI_REMIX),
        tool("edit_eye_contact", "Eye Contact", "◉", EditAction.EYE_CONTACT),
        tool("edit_relight", "Relight", "☀", EditAction.RELIGHT),
        tool("edit_opacity", "Opacity", "◐", EditAction.OPACITY),
        tool("edit_motion_blur", "Motion Blur", "≈", EditAction.MOTION_BLUR),
        tool("edit_lip_sync", "Lip Sync", "☁", EditAction.LIP_SYNC),
        tool("edit_transform", "Transform", "⌖", EditAction.TRANSFORM),
        tool("edit_auto_reframe", "Auto Reframe", "▯", EditAction.AUTO_REFRAME),
        tool("edit_stabilize", "Stabilize", "▰", EditAction.STABILIZE),
        tool("edit_camera_tracking", "Camera Tracking", "⌾", EditAction.CAMERA_TRACKING),
        tool("edit_extract_audio", "Extract Audio", "♪", EditAction.EXTRACT_AUDIO),
        tool("edit_isolate_voice", "Isolate Voice", "▱", EditAction.ISOLATE_VOICE),
        tool("edit_reduce_noise", "Reduce Noise", "≋", EditAction.REDUCE_NOISE),
        tool("edit_audio_effects", "Audio Effects", "Fx", EditAction.AUDIO_EFFECTS),
        tool("edit_enhance_voice", "Enhance Voice", "▴", EditAction.ENHANCE_VOICE),
        tool("edit_video_translator", "Video Translator", "文", EditAction.VIDEO_TRANSLATOR),
        tool("edit_freeze", "Freeze", "▮", EditAction.FREEZE),
        tool("edit_reverse", "Reverse", "↶", EditAction.REVERSE),
        tool("edit_mask", "Mask", "◒", EditAction.MASK),
        tool("edit_unlink", "Unlink", "⌁", EditAction.UNLINK)
    )

    private fun audioTools(): List<EditorToolbarItem> = listOf(
        tool("audio_volume", "Volume", "▱", EditAction.VOLUME),
        tool("audio_beats", "Beats", "♬", EditAction.BEATS),
        tool("audio_extract", "Extract Audio", "♪", EditAction.EXTRACT_AUDIO),
        tool("audio_isolate", "Isolate Voice", "▱", EditAction.ISOLATE_VOICE),
        tool("audio_reduce_noise", "Reduce Noise", "≋", EditAction.REDUCE_NOISE),
        tool("audio_effects", "Audio Effects", "Fx", EditAction.AUDIO_EFFECTS),
        tool("audio_enhance_voice", "Enhance Voice", "▴", EditAction.ENHANCE_VOICE)
    )

    private fun tool(
        id: String,
        label: String,
        icon: String,
        action: EditAction,
        badge: String? = null
    ): EditorToolbarItem = EditorToolbarItem(
        id = id,
        label = label,
        icon = icon,
        action = action,
        badge = badge
    )
}
