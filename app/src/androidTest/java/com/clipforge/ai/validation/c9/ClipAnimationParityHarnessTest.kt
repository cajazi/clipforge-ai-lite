package com.clipforge.ai.validation.c9

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.core.animation.AnimationEffectId
import com.clipforge.ai.core.animation.AnimationPresetType
import com.clipforge.ai.core.animation.AnimationPresets
import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.core.animation.AnimationTargetType
import com.clipforge.ai.core.animation.AnimationTrackMapper
import com.clipforge.ai.core.animation.AnimationWindowResolver
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectExportPolicy
import com.clipforge.ai.core.effects.EffectExportStage
import com.clipforge.ai.core.effects.EffectRegistry
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.registerTransformAnimationEffect
import com.clipforge.ai.core.overlay.TimePiece
import com.clipforge.ai.core.overlay.TimelineToCompositionTimeMap
import com.clipforge.ai.core.player.EffectPreviewPlan
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.validation.c9.support.MatrixSampler
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith

/**
 * C9.0 validation contract A, on-device: re-runs the preview-vs-export matrix/opacity parity
 * check from [com.clipforge.ai.validation.c9.AnimationMatrixParityTest] (JVM) on the device's
 * ART/CPU, using [MatrixSampler] over the real [EffectPreviewPlan] / [EffectExportStage] oracles,
 * so device-level float behavior is part of the validation evidence, not just host JVM behavior.
 */
@RunWith(AndroidJUnit4::class)
class ClipAnimationParityHarnessTest {

    private val registry = EffectRegistry().apply { registerTransformAnimationEffect() }

    @Test
    fun preset_animations_preserve_matrix_and_opacity_parity_on_device() {
        AnimationPresets.all.forEach { preset ->
            val role = when (preset.presetType) {
                AnimationPresetType.IN -> AnimationRole.IN
                AnimationPresetType.OUT -> AnimationRole.OUT
                AnimationPresetType.COMBO, AnimationPresetType.LOOP -> AnimationRole.COMBO
            }
            assertParity(
                clipId = "device-${preset.id}",
                role = role,
                incomingTransitionMs = 100L,
                outgoingTransitionMs = 150L,
                requestedDurationMs = preset.defaultDurationUs / 1_000L,
                params = AnimationTrackMapper.toParams(preset.toTrack(AnimationTargetType.CLIP))
            )
        }
    }

    private fun assertParity(
        clipId: String,
        role: AnimationRole,
        incomingTransitionMs: Long,
        outgoingTransitionMs: Long,
        requestedDurationMs: Long,
        params: Map<String, com.clipforge.ai.domain.model.EffectParamValue>
    ) {
        val clipStartMs = 0L
        val clipEndMs = incomingTransitionMs + requestedDurationMs + outgoingTransitionMs

        val window = requireNotNull(
            AnimationWindowResolver.resolve(
                clipStartMs = clipStartMs,
                clipEndMs = clipEndMs,
                requestedDurationMs = requestedDurationMs,
                role = role,
                incomingTransitionDurationMs = incomingTransitionMs,
                outgoingTransitionDurationMs = outgoingTransitionMs
            )
        ) { "window must resolve for $clipId" }

        val item = EffectItem(
            id = AnimationEffectId.of(clipId, role),
            projectId = PROJECT_ID,
            effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
            scope = EffectScope.CLIP,
            startMs = window.startMs,
            endMs = window.endMs,
            zOrder = 0,
            params = params
        )

        val previewAttachment = EffectPreviewPlan.build(listOf(item), registry).attachments.single()
        val map = TimelineToCompositionTimeMap.build(listOf(TimePiece(clipEndMs, clipEndMs)))
        val exportAttachment = EffectExportStage.build(
            effects = listOf(item),
            registry = registry,
            map = map,
            releasePolicy = EffectExportPolicy.current
        ).attachments.single()

        assertEquals("$clipId windowStartUs", previewAttachment.windowStartUs, exportAttachment.windowStartUs)
        assertEquals("$clipId windowEndUs", previewAttachment.windowEndUs, exportAttachment.windowEndUs)

        val previewSamples = MatrixSampler.sample(
            previewAttachment.provider, previewAttachment.windowStartUs, previewAttachment.windowEndUs, ASPECT
        )
        val exportSamples = MatrixSampler.sample(
            exportAttachment.provider, exportAttachment.windowStartUs, exportAttachment.windowEndUs, ASPECT
        )

        assertEquals("$clipId sample count", previewSamples.size, exportSamples.size)
        previewSamples.zip(exportSamples).forEach { (previewSample, exportSample) ->
            val delta = MatrixSampler.matrixDelta(previewSample.matrix, exportSample.matrix)
            val opacityDelta = kotlin.math.abs(previewSample.opacity - exportSample.opacity)
            Log.d(
                TAG,
                "C9_PARITY clip=$clipId t=${previewSample.timeUs} matrixDelta=$delta opacityDelta=$opacityDelta"
            )
            assertEquals("$clipId @${previewSample.timeUs}us matrix", 0f, delta, MATRIX_EPSILON)
            assertEquals("$clipId @${previewSample.timeUs}us opacity", 0f, opacityDelta, OPACITY_EPSILON)
        }
    }

    private companion object {
        const val TAG = "C9_ParityHarness"
        const val PROJECT_ID = "c9-device-matrix-parity"
        const val MATRIX_EPSILON = 1e-4f
        const val OPACITY_EPSILON = 1e-4f
        const val ASPECT = 9f / 16f
    }
}
