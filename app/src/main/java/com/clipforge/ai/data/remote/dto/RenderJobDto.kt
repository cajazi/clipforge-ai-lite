package com.clipforge.ai.data.remote.dto
import com.google.gson.annotations.SerializedName
data class RenderJobDto(
    @SerializedName("id") val id: String, @SerializedName("project_id") val projectId: String,
    @SerializedName("status") val status: String, @SerializedName("export_quality") val exportQuality: String,
    @SerializedName("output_url") val outputUrl: String?, @SerializedName("error_message") val errorMessage: String?,
    @SerializedName("progress_percent") val progressPercent: Int,
    @SerializedName("created_at") val createdAt: Long, @SerializedName("completed_at") val completedAt: Long?
)
