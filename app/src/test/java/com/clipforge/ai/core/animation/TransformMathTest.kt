package com.clipforge.ai.core.animation

import com.clipforge.ai.core.effects.ConstantParams
import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.core.effects.KeyframedParams
import com.clipforge.ai.core.effects.ParamProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Authoritative tests for the transform single-source-of-truth (C8.2). Future slices (C8.3
 * preview, C8.4 export) reuse these rather than reimplementing transform calculations.
 */
class TransformMathTest {

    private val eps = 1e-4f

    /** A provider carrying all 8 transform keys as constants. */
    private fun constants(
        posX: Float = 0f, posY: Float = 0f,
        scaleX: Float = 1f, scaleY: Float = 1f,
        rotation: Float = 0f, opacity: Float = 1f,
        anchorX: Float = 0.5f, anchorY: Float = 0.5f
    ): ParamProvider = ConstantParams(
        mapOf(
            AnimationPropertyKeys.POSITION_X to posX,
            AnimationPropertyKeys.POSITION_Y to posY,
            AnimationPropertyKeys.SCALE_X to scaleX,
            AnimationPropertyKeys.SCALE_Y to scaleY,
            AnimationPropertyKeys.ROTATION to rotation,
            AnimationPropertyKeys.OPACITY to opacity,
            AnimationPropertyKeys.ANCHOR_X to anchorX,
            AnimationPropertyKeys.ANCHOR_Y to anchorY
        )
    )

    private fun assertPoint(expectedX: Float, expectedY: Float, actual: Pair<Float, Float>) {
        assertEquals("x", expectedX, actual.first, eps)
        assertEquals("y", expectedY, actual.second, eps)
    }

    @Test
    fun identity_values_when_provider_is_identity() {
        val v = TransformMath.resolveValues(50, 0, 100, constants())
        assertEquals(TransformMath.TransformValues.IDENTITY, v)
    }

    @Test
    fun identity_matrix_maps_points_unchanged() {
        val m = TransformMath.composeMatrix(TransformMath.TransformValues.IDENTITY, aspect = 1f)
        assertPoint(0.5f, -0.3f, m.apply(0.5f, -0.3f))
        assertPoint(-1f, 1f, m.apply(-1f, 1f))
    }

    @Test
    fun outside_window_returns_identity() {
        val provider = constants(scaleX = 2f, opacity = 0.2f, posX = 0.5f)
        // before window
        assertEquals(TransformMath.TransformValues.IDENTITY, TransformMath.resolveValues(-1, 0, 100, provider))
        // at/after window end (half-open: end is outside)
        assertEquals(TransformMath.TransformValues.IDENTITY, TransformMath.resolveValues(100, 0, 100, provider))
        assertEquals(TransformMath.TransformValues.IDENTITY, TransformMath.resolveValues(200, 0, 100, provider))
    }

    @Test
    fun scale_only_about_centre_pivot() {
        val v = TransformMath.TransformValues(scaleX = 2f, scaleY = 3f)
        val m = TransformMath.composeMatrix(v, aspect = 1f)
        // Centre pivot (0,0 NDC) stays fixed; a corner scales.
        assertPoint(0f, 0f, m.apply(0f, 0f))
        assertPoint(2f, 3f, m.apply(1f, 1f))
    }

    @Test
    fun translation_only_shifts_all_points() {
        val v = TransformMath.TransformValues(positionX = 0.5f, positionY = -0.25f)
        val m = TransformMath.composeMatrix(v, aspect = 1f)
        assertPoint(0.5f, -0.25f, m.apply(0f, 0f))
        assertPoint(1.5f, 0.75f, m.apply(1f, 1f))
    }

    @Test
    fun rotation_only_90deg_square_aspect() {
        val v = TransformMath.TransformValues(rotationDeg = 90f)
        val m = TransformMath.composeMatrix(v, aspect = 1f)
        // Centre fixed; (1,0) -> (0,1) for a +90° rotation (x'=-s*y+c*x ... here c=0,s=1 => (0,1)).
        assertPoint(0f, 0f, m.apply(0f, 0f))
        assertPoint(0f, 1f, m.apply(1f, 0f))
    }

