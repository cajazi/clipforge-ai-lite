package com.clipforge.ai.validation.c9

import com.clipforge.ai.core.animation.AnimationPresets
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.animation.AnimationTargetType
import com.clipforge.ai.core.animation.AnimationTrackMapper
import com.clipforge.ai.core.animation.TransformMath
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectParamResolver
import com.clipforge.ai.core.effects.EffectRegistry
import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.KeyframeEasing
import com.clipforge.ai.core.effects.registerTransformAnimationEffect
import com.clipforge.ai.data.repository.EffectParamsCodec
import com.clipforge.ai.domain.model.EffectParamValue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * C9.0 validation: persisted == exported, restated as "persist -> load -> resolve -> matrix"
 * determinism. Goes one step further than [com.clipforge.ai.core.animation.AnimationPersistenceRoundTripTest]
 * (which only proves the codec/track round trip is value-equal): this proves the *resolved*
 * [TransformMath] matrix and opacity produced from params before persistence are identical
 * (within epsilon) to those produced from params decoded after a codec round trip.
 *
 * Oracles: [EffectParamsCodec] and [EffectParamResolver] (both forbidden-to-modify / read-only
 * sources of truth) plus [TransformMath] (forbidden to modify).
 */
class AnimationPersistenceRoundTripTest {

    private val registry = EffectRegistry().apply { registerTransformAnimationEffect() }
    private val specs = requireNotNull(registry.get(AnimationEffectRegistrations.TRANSFORM_ANIMATION))
        .descriptor.paramSpecs

    @Test
    fun `preset params resolve to identical matrix and opacity after persistence round trip`() {
        AnimationPresets.all.forEach { preset ->
            assertRoundTripParity(
                itemId = "clip-${preset.id}",
                durationMs = preset.defaultDurationUs / 1_000L,
                params = AnimationTrackMapper.toParams(preset.toTrack(AnimationTargetType.CLIP))
            )
        }
    }

    @Test
    fun `full easing coverage resolves to identical matrix and opacity after persistence round trip`() {
        assertRoundTripParity(
            itemId = "clip-easing-coverage",
            durationMs = 600L,
            params = mapOf(
                AnimationPropertyKeys.ROTATION to EffectParamValue.Keyframed(
                    listOf(
                        Keyframe(0L, 0f, KeyframeEasing.LINEAR),
                        Keyframe(150_000L, 10f, KeyframeEasing.SMOOTHSTEP),
                        Keyframe(300_000L, -10f, KeyframeEasing.CUBIC_IN_OUT),
                        Keyframe(450_000L, 14f, KeyframeEasing.BACK_OUT),
                        Keyframe(600_000L, 0f, KeyframeEasing.BOUNCE_OUT)
                    )
                ),
                AnimationPropertyKeys.SCALE_X to EffectParamValue.Keyframed(
                    listOf(
                        Keyframe(0L, 0.5f, KeyframeEasing.ELASTIC_OUT),
                        Keyframe(600_000L, 1f, KeyframeEasing.CUBIC_OUT)
                    )
                ),
                AnimationPropertyKeys.OPACITY to EffectParamValue.Keyframed(
                    listOf(Keyframe(0L, 0f), Keyframe(600_000L, 1f))
                )
            )
        )
    }

    @Test
    fun `constant params resolve to identical matrix and opacity after persistence round trip`() {
        assertRoundTripParity(
            itemId = "clip-constant",
            durationMs = 400L,
            params = mapOf(
                AnimationPropertyKeys.POSITION_X to EffectParamValue.Constant(0.33f),
                AnimationPropertyKeys.POSITION_Y to EffectParamValue.Constant(-0.21f),
                AnimationPropertyKeys.SCALE_X to EffectParamValue.Constant(1.25f),
                AnimationPropertyKeys.SCALE_Y to EffectParamValue.Constant(0.8f),
                AnimationPropertyKeys.ROTATION to EffectParamValue.Constant(-15f),
                AnimationPropertyKeys.OPACITY to EffectParamValue.Constant(0.6f)
            )
        )
    }

    private fun assertRoundTripParity(itemId: String, durationMs: Long, params: Map<String, EffectParamValue>) {
        val windowStartUs = 0L
        val windowEndUs = durationMs * 1_000L

        val beforePersist = EffectParamResolver.resolve(
            itemId = itemId,
            storedParams = params,
            specs = specs,
            windowStartUs = windowStartUs,
            constantMode = EffectParamResolver.ConstantMode.Snapshot,
            logPrefix = "C9_PERSIST_BEFORE"
        )

        val decoded = EffectParamsCodec.decode(EffectParamsCodec.encode(params))
        val afterPersist = EffectParamResolver.resolve(
            itemId = itemId,
            storedParams = decoded,
            specs = specs,
            windowStartUs = windowStartUs,
            constantMode = EffectParamResolver.ConstantMode.Snapshot,
            logPrefix = "C9_PERSIST_AFTER"
        )

        assertEquals(
            "$itemId: keyframed signature must survive persistence",
            beforePersist.keyframedParamsSignature,
            afterPersist.keyframedParamsSignature
        )

        sampleTimestampsUs(windowStartUs, windowEndUs).forEach { timeUs ->
            val beforeValues = TransformMath.resolveValues(timeUs, windowStartUs, windowEndUs, beforePersist.provider)
            val afterValues = TransformMath.resolveValues(timeUs, windowStartUs, windowEndUs, afterPersist.provider)

            val beforeMatrix = TransformMath.composeMatrix(beforeValues, ASPECT)
            val afterMatrix = TransformMath.composeMatrix(afterValues, ASPECT)

            val label = "$itemId @${timeUs}us"
            assertEquals("$label m00", beforeMatrix.m00, afterMatrix.m00, EPSILON)
            assertEquals("$label m01", beforeMatrix.m01, afterMatrix.m01, EPSILON)
            assertEquals("$label m02", beforeMatrix.m02, afterMatrix.m02, EPSILON)
            assertEquals("$label m10", beforeMatrix.m10, afterMatrix.m10, EPSILON)
            assertEquals("$label m11", beforeMatrix.m11, afterMatrix.m11, EPSILON)
            assertEquals("$label m12", beforeMatrix.m12, afterMatrix.m12, EPSILON)
            assertEquals(
                "$label opacity",
                TransformMath.opacityOf(beforeValues),
                TransformMath.opacityOf(afterValues),
                EPSILON
            )
        }
    }

    private fun sampleTimestampsUs(windowStartUs: Long, windowEndUs: Long): List<Long> {
        if (windowStartUs >= windowEndUs) return listOf(windowStartUs)
        val samples = sortedSetOf<Long>()
        var t = windowStartUs
        while (t < windowEndUs) {
            samples += t
            t += STEP_US
        }
        samples += windowEndUs - 1L
        return samples.toList()
    }

    private companion object {
        const val EPSILON = 1e-4f
        const val ASPECT = 9f / 16f
        const val STEP_US = 16_000L
    }
}
