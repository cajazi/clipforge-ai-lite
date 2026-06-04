package com.clipforge.ai.domain.model
data class MediaAsset(
    val id: String, val projectId: String, val mediaType: MediaType,
    val localUri: String, val remoteUrl: String? = null,
    val durationMs: Long? = null, val fileSizeBytes: Long = 0L,
    val mimeType: String? = null, val createdAt: Long = System.currentTimeMillis()
)
enum class MediaType { VIDEO, IMAGE, AUDIO, LOGO, OVERLAY_IMAGE, OVERLAY_VIDEO }