    @Test
    fun rotation_is_aspect_corrected_no_skew() {
        // Aspect correction makes a 90° rotation RIGID IN PIXEL SPACE (not raw NDC). On a 9:16
        // frame, an x-axis point must map onto the y-axis (x'≈0) and preserve pixel-space length:
        // pixel = (ndcX * aspect, ndcY), aspect = w/h.
        val a = 9f / 16f
        val v = TransformMath.TransformValues(rotationDeg = 90f)
        val (x, y) = TransformMath.composeMatrix(v, aspect = a).apply(0.5f, 0f)
        assertEquals("x-axis point must rotate onto the y-axis", 0f, x, 1e-3f)
        val inputPixelMag = 0.5f * a
        val outputPixelMag = kotlin.math.sqrt((x * a) * (x * a) + y * y)
        assertEquals("rotation must preserve pixel-space magnitude (no skew)", inputPixelMag, outputPixelMag, 1e-3f)
    }

    @Test
    fun pivot_correctness_top_left_pivot_stays_fixed() {
        // Pivot at NDC (-1,-1) == anchor (0,0); scaling must keep that point fixed.
        val v = TransformMath.TransformValues(scaleX = 2f, scaleY = 2f, pivotX = 0f, pivotY = 0f)
        val m = TransformMath.composeMatrix(v, aspect = 1f)
        assertPoint(-1f, -1f, m.apply(-1f, -1f))
    }

    @Test
    fun combined_trs_matches_canonical_formula() {
        // v' = position + pivot + R·S·(v - pivot)  with non-centre pivot.
        val v = TransformMath.TransformValues(
            positionX = 0.2f, positionY = -0.1f,
            scaleX = 2f, scaleY = 2f,
            rotationDeg = 90f,
            pivotX = 0.75f, pivotY = 0.75f // NDC pivot (0.5,0.5)
        )
        val m = TransformMath.composeMatrix(v, aspect = 1f)
        // Hand-compute for v=(1,1): pivotNdc=(0.5,0.5); v-pivot=(0.5,0.5);
        // S -> (1.0,1.0); R90 -> (-1.0,1.0); +pivot -> (-0.5,1.5); +pos -> (-0.3,1.4)
        assertPoint(-0.3f, 1.4f, m.apply(1f, 1f))
    }

    @Test
    fun order_sensitivity_scale_then_translate_differs_from_translate_then_scale() {
        // Canonical order scales about pivot THEN translates by position. A hypothetical
        // translate-before-scale would move the origin differently — prove the canonical
        // result is NOT the naive scale*(v)+pos-scaled form.
        val v = TransformMath.TransformValues(positionX = 1f, scaleX = 2f, pivotX = 0.5f, pivotY = 0.5f)
        val m = TransformMath.composeMatrix(v, aspect = 1f)
        // Canonical: pivotNdc x = 0; point x=0 -> S*(0-0)=0 +pivot0 +pos1 = 1.0
        assertEquals(1.0f, m.apply(0f, 0f).first, eps)
        // If order were T·S applied as scale*(v+pos) it would give 2.0 — assert it is NOT.
        assertTrue(kotlin.math.abs(m.apply(0f, 0f).first - 2.0f) > 0.1f)
    }

    @Test
    fun opacity_passthrough() {
        val v = TransformMath.resolveValues(50, 0, 100, constants(opacity = 0.4f))
        assertEquals(0.4f, TransformMath.opacityOf(v), eps)
    }

    @Test
    fun keyframe_provider_integration_samples_midpoint() {
        // scaleX keyframed 1->2 (LINEAR) over absolute window [0,100]; resolver normally offsets
        // window-relative keyframes, here we author absolute directly.
        val provider = KeyframedParams(
            mapOf(
                AnimationPropertyKeys.POSITION_X to listOf(Keyframe(0, 0f)),
                AnimationPropertyKeys.POSITION_Y to listOf(Keyframe(0, 0f)),
                AnimationPropertyKeys.SCALE_X to listOf(Keyframe(0, 1f), Keyframe(100, 2f)),
                AnimationPropertyKeys.SCALE_Y to listOf(Keyframe(0, 1f)),
                AnimationPropertyKeys.ROTATION to listOf(Keyframe(0, 0f)),
                AnimationPropertyKeys.OPACITY to listOf(Keyframe(0, 1f)),
                AnimationPropertyKeys.ANCHOR_X to listOf(Keyframe(0, 0.5f)),
                AnimationPropertyKeys.ANCHOR_Y to listOf(Keyframe(0, 0.5f))
            )
        )
        val v = TransformMath.resolveValues(50, 0, 100, provider)
        assertEquals(1.5f, v.scaleX, eps)
    }
}
