package com.clipforge.ai.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "effect_items",
    foreignKeys = [
        ForeignKey(
            entity = ProjectEntity::class,
            parentColumns = ["id"],
            childColumns = ["projectId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["projectId"], name = "index_effect_items_projectId")]
)
data class EffectItemEntity(
    @PrimaryKey val id: String,
    val projectId: String,
    val effectId: String,
    val scope: String,
    val startMs: Long,
    val endMs: Long,
    val zOrder: Int,
    val paramsJson: String
)
