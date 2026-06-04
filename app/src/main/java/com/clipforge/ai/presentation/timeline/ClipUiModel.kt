package com.clipforge.ai.presentation.timeline

import com.clipforge.ai.domain.model.Transition
import com.clipforge.ai.domain.model.MediaType

data class ClipUiModel(
    val id:           String,
    val mediaAssetId: String,
    val label:        String,
    val durationMs:   Long,
    val thumbnailUri: String?,
    val transition:   Transition?,
    val mediaType:    MediaType = MediaType.VIDEO,
    val timelineStartMs: Long = 0L,
    val timelineEndMs: Long = timelineStartMs + durationMs,
    val sourceStartMs: Long = 0L,
    val sourceEndMs: Long = sourceStartMs + durationMs,
    val sourceDurationMs: Long = sourceEndMs.coerceAtLeast(durationMs),
    val playbackSpeed: Float = 1f,
    val volume: Float = 1f,
    val opacity: Float = 1f,
    val transform: ClipTransform = ClipTransform()
)

data class ClipTransform(
    val scale: Float = 1f,
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    val rotation: Float = 0f
)
