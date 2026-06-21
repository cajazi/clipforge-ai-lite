package com.clipforge.ai.data.repository

import com.clipforge.ai.core.overlay.OverlayLayer
import com.clipforge.ai.core.overlay.OverlayTransform
import com.clipforge.ai.core.text.TextAlignment
import com.clipforge.ai.core.text.TextRenderSpec
import com.clipforge.ai.data.local.dao.TextOverlayDao
import com.clipforge.ai.data.local.entity.TextOverlayEntity
import com.clipforge.ai.domain.model.TextOverlay
import com.clipforge.ai.domain.repository.TextOverlayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class TextOverlayRepositoryImpl(
    private val textOverlayDao: TextOverlayDao
) : TextOverlayRepository {

    override suspend fun getTextOverlaysForProject(projectId: String): List<TextOverlay> =
        textOverlayDao.getForProject(projectId).map { it.toDomain() }

    override fun observeTextOverlaysForProject(projectId: String): Flow<List<TextOverlay>> =
        textOverlayDao.observeForProject(projectId).map { entities -> entities.map { it.toDomain() } }

    override suspend fun upsertTextOverlay(textOverlay: TextOverlay) {
        textOverlayDao.upsert(textOverlay.toEntity())
    }

    override suspend fun upsertTextOverlays(textOverlays: List<TextOverlay>) {
        textOverlayDao.upsertAll(textOverlays.map { it.toEntity() })
    }

    override suspend fun deleteTextOverlay(id: String) {
        textOverlayDao.deleteById(id)
    }

    override suspend fun deleteTextOverlaysForProject(projectId: String) {
        textOverlayDao.deleteForProject(projectId)
    }
}

fun TextOverlay.toEntity(): TextOverlayEntity {
    val highlightRange = renderSpec.highlightRange
    return TextOverlayEntity(
        id = id,
        projectId = projectId,
        text = renderSpec.text,
        fontId = renderSpec.fontId,
        fontSizeNorm = renderSpec.fontSizeNorm,
        colorArgb = renderSpec.colorArgb,
        bgColorArgb = renderSpec.bgColorArgb,
        bold = renderSpec.bold,
        italic = renderSpec.italic,
        alignment = renderSpec.alignment.name,
        highlightStart = highlightRange?.first,
        highlightEnd = highlightRange?.last,
        windowStartMs = windowStartMs,
        windowEndMs = windowEndMs,
        layer = layer.name,
        zIndex = zIndex,
        xNorm = transform.xNorm,
        yNorm = transform.yNorm,
        scale = transform.scale,
        rotationDeg = transform.rotationDeg,
        alpha = transform.alpha
    )
}

fun TextOverlayEntity.toDomain(): TextOverlay {
    val highlightRange = when {
        highlightStart == null && highlightEnd == null -> null
        highlightStart != null && highlightEnd != null -> highlightStart..highlightEnd
        else -> throw IllegalArgumentException("Text overlay $id has incomplete highlight range")
    }
    return TextOverlay(
        id = id,
        projectId = projectId,
        windowStartMs = windowStartMs,
        windowEndMs = windowEndMs,
        layer = OverlayLayer.valueOf(layer),
        zIndex = zIndex,
        transform = OverlayTransform(
            xNorm = xNorm,
            yNorm = yNorm,
            scale = scale,
            rotationDeg = rotationDeg,
            alpha = alpha
        ),
        renderSpec = TextRenderSpec(
            text = text,
            fontId = fontId,
            fontSizeNorm = fontSizeNorm,
            colorArgb = colorArgb,
            bgColorArgb = bgColorArgb,
            bold = bold,
            italic = italic,
            alignment = TextAlignment.valueOf(alignment),
            highlightRange = highlightRange
        )
    )
}
