package com.clipforge.ai.domain.model

import com.clipforge.ai.core.overlay.OverlayLayer
import com.clipforge.ai.core.overlay.OverlayTransform
import com.clipforge.ai.core.text.TextRenderSpec

data class TextOverlay(
    val id: String,
    val projectId: String,
    val windowStartMs: Long,
    val windowEndMs: Long,
    val layer: OverlayLayer,
    val zIndex: Int,
    val transform: OverlayTransform,
    val renderSpec: TextRenderSpec
)
