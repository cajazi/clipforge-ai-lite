package com.clipforge.ai.presentation.timeline

import com.clipforge.ai.core.overlay.OverlayTransform
import kotlin.math.abs

val DefaultTextOverlayTransform = OverlayTransform(
    xNorm = 0.5f,
    yNorm = 0.5f,
    scale = 1f,
    rotationDeg = 0f,
    alpha = 1f
)

fun moveTextOverlayTransformByPreviewDelta(
    transform: OverlayTransform,
    deltaXPx: Float,
    deltaYPx: Float,
    frameWidthPx: Int,
    frameHeightPx: Int
): OverlayTransform {
    val dx = if (frameWidthPx > 0) deltaXPx / frameWidthPx else 0f
    val dy = if (frameHeightPx > 0) deltaYPx / frameHeightPx else 0f
    return transform.copy(
        xNorm = (transform.xNorm + dx).coerceIn(0f, 1f),
        yNorm = (transform.yNorm + dy).coerceIn(0f, 1f)
    )
}

fun textOverlayTransformsEquivalent(
    first: OverlayTransform,
    second: OverlayTransform,
    epsilon: Float = 0.0001f
): Boolean =
    abs(first.xNorm - second.xNorm) <= epsilon &&
        abs(first.yNorm - second.yNorm) <= epsilon &&
        abs(first.scale - second.scale) <= epsilon &&
        abs(first.rotationDeg - second.rotationDeg) <= epsilon &&
        abs(first.alpha - second.alpha) <= epsilon
