package com.clipforge.ai.data.local.entity
import androidx.room.*
@Entity(tableName = "timeline_items",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")])
data class TimelineItemEntity(
    @PrimaryKey val id: String, val projectId: String, val mediaAssetId: String,
    val trackIndex: Int, val orderIndex: Int, val startMs: Long, val endMs: Long,
    val trimStartMs: Long, val trimEndMs: Long, val fitMode: String,
    val transitionType: String?, val transitionDurationMs: Long?,
    @ColumnInfo(name = "volumeMultiplier") val volume: Float,
    val opacity: Float,
    val playbackSpeed: Float = 1f
)
