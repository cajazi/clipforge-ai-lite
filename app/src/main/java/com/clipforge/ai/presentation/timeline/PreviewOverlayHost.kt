package com.clipforge.ai.presentation.timeline

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.zIndex
import com.clipforge.ai.core.overlay.DeterministicOverlayFrameClock
import com.clipforge.ai.core.overlay.OverlayFrameEngine
import com.clipforge.ai.core.overlay.OverlayFrameInput
import com.clipforge.ai.core.overlay.OverlayFrameLayerState
import com.clipforge.ai.core.overlay.OverlayFrameState
import com.clipforge.ai.core.overlay.PreviewOverlayFrameAdapter
import com.clipforge.ai.core.text.CanvasTextOverlayRasterizer
import com.clipforge.ai.core.text.TextOverlayRasterizer
import com.clipforge.ai.core.text.TextOverlayRenderable
import com.clipforge.ai.domain.model.TextOverlay
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

@Composable
fun PreviewOverlayHost(
    textOverlays: List<TextOverlay>,
    timelineTimeMs: Long,
    modifier: Modifier = Modifier
) {
    if (textOverlays.isEmpty()) return

    val density = LocalDensity.current
    val rasterizer = remember { CanvasTextOverlayRasterizer() }
    val frameEngine = remember(textOverlays, rasterizer) {
        buildPreviewOverlayFrameEngine(textOverlays, rasterizer)
    }
    val frameAdapter = remember(frameEngine) { PreviewOverlayFrameAdapter(frameEngine) }
    val frameState = remember(frameAdapter, timelineTimeMs) {
        frameAdapter.evaluateAtTimeUs(timelineMsToUs(timelineTimeMs))
    }

    if (frameState.activeOverlays.isEmpty()) return

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val frameW = with(density) { maxWidth.toPx().roundToInt().coerceAtLeast(1) }
        val frameH = with(density) { maxHeight.toPx().roundToInt().coerceAtLeast(1) }
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopStart) {
            frameState.activeOverlays.forEachIndexed { index, layer ->
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
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(
                            width = with(density) { bitmap.width.toDp() },
                            height = with(density) { bitmap.height.toDp() }
                        )
                        .graphicsLayer {
                            alpha = placement.alpha
                            translationX = placement.translationX
                            translationY = placement.translationY
                            scaleX = placement.scale
                            scaleY = placement.scale
                            rotationZ = placement.rotationDeg
                        }
                        .zIndex(index.toFloat())
                )
            }
        }
    }
}

internal data class PreviewOverlayPlacement(
    val translationX: Float,
    val translationY: Float,
    val scale: Float,
    val alpha: Float,
    val rotationDeg: Float
)

internal fun buildPreviewOverlayFrameEngine(
    textOverlays: List<TextOverlay>,
    rasterizer: TextOverlayRasterizer,
    frameClock: DeterministicOverlayFrameClock = DeterministicOverlayFrameClock()
): OverlayFrameEngine {
    val inputs = textOverlays
        .sortedWith(compareBy<TextOverlay> { it.zIndex }.thenBy { it.windowStartMs }.thenBy { it.id })
        .mapIndexed { index, overlay ->
            OverlayFrameInput(
                renderable = TextOverlayRenderable(overlay, rasterizer),
                windowStartUs = timelineMsToUs(overlay.windowStartMs),
                windowEndUs = timelineMsToUs(overlay.windowEndMs),
                sourceOrder = index
            )
        }
    return OverlayFrameEngine.fromCompositionOverlays(inputs, frameClock)
}

internal fun previewOverlayFrameStateAt(
    textOverlays: List<TextOverlay>,
    timelineTimeMs: Long,
    rasterizer: TextOverlayRasterizer,
    frameClock: DeterministicOverlayFrameClock = DeterministicOverlayFrameClock()
): OverlayFrameState {
    val engine = buildPreviewOverlayFrameEngine(textOverlays, rasterizer, frameClock)
    return PreviewOverlayFrameAdapter(engine).evaluateAtTimeUs(timelineMsToUs(timelineTimeMs))
}

internal fun previewOverlayPlacement(
    layer: OverlayFrameLayerState,
    bitmapWidthPx: Int,
    bitmapHeightPx: Int,
    frameWidthPx: Int,
    frameHeightPx: Int
): PreviewOverlayPlacement {
    val transform = layer.transform
    val centerX = transform.xNorm * frameWidthPx
    val centerY = transform.yNorm * frameHeightPx
    return PreviewOverlayPlacement(
        translationX = centerX - bitmapWidthPx / 2f,
        translationY = centerY - bitmapHeightPx / 2f,
        scale = transform.scale,
        alpha = transform.alpha.coerceIn(0f, 1f),
        rotationDeg = transform.rotationDeg
    )
}

internal fun timelineMsToUs(timelineMs: Long): Long =
    TimeUnit.MILLISECONDS.toMicros(timelineMs.coerceAtLeast(0L))
