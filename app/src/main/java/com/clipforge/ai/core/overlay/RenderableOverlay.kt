package com.clipforge.ai.core.overlay

import android.graphics.Bitmap

enum class OverlayLayer {
    USER,
    SYSTEM
}

data class OverlayTransform(
    val xNorm: Float,
    val yNorm: Float,
    val scale: Float,
    val rotationDeg: Float,
    val alpha: Float
)

interface RenderableOverlay {
    val id: String
    val windowStartMs: Long
    val windowEndMs: Long
    val layer: OverlayLayer
    val zIndex: Int

    fun transformAt(progress: Float): OverlayTransform

    fun frameAt(progress: Float, frameW: Int, frameH: Int): Bitmap
}

interface OverlaySource {
    suspend fun load(projectId: String): List<RenderableOverlay>
}
