package com.clipforge.ai.presentation.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.overlay.OverlayTransform
import com.clipforge.ai.core.text.CanvasTextOverlayRasterizer
import com.clipforge.ai.domain.model.TextOverlay
import kotlin.math.roundToInt

@Composable
fun TextOverlayInteractionLayer(
    textOverlays: List<TextOverlay>,
    timelineTimeMs: Long,
    selectedTextOverlayId: String?,
    onSelectTextOverlay: (String) -> Unit,
    onPreviewTransformChanged: (String, OverlayTransform) -> Unit,
    onTransformCommitted: (String, OverlayTransform) -> Unit,
    modifier: Modifier = Modifier
) {
    if (textOverlays.isEmpty()) return

    val density = LocalDensity.current
    val rasterizer = remember { CanvasTextOverlayRasterizer() }
    val frameEngine = remember(textOverlays, rasterizer) {
        buildPreviewOverlayFrameEngine(textOverlays, rasterizer)
    }
    val frameAdapter = remember(frameEngine) { com.clipforge.ai.core.overlay.PreviewOverlayFrameAdapter(frameEngine) }
    val frameState = remember(frameAdapter, timelineTimeMs) {
        frameAdapter.evaluateAtTimeUs(timelineMsToUs(timelineTimeMs))
    }
    if (frameState.activeOverlays.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val frameW = with(density) { maxWidth.toPx().roundToInt().coerceAtLeast(1) }
        val frameH = with(density) { maxHeight.toPx().roundToInt().coerceAtLeast(1) }
        val overlaysById = remember(textOverlays) { textOverlays.associateBy { it.id } }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
            val selectedOverlay = selectedTextOverlayId?.let(overlaysById::get)
            if (selectedOverlay != null && frameState.activeOverlays.any { it.id == selectedOverlay.id }) {
                SelectedTextOverlayDragSurface(
                    overlay = selectedOverlay,
                    frameWidthPx = frameW,
                    frameHeightPx = frameH,
                    onSelectTextOverlay = onSelectTextOverlay,
                    onPreviewTransformChanged = onPreviewTransformChanged,
                    onTransformCommitted = onTransformCommitted,
                    modifier = Modifier
                        .fillMaxSize()
                        .zIndex(200f)
                )
            }
            frameState.activeOverlays.forEachIndexed { index, layer ->
                val overlay = overlaysById[layer.id] ?: return@forEachIndexed
                val bitmap = remember(layer.id, layer.progress, layer.renderable, frameW, frameH) {
                    layer.renderable.frameAt(layer.progress, frameW, frameH)
                }
                val placement = previewOverlayPlacement(
                    layer = layer,
                    bitmapWidthPx = bitmap.width,
                    bitmapHeightPx = bitmap.height,
                    frameWidthPx = frameW,
                    frameHeightPx = frameH
                )
                TextOverlayDragTarget(
                    overlay = overlay,
                    selected = overlay.id == selectedTextOverlayId,
                    placement = placement,
                    bitmapWidthPx = bitmap.width,
                    bitmapHeightPx = bitmap.height,
                    frameWidthPx = frameW,
                    frameHeightPx = frameH,
                    zIndex = index.toFloat(),
                    onSelectTextOverlay = onSelectTextOverlay,
                    onPreviewTransformChanged = onPreviewTransformChanged,
                    onTransformCommitted = onTransformCommitted
                )
            }
        }
    }
}

