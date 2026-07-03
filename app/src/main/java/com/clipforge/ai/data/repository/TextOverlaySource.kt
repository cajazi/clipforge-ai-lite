package com.clipforge.ai.data.repository

import com.clipforge.ai.core.overlay.OverlaySource
import com.clipforge.ai.core.overlay.RenderableOverlay
import com.clipforge.ai.core.text.TextOverlayRasterizer
import com.clipforge.ai.core.text.TextOverlayRenderable
import com.clipforge.ai.domain.repository.TextOverlayRepository

class TextOverlaySource(
    private val repository: TextOverlayRepository,
    private val rasterizer: TextOverlayRasterizer
) : OverlaySource {
    override suspend fun load(projectId: String): List<RenderableOverlay> =
        repository.getTextOverlaysForProject(projectId)
            .sortedBy { it.zIndex }
            .map { overlay -> TextOverlayRenderable(overlay, rasterizer) }
            .toList()
}
