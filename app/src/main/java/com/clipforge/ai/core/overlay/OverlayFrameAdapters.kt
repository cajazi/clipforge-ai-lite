package com.clipforge.ai.core.overlay

interface SharedOverlayFrameAdapter {
    fun evaluateAtTimeUs(timeUs: Long): OverlayFrameState

    fun evaluateFrame(frameIndex: Long): OverlayFrameState
}

class ExportOverlayFrameAdapter(
    private val evaluator: OverlayFrameEvaluator
) : SharedOverlayFrameAdapter {
    override fun evaluateAtTimeUs(timeUs: Long): OverlayFrameState =
        evaluator.frameStateAtTimeUs(timeUs)

    override fun evaluateFrame(frameIndex: Long): OverlayFrameState =
        evaluator.frameStateForFrame(frameIndex)
}

class PreviewOverlayFrameAdapter(
    private val evaluator: OverlayFrameEvaluator
) : SharedOverlayFrameAdapter {
    override fun evaluateAtTimeUs(timeUs: Long): OverlayFrameState =
        evaluator.frameStateAtTimeUs(timeUs)

    override fun evaluateFrame(frameIndex: Long): OverlayFrameState =
        evaluator.frameStateForFrame(frameIndex)
}
