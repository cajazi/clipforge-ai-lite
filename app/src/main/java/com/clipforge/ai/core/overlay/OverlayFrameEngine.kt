package com.clipforge.ai.core.overlay

import java.util.concurrent.TimeUnit

interface OverlayFrameEvaluator {
    val frameClock: DeterministicOverlayFrameClock

    fun frameStateAtTimeUs(timeUs: Long): OverlayFrameState

    fun frameStateForFrame(frameIndex: Long): OverlayFrameState
}

data class OverlayFrameInput(
    val renderable: RenderableOverlay,
    val windowStartUs: Long,
    val windowEndUs: Long,
    val sourceOrder: Int
) {
    init {
        require(windowStartUs >= 0L) { "Overlay window start must be non-negative" }
        require(windowEndUs >= windowStartUs) { "Overlay window end must be >= start" }
        require(sourceOrder >= 0) { "Overlay source order must be non-negative" }
    }
}

data class OverlayFrameLayerState(
    val id: String,
    val layer: OverlayLayer,
    val zIndex: Int,
    val sourceOrder: Int,
    val windowStartUs: Long,
    val windowEndUs: Long,
    val progress: Float,
    val transform: OverlayTransform,
    val renderable: RenderableOverlay
)

data class OverlayFrameState(
    val frameIndex: Long,
    val frameTimeUs: Long,
    val activeOverlays: List<OverlayFrameLayerState>
)

class OverlayFrameEngine private constructor(
    override val frameClock: DeterministicOverlayFrameClock,
    overlays: List<OverlayFrameInput>
) : OverlayFrameEvaluator {

    val orderedOverlays: List<OverlayFrameInput> = overlays
        .filter { it.windowEndUs > it.windowStartUs }
        .sortedWith(overlayOrdering)

    override fun frameStateAtTimeUs(timeUs: Long): OverlayFrameState =
        frameStateForFrame(frameClock.frameIndexAt(timeUs))

    override fun frameStateForFrame(frameIndex: Long): OverlayFrameState {
        val frameTimeUs = frameClock.frameTimeUs(frameIndex)
        val active = orderedOverlays
            .asSequence()
            .filter { overlay -> isActive(overlay, frameTimeUs) }
            .map { overlay -> overlay.toLayerState(frameTimeUs) }
            .toList()
        return OverlayFrameState(
            frameIndex = frameIndex,
            frameTimeUs = frameTimeUs,
            activeOverlays = active
        )
    }

    private fun isActive(overlay: OverlayFrameInput, frameTimeUs: Long): Boolean =
        overlay.windowStartUs <= frameTimeUs && frameTimeUs < overlay.windowEndUs

    private fun OverlayFrameInput.toLayerState(frameTimeUs: Long): OverlayFrameLayerState {
        val progress = progressAt(frameTimeUs)
        return OverlayFrameLayerState(
            id = renderable.id,
            layer = renderable.layer,
            zIndex = renderable.zIndex,
            sourceOrder = sourceOrder,
            windowStartUs = windowStartUs,
            windowEndUs = windowEndUs,
            progress = progress,
            transform = renderable.transformAt(progress),
            renderable = renderable
        )
    }

    private fun OverlayFrameInput.progressAt(frameTimeUs: Long): Float {
        val spanUs = (windowEndUs - windowStartUs).coerceAtLeast(1L)
        return ((frameTimeUs - windowStartUs).toDouble() / spanUs.toDouble())
            .coerceIn(0.0, 1.0)
            .toFloat()
    }

    companion object {
        fun fromCompositionOverlays(
            overlays: List<OverlayFrameInput>,
            frameClock: DeterministicOverlayFrameClock = DeterministicOverlayFrameClock()
        ): OverlayFrameEngine = OverlayFrameEngine(frameClock, overlays)

        fun fromTimelineRenderables(
            renderables: List<RenderableOverlay>,
            timeMap: TimelineToCompositionTimeMap,
            frameClock: DeterministicOverlayFrameClock = DeterministicOverlayFrameClock()
        ): OverlayFrameEngine {
            val overlays = renderables.mapIndexed { index, renderable ->
                val compositionWindowMs = timeMap.mapWindow(
                    renderable.windowStartMs,
                    renderable.windowEndMs
                )
                OverlayFrameInput(
                    renderable = renderable,
                    windowStartUs = compositionMsToUs(compositionWindowMs.first),
                    windowEndUs = compositionMsToUs(compositionWindowMs.last),
                    sourceOrder = index
                )
            }
            return fromCompositionOverlays(overlays, frameClock)
        }

        private val overlayOrdering: Comparator<OverlayFrameInput> =
            compareBy<OverlayFrameInput> { layerRank(it.renderable.layer) }
                .thenBy { it.renderable.zIndex }
                .thenBy { it.sourceOrder }
                .thenBy { it.renderable.id }

        private fun layerRank(layer: OverlayLayer): Int =
            when (layer) {
                OverlayLayer.USER -> 0
                OverlayLayer.SYSTEM -> 1
            }

        private fun compositionMsToUs(compositionMs: Long): Long =
            TimeUnit.MILLISECONDS.toMicros(compositionMs)
    }
}
