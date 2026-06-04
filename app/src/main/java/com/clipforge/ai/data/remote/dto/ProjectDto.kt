package com.clipforge.ai.data.remote.dto
import com.google.gson.annotations.SerializedName
data class ProjectDto(
    @SerializedName("id") val id: String, @SerializedName("title") val title: String,
    @SerializedName("aspect_ratio") val aspectRatio: String, @SerializedName("quality") val quality: String,
    @SerializedName("plan_type") val planType: String, @SerializedName("thumbnail_url") val thumbnailUrl: String?,
    @SerializedName("created_at") val createdAt: Long, @SerializedName("updated_at") val updatedAt: Long
)
