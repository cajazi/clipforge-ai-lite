package com.clipforge.ai.data.local.entity
import androidx.room.*
@Entity(tableName = "media_assets",
    foreignKeys = [ForeignKey(entity = ProjectEntity::class, parentColumns = ["id"], childColumns = ["projectId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index("projectId")])
data class MediaAssetEntity(
    @PrimaryKey val id: String, val projectId: String, val mediaType: String,
    val localUri: String, val remoteUrl: String?, val durationMs: Long?,
    val fileSizeBytes: Long, val mimeType: String?, val createdAt: Long
)
