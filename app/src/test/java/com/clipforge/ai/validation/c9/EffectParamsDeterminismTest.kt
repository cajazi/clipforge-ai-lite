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
 * C9.0 validation: same inputs must produce byte-identical outputs. Exercises the
 * forbidden-to-modify [EffectParamsCodec] (encode determinism, idempotent re-encode) and
 * [EffectParamResolver] + [TransformMath] (pure-function determinism: identical inputs resolved
 * twice must compose to exactly equal matrices/opacity, not merely within an epsilon).
 */
class EffectParamsDeterminismTest {

    private val registry = EffectRegistry().apply { registerTransformAnimationEffect() }
    private val specs = requireNotNull(registry.get(AnimationEffectRegistrations.TRANSFORM_ANIMATION))
        .descriptor.paramSpecs

    @Test
    fun `encode is byte-identical regardless of map insertion order`() {
        val ordered = linkedMapOf(
            AnimationPropertyKeys.OPACITY to EffectParamValue.Constant(0.5f),
            AnimationPropertyKeys.ROTATION to EffectParamValue.Constant(12f),
            AnimationPropertyKeys.SCALE_X to EffectParamValue.Keyframed(
                listOf(Keyframe(0L, 1f), Keyframe(200_000L, 1.5f, KeyframeEasing.CUBIC_OUT))
            )
        )
        val reversed = linkedMapOf(
            AnimationPropertyKeys.SCALE_X to EffectParamValue.Keyframed(
                listOf(Keyframe(0L, 1f), Keyframe(200_000L, 1.5f, KeyframeEasing.CUBIC_OUT))
            ),
            AnimationPropertyKeys.ROTATION to EffectParamValue.Constant(12f),
            AnimationPropertyKeys.OPACITY to EffectParamValue.Constant(0.5f)
        )

        assertEquals(EffectParamsCodec.encode(ordered), EffectParamsCodec.encode(reversed))
    }

    @Test
    fun `encode is byte-identical across repeated calls with the same input`() {
        AnimationPresets.all.forEach { preset ->
            val params = AnimationTrackMapper.toParams(preset.toTrack(AnimationTargetType.CLIP))
            val first = EffectParamsCodec.encode(params)
            val second = EffectParamsCodec.encode(params)
            assertEquals("${preset.id}: repeated encode must be byte-identical", first, second)
        }
    }

    @Test
    fun `encode-decode-encode is idempotent`() {
        AnimationPresets.all.forEach { preset ->
            val params = AnimationTrackMapper.toParams(preset.toTrack(AnimationTargetType.CLIP))
            val encodedOnce = EffectParamsCodec.encode(params)
            val reEncoded = EffectParamsCodec.encode(EffectParamsCodec.decode(encodedOnce))
            assertEquals("${preset.id}: re-encode after decode must match", encodedOnce, reEncoded)
        }
    }

    @Test
    fun `resolver and matrix composition are deterministic for preset params`() {
        AnimationPresets.all.forEach { preset ->
            val params = AnimationTrackMapper.toParams(preset.toTrack(AnimationTargetType.CLIP))
            assertResolveDeterministic(itemId = "clip-${preset.id}", durationMs = preset.defaultDurationUs / 1_000L, params = params)
        }
    }

    @Test
    fun `resolver and matrix composition are deterministic for constant params`() {
        val params = mapOf(
            AnimationPropertyKeys.POSITION_X to EffectParamValue.Constant(0.18f),
            AnimationPropertyKeys.SCALE_Y to EffectParamValue.Constant(1.35f),
            AnimationPropertyKeys.ROTATION to EffectParamValue.Constant(-22f),
            AnimationPropertyKeys.OPACITY to EffectParamValue.Constant(0.77f)
        )
        assertResolveDeterministic(itemId = "clip-constant", durationMs = 350L, params = params)
    }

    private fun assertResolveDeterministic(itemId: String, durationMs: Long, params: Map<String, EffectParamValue>) {
        val windowStartUs = 0L
        val windowEndUs = durationMs * 1_000L

        val first = EffectParamResolver.resolve(
            itemId = itemId,
            storedParams = params,
            specs = specs,
            windowStartUs = windowStartUs,
            constantMode = EffectParamResolver.ConstantMode.Snapshot,
            logPrefix = "C9_DETERMINISM_1"
        )
        val second = EffectParamResolver.resolve(
            itemId = itemId,
            storedParams = params,
            specs = specs,
            windowStartUs = windowStartUs,
            constantMode = EffectParamResolver.ConstantMode.Snapshot,
            logPrefix = "C9_DETERMINISM_2"
        )

        assertEquals("$itemId: signature must be deterministic", first.keyframedParamsSignature, second.keyframedParamsSignature)

        var t = windowStartUs
        while (t < windowEndUs) {
            val firstValues = TransformMath.resolveValues(t, windowStartUs, windowEndUs, first.provider)
            val secondValues = TransformMath.resolveValues(t, windowStartUs, windowEndUs, second.provider)
            val firstMatrix = TransformMath.composeMatrix(firstValues, ASPECT)
            val secondMatrix = TransformMath.composeMatrix(secondValues, ASPECT)

            val label = "$itemId @${t}us"
            assertEquals("$label m00", firstMatrix.m00, secondMatrix.m00, 0f)
            assertEquals("$label m01", firstMatrix.m01, secondMatrix.m01, 0f)
            assertEquals("$label m02", firstMatrix.m02, secondMatrix.m02, 0f)
            assertEquals("$label m10", firstMatrix.m10, secondMatrix.m10, 0f)
            assertEquals("$label m11", firstMatrix.m11, secondMatrix.m11, 0f)
            assertEquals("$label m12", firstMatrix.m12, secondMatrix.m12, 0f)
            assertEquals("$label opacity", TransformMath.opacityOf(firstValues), TransformMath.opacityOf(secondValues), 0f)
            t += STEP_US
        }
    }

    private companion object {
        const val ASPECT = 9f / 16f
        const val STEP_US = 16_000L
    }
}
