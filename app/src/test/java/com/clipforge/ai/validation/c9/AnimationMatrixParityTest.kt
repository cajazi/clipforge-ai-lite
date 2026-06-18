package com.clipforge.ai.validation.c9

import com.clipforge.ai.core.animation.AnimationEffectId
import com.clipforge.ai.core.animation.AnimationPresets
import com.clipforge.ai.core.animation.AnimationPresetType
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.core.animation.AnimationTargetType
import com.clipforge.ai.core.animation.AnimationTrackMapper
import com.clipforge.ai.core.animation.AnimationWindowResolver
import com.clipforge.ai.core.animation.TransformMath
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectExportPolicy
import com.clipforge.ai.core.effects.EffectExportStage
import com.clipforge.ai.core.effects.EffectRegistry
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.KeyframeEasing
import com.clipforge.ai.core.effects.registerTransformAnimationEffect
import com.clipforge.ai.core.overlay.TimePiece
import com.clipforge.ai.core.overlay.TimelineToCompositionTimeMap
import com.clipforge.ai.core.player.EffectPreviewPlan
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * C9.0 validation contract A: preview provider vs export provider must resolve to the
 * byte-equivalent (within epsilon) [TransformMath] matrix + opacity at every sampled instant.
 *
 * Oracles: [EffectPreviewPlan] (preview) and [EffectExportStage] (export) — the existing,
 * unmodified C8 sources of truth. This test only observes their outputs; it does not alter
 * renderer, export, or animation-resolution behavior.
 */
class AnimationMatrixParityTest {

    private val registry = EffectRegistry().apply { registerTransformAnimationEffect() }

    @Test
    fun `preset animations preserve preview-export matrix and opacity parity`() {
        AnimationPresets.all.forEach { preset ->
            val case = ParityCase(
                clipId = "clip-${preset.id}",
                role = roleFor(preset.presetType),
                incomingTransitionMs = 100L,
                outgoingTransitionMs = 150L,
                requestedDurationMs = preset.defaultDurationUs / 1_000L,
                params = AnimationTrackMapper.toParams(preset.toTrack(AnimationTargetType.CLIP))
            )
            assertParity(case)
        }
    }

    @Test
    fun `full easing coverage including overshoot curves preserves matrix and opacity parity`() {
        val case = ParityCase(
            clipId = "clip-easing-coverage",
            role = AnimationRole.COMBO,
            incomingTransitionMs = 80L,
            outgoingTransitionMs = 120L,
            requestedDurationMs = 600L,
            params = mapOf(
                AnimationPropertyKeys.ROTATION to EffectParamValue.Keyframed(
                    listOf(
                        Keyframe(0L, 0f, KeyframeEasing.LINEAR),
                        Keyframe(100_000L, 8f, KeyframeEasing.SMOOTHSTEP),
                        Keyframe(200_000L, -8f, KeyframeEasing.CUBIC_IN),
                        Keyframe(300_000L, 12f, KeyframeEasing.CUBIC_OUT),
                        Keyframe(400_000L, -12f, KeyframeEasing.BACK_OUT),
                        Keyframe(500_000L, 6f, KeyframeEasing.ELASTIC_OUT),
                        Keyframe(600_000L, 0f, KeyframeEasing.BOUNCE_OUT)
                    )
                ),
                AnimationPropertyKeys.OPACITY to EffectParamValue.Keyframed(
                    listOf(
                        Keyframe(0L, 0f, KeyframeEasing.CUBIC_IN_OUT),
                        Keyframe(600_000L, 1f)
                    )
                ),
                AnimationPropertyKeys.SCALE_X to EffectParamValue.Keyframed(
                    listOf(
                        Keyframe(0L, 0.4f, KeyframeEasing.BACK_OUT),
                        Keyframe(300_000L, 1.3f, KeyframeEasing.ELASTIC_OUT),
                        Keyframe(600_000L, 1f, KeyframeEasing.BOUNCE_OUT)
                    )
                )
            )
        )
        assertParity(case)
    }

    @Test
    fun `constant-only animation preserves matrix and opacity parity`() {
        val case = ParityCase(
            clipId = "clip-constant",
            role = AnimationRole.IN,
            incomingTransitionMs = 0L,
            outgoingTransitionMs = 0L,
            requestedDurationMs = 400L,
            params = mapOf(
                AnimationPropertyKeys.POSITION_X to EffectParamValue.Constant(0.2f),
                AnimationPropertyKeys.POSITION_Y to EffectParamValue.Constant(-0.15f),
                AnimationPropertyKeys.SCALE_X to EffectParamValue.Constant(0.6f),
                AnimationPropertyKeys.SCALE_Y to EffectParamValue.Constant(1.4f),
                AnimationPropertyKeys.ROTATION to EffectParamValue.Constant(27f),
                AnimationPropertyKeys.OPACITY to EffectParamValue.Constant(0.42f),
                AnimationPropertyKeys.ANCHOR_X to EffectParamValue.Constant(0.3f),
                AnimationPropertyKeys.ANCHOR_Y to EffectParamValue.Constant(0.7f)
            )
        )
        assertParity(case)
    }

    @Test
    fun `out role animation preserves matrix and opacity parity`() {
        val case = ParityCase(
            clipId = "clip-out",
            role = AnimationRole.OUT,
            incomingTransitionMs = 50L,
            outgoingTransitionMs = 50L,
            requestedDurationMs = 300L,
            params = mapOf(
                AnimationPropertyKeys.POSITION_X to EffectParamValue.Keyframed(
                    listOf(Keyframe(0L, 0f, KeyframeEasing.CUBIC_IN), Keyframe(300_000L, 1.5f))
                ),
                AnimationPropertyKeys.OPACITY to EffectParamValue.Keyframed(
                    listOf(Keyframe(0L, 1f, KeyframeEasing.SMOOTHSTEP), Keyframe(300_000L, 0f))
                )
            )
        )
        assertParity(case)
    }

