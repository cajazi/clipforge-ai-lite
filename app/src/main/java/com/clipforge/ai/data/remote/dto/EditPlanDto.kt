package com.clipforge.ai.data.remote.dto
import com.google.gson.annotations.SerializedName
data class EditPlanDto(
    @SerializedName("project_id") val projectId: String, @SerializedName("aspect_ratio") val aspectRatio: String,
    @SerializedName("quality") val quality: String, @SerializedName("watermark") val addWatermark: Boolean,
    @SerializedName("clips") val clips: List<ClipDto>, @SerializedName("overlays") val overlays: List<OverlayDto>,
    @SerializedName("audio_tracks") val audioTracks: List<AudioTrackDto>
)
data class ClipDto(
    @SerializedName("asset_id") val assetId: String, @SerializedName("order_index") val orderIndex: Int,
    @SerializedName("trim_start_ms") val trimStartMs: Long, @SerializedName("trim_end_ms") val trimEndMs: Long,
    @SerializedName("playback_speed") val playbackSpeed: Float = 1f,
    @SerializedName("volume_multiplier") val volumeMultiplier: Float = 1f,
    @SerializedName("fit_mode") val fitMode: String, @SerializedName("transition_type") val transitionType: String?,
    @SerializedName("transition_dur_ms") val transitionDurMs: Long?
)
data class OverlayDto(
    @SerializedName("asset_id") val assetId: String?, @SerializedName("type") val type: String,
    @SerializedName("text") val text: String?, @SerializedName("x") val positionX: Float,
    @SerializedName("y") val positionY: Float, @SerializedName("scale_x") val scaleX: Float,
    @SerializedName("scale_y") val scaleY: Float, @SerializedName("start_ms") val startMs: Long,
    @SerializedName("end_ms") val endMs: Long, @SerializedName("opacity") val opacity: Float
)
data class AudioTrackDto(
    @SerializedName("asset_id") val assetId: String, @SerializedName("volume") val volume: Float,
    @SerializedName("start_ms") val startMs: Long, @SerializedName("end_ms") val endMs: Long
)
