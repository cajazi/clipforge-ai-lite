package com.clipforge.ai.domain.repository

import com.clipforge.ai.domain.model.TextOverlay
import kotlinx.coroutines.flow.Flow

interface TextOverlayRepository {
    suspend fun getTextOverlaysForProject(projectId: String): List<TextOverlay>
    fun observeTextOverlaysForProject(projectId: String): Flow<List<TextOverlay>>
    suspend fun upsertTextOverlay(textOverlay: TextOverlay)
    suspend fun upsertTextOverlays(textOverlays: List<TextOverlay>)
    suspend fun deleteTextOverlay(id: String)
    suspend fun deleteTextOverlaysForProject(projectId: String)
}
