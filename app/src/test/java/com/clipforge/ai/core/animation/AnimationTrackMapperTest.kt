package com.clipforge.ai.core.animation

import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.KeyframeEasing
import com.clipforge.ai.domain.model.EffectParamValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationTrackMapperTest {

    @Test
    fun `track maps to params and back without losing keyframes or easing`() {
        val track = sampleTrack(AnimationPresetType.COMBO)

        val params = AnimationTrackMapper.toParams(track)
        val restored = AnimationTrackMapper.toTrack(
            params = params,
            targetType = track.targetType,
            presetType = track.presetType
        )

        assertEquals(track, restored)
    }

    @Test
    fun `mapper persists keyframes only`() {
        val params = AnimationTrackMapper.toParams(sampleTrack())

        assertTrue(params.values.all { it is EffectParamValue.Keyframed })
    }

    @Test
    fun `mapper rejects constant params when reconstructing track`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnimationTrackMapper.toTrack(
                params = mapOf(AnimationPropertyKeys.OPACITY to EffectParamValue.Constant(1f)),
                targetType = AnimationTargetType.CLIP
            )
        }
    }

    @Test
    fun `mapper rejects invalid tracks before persistence`() {
        val track = AnimationTrack(
            targetType = AnimationTargetType.CLIP,
            properties = listOf(
                AnimationProperty("unknown", listOf(Keyframe(0L, 0f), Keyframe(100L, 1f)))
            )
        )

        assertThrows(IllegalArgumentException::class.java) {
            AnimationTrackMapper.toParams(track)
        }
    }

    @Test
    fun `factory creates global transform animation effect item`() {
        val track = sampleTrack()

        val item = AnimationEffectItemFactory.createTransformAnimationEffectItem(
            id = "anim-1",
            projectId = "project",
            track = track,
            startMs = 100L,
            endMs = 900L,
            zOrder = 4
        )

        assertEquals("transform_animation", item.effectId)
        assertEquals(EffectScope.GLOBAL, item.scope)
        assertEquals(AnimationTrackMapper.toParams(track), item.params)
        assertEquals(4, item.zOrder)
    }

    @Test
    fun `factory creates clip transform animation effect item`() {
        val item = AnimationEffectItemFactory.createTransformAnimationEffectItem(
            id = AnimationEffectId.of("clip-1", AnimationRole.IN),
            projectId = "project",
            track = sampleTrack(),
            startMs = 0L,
            endMs = 1_000L,
            scope = EffectScope.CLIP
        )

        assertEquals("transform_animation", item.effectId)
        assertEquals(EffectScope.CLIP, item.scope)
    }

    @Test
    fun `factory rejects empty or reversed window`() {
        assertThrows(IllegalArgumentException::class.java) {
            AnimationEffectItemFactory.createTransformAnimationEffectItem(
                id = "anim-1",
                projectId = "project",
                track = sampleTrack(),
                startMs = 1_000L,
                endMs = 1_000L
            )
        }
    }

    private fun sampleTrack(
        presetType: AnimationPresetType? = AnimationPresetType.IN
    ) = AnimationTrack(
        targetType = AnimationTargetType.CLIP,
        presetType = presetType,
        properties = listOf(
            AnimationProperty(
                AnimationPropertyKeys.SCALE_X,
                listOf(
                    Keyframe(0L, 0.8f, KeyframeEasing.BACK_OUT),
                    Keyframe(500_000L, 1f, KeyframeEasing.CUBIC_OUT)
                )
            ),
            AnimationProperty(
                AnimationPropertyKeys.OPACITY,
                listOf(
                    Keyframe(0L, 0f, KeyframeEasing.SMOOTHSTEP),
                    Keyframe(500_000L, 1f, KeyframeEasing.LINEAR)
                )
            )
        )
    )
}
