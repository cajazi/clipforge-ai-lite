package com.clipforge.ai.core.gl

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pure JVM tests for the BOUNCE easing curve. No GL/Android — exercises [BounceEasing] only.
 */
class BounceEasingTest {

    private val eps = 1e-3f

    @Test
    fun scale_starts_at_scaleStart() {
        assertEquals(BounceEasing.SCALE_START, BounceEasing.scale(0f), eps)
    }

    @Test
    fun scale_settles_exactly_at_scaleEnd_at_t1() {
        // easeOutBack(1) == 1, so scale(1) must be exactly SCALE_END (no residual overshoot).
        assertEquals(BounceEasing.SCALE_END, BounceEasing.scale(1f), eps)
    }

    @Test
    fun scale_overshoots_above_scaleEnd_before_settling() {
        // The elastic bounce must exceed 1.0 somewhere in the late phase.
        val maxScale = (0..100).map { BounceEasing.scale(it / 100f) }.max()
        assertTrue("expected overshoot above ${BounceEasing.SCALE_END}, got $maxScale", maxScale > BounceEasing.SCALE_END)
    }

    @Test
    fun scale_is_clamped_outside_unit_range() {
        assertEquals(BounceEasing.SCALE_START, BounceEasing.scale(-0.5f), eps)
        assertEquals(BounceEasing.SCALE_END, BounceEasing.scale(1.5f), eps)
    }

    @Test
    fun alpha_fades_0_to_1_monotonically() {
        assertEquals(0f, BounceEasing.alpha(0f), eps)
        assertEquals(1f, BounceEasing.alpha(1f), eps)
        var prev = -1f
        for (i in 0..100) {
            val a = BounceEasing.alpha(i / 100f)
            assertTrue("alpha must be in [0,1], got $a", a in 0f..1f)
            assertTrue("alpha must be non-decreasing", a >= prev - eps)
            prev = a
        }
    }

    @Test
    fun easeOutBack_endpoints() {
        assertEquals(0f, BounceEasing.easeOutBack(0f), eps)
        assertEquals(1f, BounceEasing.easeOutBack(1f), eps)
    }
}
