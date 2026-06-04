package com.clipforge.ai.core.gl

import androidx.media3.common.OverlaySettings
import androidx.media3.common.VideoCompositorSettings
import androidx.media3.common.util.Size
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.StaticOverlaySettings

/**
 * STEP 2 (Option A) - crossfade compositor.
 *
 * A Composition can hold multiple EditedMediaItemSequences that are composited
 * together. This VideoCompositorSettings decides, per output frame, how each
 * input sequence is blended via its alpha.
 *
 * Layout for a 2-clip crossfade:
 *   - inputId 0 = BACKGROUND sequence = clip A (plays first, full screen)
 *   - inputId 1 = OVERLAY sequence    = clip B, gap-padded so its visible
 *                 content begins at (clipADurationUs - crossfadeDurationUs),
 *                 i.e. it overlaps the tail of A.
 *
 * During the overlap window [fadeStartUs, fadeEndUs]:
 *   - clip B (overlay) alpha ramps 0 -> 1
 *   - clip A shows through underneath as B fades in
 * That cross-dissolve IS the crossfade, baked into the export.
 *
 * presentationTimeUs passed to getOverlaySettings is the COMPOSITION timeline
 * time (microseconds from the start of the whole output).
 *
 * APIs confirmed against the resolved media3 1.9.0 jars:
 *   - VideoCompositorSettings (now in androidx.media3.common): getOverlaySettings(int, long)
 *   - OverlaySettings (interface in common): getAlphaScale()
 *   - StaticOverlaySettings.Builder.setAlphaScale(float) (in androidx.media3.effect)
 */
@UnstableApi
class CrossfadeCompositorSettings(
    private val fadeStartUs: Long,
    private val fadeEndUs: Long
) : VideoCompositorSettings {

    private val backgroundOpaque: OverlaySettings =
        StaticOverlaySettings.Builder().setAlphaScale(1f).build()

    init {
        require(fadeEndUs > fadeStartUs) { "fadeEndUs must be > fadeStartUs" }
    }

    // Output size = size of the first (background) input. DEFAULT picks the first input's size.
    override fun getOutputSize(inputSizes: MutableList<Size>): Size =
        VideoCompositorSettings.DEFAULT.getOutputSize(inputSizes)

    override fun getOverlaySettings(inputId: Int, presentationTimeUs: Long): OverlaySettings {
        // inputId 0 = background (clip A): always fully opaque.
        if (inputId == 0) return backgroundOpaque

        // inputId 1 = overlay (clip B): alpha ramps 0 -> 1 across the fade window.
        val alpha: Float = when {
            presentationTimeUs <= fadeStartUs -> 0f
            presentationTimeUs >= fadeEndUs -> 1f
            else -> {
                val span = (fadeEndUs - fadeStartUs).toFloat()
                ((presentationTimeUs - fadeStartUs).toFloat() / span).coerceIn(0f, 1f)
            }
        }
        return StaticOverlaySettings.Builder().setAlphaScale(alpha).build()
    }
}
