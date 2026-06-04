package com.clipforge.ai.core.supabase

import com.google.gson.annotations.SerializedName

// ── Projects ─────────────────────────────────────────────────
data class SupabaseProject(
    @SerializedName("id")            val id: String,
    @SerializedName("title")         val title: String,
    @SerializedName("aspect_ratio")  val aspectRatio: String,
    @SerializedName("export_quality") val exportQuality: String,
    @SerializedName("plan_type")     val planType: String,
    @SerializedName("thumbnail_url") val thumbnailUrl: String? = null,
    @SerializedName("user_id")       val userId: String? = null,
    @SerializedName("created_at")    val createdAt: String? = null,
    @SerializedName("updated_at")    val updatedAt: String? = null
)

data class CreateProjectBody(
    @SerializedName("id")            val id: String,
    @SerializedName("title")         val title: String,
    @SerializedName("aspect_ratio")  val aspectRatio: String,
    @SerializedName("export_quality") val exportQuality: String,
    @SerializedName("plan_type")     val planType: String = "FREE"
)

// ── Media Assets ─────────────────────────────────────────────
data class SupabaseMediaAsset(
    @SerializedName("id")           val id: String,
    @SerializedName("project_id")   val projectId: String,
    @SerializedName("media_type")   val mediaType: String,
    @SerializedName("storage_path") val storagePath: String? = null,
    @SerializedName("public_url")   val publicUrl: String? = null,
    @SerializedName("duration_ms")  val durationMs: Long? = null,
    @SerializedName("file_size")    val fileSizeBytes: Long = 0L,
    @SerializedName("mime_type")    val mimeType: String? = null,
    @SerializedName("created_at")   val createdAt: String? = null
)

// ── Render Jobs ──────────────────────────────────────────────
data class SupabaseRenderJob(
    @SerializedName("id")               val id: String,
    @SerializedName("project_id")       val projectId: String,
    @SerializedName("status")           val status: String,
    @SerializedName("export_quality")   val exportQuality: String,
    @SerializedName("output_url")       val outputUrl: String? = null,
    @SerializedName("error_message")    val errorMessage: String? = null,
    @SerializedName("progress_percent") val progressPercent: Int = 0,
    @SerializedName("add_watermark")    val addWatermark: Boolean = true,
    @SerializedName("created_at")       val createdAt: String? = null,
    @SerializedName("completed_at")     val completedAt: String? = null
)

data class CreateRenderJobBody(
    @SerializedName("id")             val id: String,
    @SerializedName("project_id")     val projectId: String,
    @SerializedName("status")         val status: String = "QUEUED",
    @SerializedName("export_quality") val exportQuality: String,
    @SerializedName("add_watermark")  val addWatermark: Boolean
)
