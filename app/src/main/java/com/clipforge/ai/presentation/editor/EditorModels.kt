package com.clipforge.ai.presentation.editor

enum class EditorMode {
    NONE,
    EDIT,
    AUDIO,
    TEXT,
    EFFECTS,
    OVERLAY,
    CAPTIONS,
    FILTERS,
    ADJUST,
    STICKERS,
    AI_AVATAR,
    AI_MEDIA,
    ASPECT_RATIO,
    BACKGROUND
}

enum class EditAction {
    SPLIT,
    VOLUME,
    ANIMATION,
    DELETE,
    SPEED,
    CROP,
    DUPLICATE,
    REPLACE,
    FILTERS,
    OPACITY,
    TRANSFORM,
    FREEZE,
    REVERSE,
    MASK,
    BEATS,
    OVERLAY,
    ADJUST,
    RETOUCH,
    VIDEO_QUALITY,
    REMOVE_BG,
    AI_REMOVE,
    AI_EXPAND,
    AI_REMIX,
    EYE_CONTACT,
    RELIGHT,
    MOTION_BLUR,
    LIP_SYNC,
    AUTO_REFRAME,
    STABILIZE,
    CAMERA_TRACKING,
    EXTRACT_AUDIO,
    ISOLATE_VOICE,
    REDUCE_NOISE,
    AUDIO_EFFECTS,
    ENHANCE_VOICE,
    VIDEO_TRANSLATOR,
    UNLINK
}

enum class EditorTrackType {
    PRIMARY_VIDEO,
    AUDIO,
    TEXT,
    OVERLAY,
    STICKER,
    EFFECTS
}

data class EditorToolbarItem(
    val id: String,
    val label: String,
    val icon: String,
    val mode: EditorMode? = null,
    val action: EditAction? = null,
    val badge: String? = null,
    val enabled: Boolean = true
)

data class EditorTimelineClipState(
    val id: String,
    val label: String,
    val durationLabel: String? = null,
    val thumbnailUri: String? = null,
    val isSelected: Boolean = false
)

data class EditorTrackState(
    val type: EditorTrackType,
    val label: String,
    val isVisible: Boolean = true,
    val itemCount: Int = 0,
    val clips: List<EditorTimelineClipState> = emptyList(),
    val addLabel: String? = null
)

data class AppliedEditAction(
    val action: EditAction,
    val clipId: String,
    val mode: EditorMode
)
