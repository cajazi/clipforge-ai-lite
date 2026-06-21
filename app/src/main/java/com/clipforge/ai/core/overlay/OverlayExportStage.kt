@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.core.overlay

import androidx.media3.effect.OverlayEffect
import com.clipforge.ai.core.gl.RenderableBitmapOverlay
import java.util.concurrent.TimeUnit

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

        return renderables
            .sortedWith(
                compareBy<RenderableOverlay> { layerRank(it.layer) }
                    .thenBy { if (it.layer == OverlayLayer.USER) it.zIndex else 0 }
            )
            .map { renderable ->
                val compositionWindowMs = timeMap.mapWindow(renderable.windowStartMs, renderable.windowEndMs)
                RenderableBitmapOverlay(
                    renderable = renderable,
                    windowStartUs = compositionMsToUs(compositionWindowMs.first),
                    windowEndUs = compositionMsToUs(compositionWindowMs.last),
                    frameW = frameW,
                    frameH = frameH
                )
            }
    }

    private fun layerRank(layer: OverlayLayer): Int =
        when (layer) {
            OverlayLayer.USER -> 0
            OverlayLayer.SYSTEM -> 1
        }

    private fun compositionMsToUs(compositionMs: Long): Long =
        TimeUnit.MILLISECONDS.toMicros(compositionMs)
}
