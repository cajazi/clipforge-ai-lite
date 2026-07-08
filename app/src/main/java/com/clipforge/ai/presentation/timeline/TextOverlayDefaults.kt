package com.clipforge.ai.presentation.timeline

import android.graphics.Color
import com.clipforge.ai.core.overlay.OverlayLayer
import com.clipforge.ai.core.text.TextAlignment
import com.clipforge.ai.core.text.TextRenderSpec
import com.clipforge.ai.domain.model.TextOverlay
import java.util.UUID

data class TextOverlayCreationPlan(
    val overlay: TextOverlay?,
    val mirrorLegacyTimelineItem: Boolean
)

fun planDefaultTimelineTextOverlayCreation(
    projectId: String,
    text: String,
    timelineStartMs: Long,
    totalDurationMs: Long,
    zIndex: Int,
    id: String = UUID.randomUUID().toString()
): TextOverlayCreationPlan =
    TextOverlayCreationPlan(
        overlay = createDefaultTimelineTextOverlay(
            projectId = projectId,
            text = text,
            timelineStartMs = timelineStartMs,
            totalDurationMs = totalDurationMs,
            zIndex = zIndex,
            id = id
        ),
        mirrorLegacyTimelineItem = false
    )

fun createDefaultTimelineTextOverlay(
    projectId: String,
    text: String,
    timelineStartMs: Long,
    totalDurationMs: Long,
    zIndex: Int,
    id: String = UUID.randomUUID().toString()
): TextOverlay? {
    val clean = text.trim()
    if (clean.isEmpty()) return null

    val safeTotalMs = totalDurationMs.coerceAtLeast(0L)
    val startMs = if (safeTotalMs > 0L) {
        timelineStartMs.coerceIn(0L, safeTotalMs)
    } else {
        timelineStartMs.coerceAtLeast(0L)
    }
    val requestedEndMs = startMs + DEFAULT_TEXT_OVERLAY_DURATION_MS
    val endMs = if (safeTotalMs > 0L) {
        requestedEndMs.coerceAtMost(safeTotalMs).coerceAtLeast(startMs + 1L)
    } else {
        requestedEndMs
    }

    return TextOverlay(
        id = id,
        projectId = projectId,
        windowStartMs = startMs,
        windowEndMs = endMs,
        layer = OverlayLayer.USER,
        zIndex = zIndex,
        transform = DefaultTextOverlayTransform,
        renderSpec = TextRenderSpec(
            text = clean,
            fontId = "default",
            fontSizeNorm = 0.08f,
            colorArgb = Color.WHITE,
            bgColorArgb = null,
            bold = false,
            italic = false,
            alignment = TextAlignment.CENTER
        )
    )
}

private const val DEFAULT_TEXT_OVERLAY_DURATION_MS = 3_000L
