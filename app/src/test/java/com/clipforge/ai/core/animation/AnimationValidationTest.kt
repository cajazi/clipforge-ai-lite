package com.clipforge.ai.core.animation

import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.KeyframeEasing
import com.clipforge.ai.core.effects.KeyframeSampler
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationValidationTest {

    private val eps = 1e-4f

    private fun kf(t: Long, v: Float, e: KeyframeEasing = KeyframeEasing.LINEAR) = Keyframe(t, v, e)

    @Test
    fun valid_property_key_accepted() {
        assertTrue(AnimationValidation.isValidPropertyKey("scale"))
    }

    @Test
    fun invalid_property_key_rejected() {
        assertFalse(AnimationValidation.isValidPropertyKey("vignette"))
    }

    @Test
    fun valid_track_is_valid() {
        val track = AnimationTrack(
            targetType = AnimationTargetType.CLIP,
            properties = listOf(
                AnimationProperty("scale", listOf(kf(0, 0.8f, KeyframeEasing.BACK_OUT), kf(500_000, 1.0f))),
                AnimationProperty("opacity", listOf(kf(0, 0f), kf(500_000, 1f)))
            ),
            presetType = AnimationPresetType.IN
        )
        assertTrue(AnimationValidation.validateTrack(track).isValid)
    }

    @Test
    fun unknown_key_track_is_invalid() {
        val track = AnimationTrack(
            AnimationTargetType.CLIP,
            listOf(AnimationProperty("wobble", listOf(kf(0, 0f), kf(100, 1f))))
        )
        assertFalse(AnimationValidation.validateTrack(track).isValid)
    }

    @Test
    fun duplicate_property_keys_rejected() {
        val track = AnimationTrack(
            AnimationTargetType.IMAGE,
            listOf(
                AnimationProperty("scale", listOf(kf(0, 1f), kf(100, 1f))),
                AnimationProperty("scale", listOf(kf(0, 1f), kf(100, 1f)))
            )
        )
        val result = AnimationValidation.validateTrack(track)
        assertFalse(result.isValid)
        assertTrue((result as AnimationValidationResult.Invalid).reasons.any { it.contains("duplicate") })
    }

    @Test
    fun non_increasing_keyframes_rejected() {
        val r = AnimationValidation.validateKeyframeOrdering(listOf(kf(0, 0f), kf(0, 1f)))
        assertFalse(r.isValid)
    }

    @Test
    fun empty_keyframes_rejected() {
        assertFalse(AnimationValidation.validateKeyframeOrdering(emptyList()).isValid)
    }

    @Test
    fun easing_compatibility_all_easings_valid() {
        KeyframeEasing.entries.forEach { e ->
            val prop = AnimationProperty("rotation", listOf(kf(0, 0f, e), kf(100, 90f)))
            assertTrue("$e should be compatible", AnimationValidation.validateEasingCompatibility(prop).isValid)
        }
    }

    // --- Backward compatibility: existing easings unchanged; new easings hit exact endpoints ---

    @Test
    fun linear_and_smoothstep_unchanged() {
        // LINEAR midpoint == raw fraction; SMOOTHSTEP midpoint == 0.5 (t*t*(3-2t) at 0.5).
        val lin = KeyframeSampler.sample(listOf(kf(0, 0f, KeyframeEasing.LINEAR), kf(100, 1f)), 50)
        assertEquals(0.5f, lin, eps)
        val smooth = KeyframeSampler.sample(listOf(kf(0, 0f, KeyframeEasing.SMOOTHSTEP), kf(100, 1f)), 50)
        assertEquals(0.5f, smooth, eps)
        assertEquals(0.25f, KeyframeSampler.applyEasing(KeyframeEasing.SMOOTHSTEP, 0.5f) - 0.25f, eps)
    }

    @Test
    fun every_easing_is_identity_at_endpoints() {
        KeyframeEasing.entries.forEach { e ->
            assertEquals("$e at 0", 0f, KeyframeSampler.applyEasing(e, 0f), eps)
            assertEquals("$e at 1", 1f, KeyframeSampler.applyEasing(e, 1f), eps)
        }
    }

    @Test
    fun overshoot_curves_exceed_unit_range_midway() {
        // BACK_OUT and ELASTIC_OUT must overshoot above 1.0 somewhere in (0,1).
        listOf(KeyframeEasing.BACK_OUT, KeyframeEasing.ELASTIC_OUT).forEach { e ->
            val maxV = (1..99).map { KeyframeSampler.applyEasing(e, it / 100f) }.max()
            assertTrue("$e should overshoot >1, got $maxV", maxV > 1f)
        }
    }

    @Test
    fun cubic_in_is_below_linear_midway() {
        assertTrue(KeyframeSampler.applyEasing(KeyframeEasing.CUBIC_IN, 0.5f) < 0.5f)
        assertTrue(KeyframeSampler.applyEasing(KeyframeEasing.CUBIC_OUT, 0.5f) > 0.5f)
    }
}
