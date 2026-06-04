package com.clipforge.ai.domain.model

data class AutoEditSettings(
    val finalDurationSeconds: Int = 30,
    val secondsPerClip: Int       = 3,
    val transitionType: TransitionType = TransitionType.FADE,
    val musicEnabled: Boolean     = false,
    val musicAssetId: String?     = null
) {
    /** Total number of clips that will be generated. */
    val expectedClipCount: Int
        get() = if (secondsPerClip > 0) finalDurationSeconds / secondsPerClip else 0

    /** Validate settings are coherent. Returns error message or null. */
    fun validate(): String? = when {
        finalDurationSeconds <= 0          -> "Final duration must be greater than 0"
        secondsPerClip <= 0                -> "Seconds per clip must be greater than 0"
        secondsPerClip > finalDurationSeconds -> "Seconds per clip cannot exceed final duration"
        else                               -> null
    }
}
