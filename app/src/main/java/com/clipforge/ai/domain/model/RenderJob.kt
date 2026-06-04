package com.clipforge.ai.domain.model
data class RenderJob(
    val id: String, val projectId: String, val status: RenderJobStatus,
    val exportQuality: String, val outputUrl: String? = null,
    val errorMessage: String? = null, val progressPercent: Int = 0,
    val createdAt: Long = System.currentTimeMillis(), val completedAt: Long? = null
)
enum class RenderJobStatus { QUEUED, UPLOADING, PROCESSING, RENDERING, COMPLETED, FAILED, CANCELLED }
