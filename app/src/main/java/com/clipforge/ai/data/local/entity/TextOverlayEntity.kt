package com.clipforge.ai.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "text_overlays",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"], name = "index_text_overlays_projectId")]
)
data class TextOverlayEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val text: String,
    val fontId: String,
    val fontSizeNorm: Float,
    val colorArgb: Int,
    val bgColorArgb: Int?,
    val bold: Boolean,
    val italic: Boolean,
    val alignment: String,
    val highlightStart: Int?,
    val highlightEnd: Int?,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val layer: String,
    val zIndex: Int,
    val xNorm: Float,
    val yNorm: Float,
    val scale: Float,
    val rotationDeg: Float,
    val alpha: Float
)