    private data class ParityCase(
        val clipId: String,
        val role: AnimationRole,
        val incomingTransitionMs: Long,
        val outgoingTransitionMs: Long,
        val requestedDurationMs: Long,
        val params: Map<String, EffectParamValue>
    )

    private fun roleFor(type: AnimationPresetType): AnimationRole = when (type) {
        AnimationPresetType.IN -> AnimationRole.IN
        AnimationPresetType.OUT -> AnimationRole.OUT
        AnimationPresetType.COMBO, AnimationPresetType.LOOP -> AnimationRole.COMBO
    }

    private fun assertParity(case: ParityCase) {
        val clipStartMs = 0L
        val clipEndMs = case.incomingTransitionMs + case.requestedDurationMs + case.outgoingTransitionMs

        val window = requireNotNull(
            AnimationWindowResolver.resolve(
                clipStartMs = clipStartMs,
                clipEndMs = clipEndMs,
                requestedDurationMs = case.requestedDurationMs,
                role = case.role,
                incomingTransitionDurationMs = case.incomingTransitionMs,
                outgoingTransitionDurationMs = case.outgoingTransitionMs
            )
        ) { "window must resolve for ${case.clipId}" }

        val item = EffectItem(
            id = AnimationEffectId.of(case.clipId, case.role),
            projectId = PROJECT_ID,
            effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
            scope = EffectScope.CLIP,
            startMs = window.startMs,
            endMs = window.endMs,
            zOrder = 0,
            params = case.params
        )

        val previewResult = EffectPreviewPlan.build(effects = listOf(item), registry = registry)
        val map = TimelineToCompositionTimeMap.build(listOf(TimePiece(clipEndMs, clipEndMs)))
        val exportResult = EffectExportStage.build(
            effects = listOf(item),
            registry = registry,
            map = map,
            releasePolicy = EffectExportPolicy.current
        )

        val previewAttachment = previewResult.attachments.single()
        val exportAttachment = exportResult.attachments.single()
        assertEquals("${case.clipId} windowStartUs", previewAttachment.windowStartUs, exportAttachment.windowStartUs)
        assertEquals("${case.clipId} windowEndUs", previewAttachment.windowEndUs, exportAttachment.windowEndUs)

        val mandatorySamples = setOf(
            previewAttachment.windowStartUs,
            previewAttachment.windowEndUs - 1L,
            (clipStartMs + case.incomingTransitionMs) * 1_000L,
            (clipEndMs - case.outgoingTransitionMs) * 1_000L - 1L
        )
        val samples = sampleTimestampsUs(previewAttachment.windowStartUs, previewAttachment.windowEndUs, mandatorySamples)
        check(samples.isNotEmpty()) { "${case.clipId} must produce at least one sample" }

        samples.forEach { timeUs ->
            val previewValues = TransformMath.resolveValues(
                timeUs, previewAttachment.windowStartUs, previewAttachment.windowEndUs, previewAttachment.provider
            )
            val exportValues = TransformMath.resolveValues(
                timeUs, exportAttachment.windowStartUs, exportAttachment.windowEndUs, exportAttachment.provider
            )

            val previewMatrix = TransformMath.composeMatrix(previewValues, ASPECT)
            val exportMatrix = TransformMath.composeMatrix(exportValues, ASPECT)

            val label = "${case.clipId} @${timeUs}us"
            assertEquals("$label m00", previewMatrix.m00, exportMatrix.m00, MATRIX_EPSILON)
            assertEquals("$label m01", previewMatrix.m01, exportMatrix.m01, MATRIX_EPSILON)
            assertEquals("$label m02", previewMatrix.m02, exportMatrix.m02, MATRIX_EPSILON)
            assertEquals("$label m10", previewMatrix.m10, exportMatrix.m10, MATRIX_EPSILON)
            assertEquals("$label m11", previewMatrix.m11, exportMatrix.m11, MATRIX_EPSILON)
            assertEquals("$label m12", previewMatrix.m12, exportMatrix.m12, MATRIX_EPSILON)
            assertEquals(
                "$label opacity",
                TransformMath.opacityOf(previewValues),
                TransformMath.opacityOf(exportValues),
                OPACITY_EPSILON
            )
        }
    }

    /**
     * Sampling rules (C9.0 spec): every frame for animations < 500ms, otherwise max interval
     * 16ms, plus mandatory boundary samples (window start/end, clean-span/transition edges).
     */
    private fun sampleTimestampsUs(windowStartUs: Long, windowEndUs: Long, mandatory: Set<Long>): List<Long> {
        if (windowStartUs >= windowEndUs) return emptyList()
        val durationUs = windowEndUs - windowStartUs
        val stepUs = if (durationUs < 500_000L) FINE_STEP_US else MAX_STEP_US

        val samples = sortedSetOf<Long>()
        var t = windowStartUs
        while (t < windowEndUs) {
            samples += t
            t += stepUs
        }
        samples += windowEndUs - 1L
        mandatory.forEach { if (it in windowStartUs until windowEndUs) samples += it }
        return samples.toList()
    }

    private companion object {
        const val PROJECT_ID = "c9-matrix-parity"
        const val MATRIX_EPSILON = 1e-4f
        const val OPACITY_EPSILON = 1e-4f
        const val ASPECT = 9f / 16f
        const val FINE_STEP_US = 8_000L
        const val MAX_STEP_US = 16_000L
    }
}
