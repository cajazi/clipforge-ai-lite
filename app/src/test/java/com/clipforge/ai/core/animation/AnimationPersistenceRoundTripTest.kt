package com.clipforge.ai.core.animation

import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.KeyframeEasing
import com.clipforge.ai.data.repository.EffectParamsCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationPersistenceRoundTripTest {

    @Test
    fun `codec round trip restores animation track`() {
        val track = easingCoverageTrack()

        val restored = track.roundTripThroughCodec()

        assertTrackEquals(track, restored)
        assertValid(track)
        assertValid(restored)
    }

    @Test
    fun `all animation presets survive persistence round trip`() {
        val presetIds = listOf(
            AnimationPresetIds.FADE_IN,
            AnimationPresetIds.ZOOM_IN,
            AnimationPresetIds.SLIDE_IN_LEFT,
            AnimationPresetIds.FADE_OUT,
            AnimationPresetIds.ZOOM_OUT,
            AnimationPresetIds.SLIDE_OUT_RIGHT,
            AnimationPresetIds.SLOW_ZOOM,
            AnimationPresetIds.PULSE,
            AnimationPresetIds.SWAY
        )

        assertEquals(presetIds, AnimationPresets.all.map { it.id })
        AnimationPresets.all.forEach { preset ->
            val track = preset.toTrack(AnimationTargetType.CLIP)
            val restored = track.roundTripThroughCodec()

            assertTrackEquals(track, restored)
            assertValid(track)
            assertValid(restored)
        }
    }

    @Test
    fun `easing values survive persistence round trip`() {
        val restored = easingCoverageTrack().roundTripThroughCodec()
        val easings = restored.properties.single().keyframes.map { it.easing }

        assertEquals(
            listOf(
                KeyframeEasing.LINEAR,
                KeyframeEasing.SMOOTHSTEP,
                KeyframeEasing.CUBIC_IN,
                KeyframeEasing.CUBIC_OUT,
                KeyframeEasing.BACK_OUT,
                KeyframeEasing.ELASTIC_OUT
            ),
            easings
        )
    }

    private fun AnimationTrack.roundTripThroughCodec(): AnimationTrack {
        val params = AnimationTrackMapper.toParams(this)
        val encoded = EffectParamsCodec.encode(params)
        val decoded = EffectParamsCodec.decode(encoded)
        return AnimationTrackMapper.toTrack(
            params = decoded,
            targetType = targetType,
            presetType = presetType
        )
    }

    private fun easingCoverageTrack() = AnimationTrack(
        targetType = AnimationTargetType.CLIP,
        presetType = AnimationPresetType.COMBO,
        properties = listOf(
            AnimationProperty(
                AnimationPropertyKeys.ROTATION,
                listOf(
                    Keyframe(0L, 0f, KeyframeEasing.LINEAR),
                    Keyframe(100_000L, 3f, KeyframeEasing.SMOOTHSTEP),
                    Keyframe(200_000L, -3f, KeyframeEasing.CUBIC_IN),
                    Keyframe(300_000L, 6f, KeyframeEasing.CUBIC_OUT),
                    Keyframe(400_000L, -6f, KeyframeEasing.BACK_OUT),
                    Keyframe(500_000L, 0f, KeyframeEasing.ELASTIC_OUT)
                )
            )
        )
    )

    private fun assertValid(track: AnimationTrack) {
        assertTrue(AnimationValidation.validateTrack(track).isValid)
    }

    private fun assertTrackEquals(expected: AnimationTrack, actual: AnimationTrack) {
        assertEquals(expected.targetType, actual.targetType)
        assertEquals(expected.presetType, actual.presetType)
        assertEquals(
            expected.properties.sortedBy { it.key },
            actual.properties.sortedBy { it.key }
        )
    }
}
