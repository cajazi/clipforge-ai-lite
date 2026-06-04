package com.clipforge.ai.domain.model
data class TimelineItem(
    val id: String, val projectId: String, val mediaAssetId: String,
    val trackIndex: Int, val orderIndex: Int, val startMs: Long, val endMs: Long,
    val trimStartMs: Long = 0L, val trimEndMs: Long = 0L,
    val fitMode: FitMode = FitMode.FIT, val transition: Transition? = null,
    val volume: Float = 1.0f, val opacity: Float = 1.0f
)
enum class FitMode { FIT, FILL, CROP, BLUR_BACKGROUND }
