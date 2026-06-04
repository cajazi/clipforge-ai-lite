package com.clipforge.ai.domain.model

import com.clipforge.ai.presentation.timeline.ClipUiModel

/**
 * One clip's position on the global project timeline (cumulative).
 * startMs/endMs are GLOBAL times, not clip-local.
 */
data class TimelineSegment(
    val clipId:       String,
    val mediaAssetId: String,
    val localUri:     String?,
    val startMs:      Long,
    val endMs:        Long,
    val thumbnailUri: String? = null,
    val label:        String  = "",
    val transition:   Transition? = null
) {
    val durationMs: Long get() = endMs - startMs
    fun contains(globalTimeMs: Long): Boolean = globalTimeMs >= startMs && globalTimeMs < endMs
}

fun buildTimelineSegments(clips: List<ClipUiModel>): List<TimelineSegment> {
    var cursor = 0L
    return clips.map { clip ->
        val start = cursor
        val end   = cursor + clip.durationMs.coerceAtLeast(1L)
        cursor    = end
        TimelineSegment(
            clipId       = clip.id,
            mediaAssetId = clip.mediaAssetId,
            localUri     = clip.thumbnailUri,
            startMs      = start,
            endMs        = end,
            thumbnailUri = clip.thumbnailUri,
            label        = clip.label,
            transition   = clip.transition
        )
    }
}