@Composable
private fun SelectedTextOverlayDragSurface(
    overlay: TextOverlay,
    frameWidthPx: Int,
    frameHeightPx: Int,
    onSelectTextOverlay: (String) -> Unit,
    onPreviewTransformChanged: (String, OverlayTransform) -> Unit,
    onTransformCommitted: (String, OverlayTransform) -> Unit,
    modifier: Modifier = Modifier
) {
    val latestTransform = rememberUpdatedState(overlay.transform)
    Box(
        modifier = modifier.pointerInput(overlay.id, frameWidthPx, frameHeightPx) {
            var workingTransform = latestTransform.value
            detectDragGestures(
                onDragStart = {
                    workingTransform = latestTransform.value
                    onSelectTextOverlay(overlay.id)
                },
                onDragEnd = {
                    onTransformCommitted(overlay.id, workingTransform)
                },
                onDragCancel = {
                    onTransformCommitted(overlay.id, workingTransform)
                }
            ) { change, dragAmount ->
                change.consume()
                workingTransform = moveTextOverlayTransformByPreviewDelta(
                    transform = workingTransform,
                    deltaXPx = dragAmount.x,
                    deltaYPx = dragAmount.y,
                    frameWidthPx = frameWidthPx,
                    frameHeightPx = frameHeightPx
                )
                onPreviewTransformChanged(overlay.id, workingTransform)
            }
        }
    )
}

@Composable
private fun TextOverlayDragTarget(
    overlay: TextOverlay,
    selected: Boolean,
    placement: PreviewOverlayPlacement,
    bitmapWidthPx: Int,
    bitmapHeightPx: Int,
    frameWidthPx: Int,
    frameHeightPx: Int,
    zIndex: Float,
    onSelectTextOverlay: (String) -> Unit,
    onPreviewTransformChanged: (String, OverlayTransform) -> Unit,
    onTransformCommitted: (String, OverlayTransform) -> Unit
) {
    val density = LocalDensity.current
    Box(
        modifier = Modifier
            .offset {
                IntOffset(
                    x = placement.translationX.roundToInt(),
                    y = placement.translationY.roundToInt()
                )
            }
            .size(
                width = with(density) { bitmapWidthPx.toDp() },
                height = with(density) { bitmapHeightPx.toDp() }
            )
            .graphicsLayer {
                alpha = if (placement.alpha > 0f) 1f else 0f
                scaleX = placement.scale
                scaleY = placement.scale
                rotationZ = placement.rotationDeg
                transformOrigin = TransformOrigin.Center
            }
            .zIndex(100f + zIndex)
            .pointerInput(overlay.id, overlay.transform, frameWidthPx, frameHeightPx) {
                var workingTransform = overlay.transform
                detectDragGestures(
                    onDragStart = {
                        workingTransform = overlay.transform
                        onSelectTextOverlay(overlay.id)
                    },
                    onDragEnd = {
                        onTransformCommitted(overlay.id, workingTransform)
                    },
                    onDragCancel = {
                        onTransformCommitted(overlay.id, workingTransform)
                    }
                ) { change, dragAmount ->
                    change.consume()
                    workingTransform = moveTextOverlayTransformByPreviewDelta(
                        transform = workingTransform,
                        deltaXPx = dragAmount.x,
                        deltaYPx = dragAmount.y,
                        frameWidthPx = frameWidthPx,
                        frameHeightPx = frameHeightPx
                    )
                    onPreviewTransformChanged(overlay.id, workingTransform)
                }
            }
            .clickable { onSelectTextOverlay(overlay.id) }
    ) {
        if (selected) {
            TextOverlaySelectionAffordance()
        }
    }
}

@Composable
private fun BoxScope.TextOverlaySelectionAffordance() {
    Box(
        modifier = Modifier
            .matchParentSize()
            .border(width = 1.dp, color = Color.White)
    )
    TextOverlaySelectionHandle(Modifier.align(Alignment.TopStart).offset((-5).dp, (-5).dp))
    TextOverlaySelectionHandle(Modifier.align(Alignment.TopEnd).offset(5.dp, (-5).dp))
    TextOverlaySelectionHandle(Modifier.align(Alignment.BottomStart).offset((-5).dp, 5.dp))
    TextOverlaySelectionHandle(Modifier.align(Alignment.BottomEnd).offset(5.dp, 5.dp))
}

@Composable
private fun TextOverlaySelectionHandle(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(10.dp)
            .clip(CircleShape)
            .background(Color.Black.copy(alpha = 0.72f))
            .border(width = 1.dp, color = AppColors.Primary, shape = CircleShape)
    )
}
