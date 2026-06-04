package com.clipforge.ai.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "projects")
data class ProjectEntity(
    @PrimaryKey val id: String,
    val title: String,
    val aspectRatio: String,
    val exportQuality: String,
    val planType: String,
    val thumbnailUri: String?,
    val createdAt: Long,
    val updatedAt: Long,
    // New fields
    val projectType: String             = "MANUAL",
    val autoFinalDuration: Int?         = null,
    val autoSecondsPerClip: Int?        = null,
    val autoTransitionType: String?     = null,
    val autoMusicEnabled: Boolean       = false,
    val autoMusicAssetId: String?       = null
)
