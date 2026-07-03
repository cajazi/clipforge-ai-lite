@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.overlay

import androidx.media3.effect.OverlayEffect
import com.clipforge.ai.core.gl.RenderableBitmapOverlay

object OverlayExportStage {
    suspend fun build(
        projectId: String,
        sources: List<OverlaySource>,
        timeMap: TimelineToCompositionTimeMap,
        frameW: Int,
        frameH: Int
    ): OverlayEffect? {
        val overlays = buildOverlays(projectId, sources, timeMap, frameW, frameH)
        return if (overlays.isEmpty()) null else OverlayEffect(overlays)
    }

    internal suspend fun buildOverlays(
        projectId: String,
        sources: List<OverlaySource>,
        timeMap: TimelineToCompositionTimeMap,
        frameW: Int,
        frameH: Int
    ): List<RenderableBitmapOverlay> {
        if (sources.isEmpty()) return emptyList()

        val renderables = sources.flatMap { source -> source.load(projectId) }
        if (renderables.isEmpty()) return emptyList()

        val frameEngine = OverlayFrameEngine.fromTimelineRenderables(renderables, timeMap)

        return frameEngine.orderedOverlays
            .map { overlay ->
                RenderableBitmapOverlay(
                    renderable = overlay.renderable,
                    windowStartUs = overlay.windowStartUs,
                    windowEndUs = overlay.windowEndUs,
                    frameW = frameW,
                    frameH = frameH,
                    frameEvaluator = frameEngine
                )
            }
    }
}
