package com.clipforge.ai.data.remote.dto
import com.google.gson.annotations.SerializedName
data class MediaAssetDto(
    @SerializedName("id") val id: String, @SerializedName("project_id") val projectId: String,
    @SerializedName("media_type") val mediaType: String, @SerializedName("remote_url") val remoteUrl: String?,
    @SerializedName("duration_ms") val durationMs: Long?, @SerializedName("file_size") val fileSizeBytes: Long,
    @SerializedName("mime_type") val mimeType: String?
)
