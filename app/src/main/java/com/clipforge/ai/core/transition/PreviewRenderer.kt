package com.clipforge.ai.core.transition

/**
 * Per-layer visual transform for a preview transition frame. Compose-free on purpose: the
 * framework describes *what* to draw; the timeline UI (a later, protected-file phase) maps
 * this onto `graphicsLayer` / a shared GL renderer. This is the decoupled successor to the
 * inline `PreviewTransitionLayerState` in TimelineScreen.
 *
 * Colors are ARGB ints to avoid a Compose dependency in core.
 */
data class PreviewLayerState(
    val alpha: Float = 1f,
    val scale: Float = 1f,
    val translationXPx: Float = 0f,
    val translationYPx: Float = 0f,
    val rotationXDeg: Float = 0f,
    val rotationYDeg: Float = 0f,
    val rotationZDeg: Float = 0f
)

/**
 * Full preview state for a transition at a given progress: the outgoing (A) and incoming (B)
 * layers plus an optional full-frame color wash (dip/flash). Mirrors the shape the existing
 * preview produces, but lives in the framework so preview and export share one definition.
 */
data class PreviewTransitionState(
    val outgoing: PreviewLayerState = PreviewLayerState(),
    val incoming: PreviewLayerState = PreviewLayerState(alpha = 0f),
    val overlayColorArgb: Int? = null,
    val overlayAlpha: Float = 0f
)

/**
 * Preview strategy for a transition family — produces the on-canvas visual that must MATCH
 * the exported result. Driven by the same [TransitionDescriptor] (easing, params) as the
 * export [TransitionRenderer], which is how parity becomes structural rather than
 * hand-maintained.
 *
 * @param progress linear boundary progress in [0,1]; the implementation applies the
 *                 descriptor's [Easing] itself so preview and export ease identically.
 *
 * For non-exportable transitions, the registered implementation should return the neutral
 * state (plain cut) to keep the preview honest about what export will actually bake.
 *
 * Phase A: interface only. The current `previewTransitionVisualState()` in TimelineScreen is
 * untouched and remains the live preview source until migration.
 */
fun interface PreviewRenderer {
    fun stateFor(
        progress: Float,
        widthPx: Float,
        heightPx: Float,
        params: Map<String, String>
    ): PreviewTransitionState

    companion object {
        /** Neutral (plain-cut) preview — honest default for non-exportable transitions. */
        val PlainCut: PreviewRenderer = PreviewRenderer { _, _, _, _ -> PreviewTransitionState() }
    }
}
