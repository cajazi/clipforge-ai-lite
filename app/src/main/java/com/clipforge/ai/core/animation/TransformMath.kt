package com.clipforge.ai.core.animation

import com.clipforge.ai.core.effects.ParamProvider
import kotlin.math.cos
import kotlin.math.sin

/**
 * SINGLE SOURCE OF TRUTH for all transform-animation math (C8.2).
 *
 * Every position/scale/rotation/pivot/opacity and matrix-composition equation lives here and
 * nowhere else. Consumers (the GL export effect now; the Compose preview in C8.3; the export
 * wiring in C8.4) must ONLY:
 *   1. call [resolveValues] to sample the animated values at a presentation time, then
 *   2. call [composeMatrix] to get the affine matrix, and
 *   3. apply that matrix (+ [TransformValues.opacity]) — never restate any TRS equation.
 *
 * Pure Kotlin: no Android, no Media3, no GLSL. The GL shader is a dumb `uMatrix * pos` multiply
 * fed by [Mat3.toColumnMajor4x4]; the preview applies the same matrix via a Canvas concat.
 *
 * Coordinate space: vertices are in normalized device coordinates (NDC, [-1,1] each axis, centre
 * = origin). `positionX/Y` shift in NDC (1.0 = half-frame). `anchorX/Y` are normalized [0,1]
 * (0.5 = centre) and converted to NDC pivots internally. Rotation is degrees about Z, aspect-
 * corrected here (and only here) so it does not skew on non-square frames.
 */
object TransformMath {

    /** Resolved animation values at one instant. Defaults are the identity (no-op) transform. */
    data class TransformValues(
        val positionX: Float = 0f,
        val positionY: Float = 0f,
        val scaleX: Float = 1f,
        val scaleY: Float = 1f,
        val rotationDeg: Float = 0f,
        val opacity: Float = 1f,
        val pivotX: Float = 0.5f,
        val pivotY: Float = 0.5f
    ) {
        companion object {
            val IDENTITY = TransformValues()
        }
    }

    /**
     * Row-major 3x3 affine matrix (last row implied 0 0 1):
     *   [m00 m01 m02]   x' = m00*x + m01*y + m02
     *   [m10 m11 m12]   y' = m10*x + m11*y + m12
     *   [ 0   0   1 ]
     */
    class Mat3(
        val m00: Float, val m01: Float, val m02: Float,
        val m10: Float, val m11: Float, val m12: Float
    ) {
        /** Apply to a 2D point. Used by tests and by anyone needing CPU-side mapping. */
        fun apply(x: Float, y: Float): Pair<Float, Float> =
            Pair(m00 * x + m01 * y + m02, m10 * x + m11 * y + m12)

        /** this * other (this applied AFTER other). */
        operator fun times(o: Mat3): Mat3 = Mat3(
            m00 * o.m00 + m01 * o.m10, m00 * o.m01 + m01 * o.m11, m00 * o.m02 + m01 * o.m12 + m02,
            m10 * o.m00 + m11 * o.m10, m10 * o.m01 + m11 * o.m11, m10 * o.m02 + m11 * o.m12 + m12
        )

        /**
         * Column-major float[16] for a GL `mat4` uniform. Pure layout repack of the already-
         * composed affine (NOT a transform equation): the 3x3 affine sits in the x/y plane, z
         * passes through, w=1.
         */
        fun toColumnMajor4x4(): FloatArray = floatArrayOf(
            m00, m10, 0f, 0f,   // column 0
            m01, m11, 0f, 0f,   // column 1
            0f, 0f, 1f, 0f,     // column 2
            m02, m12, 0f, 1f    // column 3
        )

        companion object {
            val IDENTITY = Mat3(1f, 0f, 0f, 0f, 1f, 0f)
            fun translate(tx: Float, ty: Float) = Mat3(1f, 0f, tx, 0f, 1f, ty)
            fun scale(sx: Float, sy: Float) = Mat3(sx, 0f, 0f, 0f, sy, 0f)
            /** Pure (uncorrected) rotation about origin. */
            fun rotate(deg: Float): Mat3 {
                val r = Math.toRadians(deg.toDouble())
                val c = cos(r).toFloat()
                val s = sin(r).toFloat()
                return Mat3(c, -s, 0f, s, c, 0f)
            }
        }
    }

    /**
     * Sample the animated values at [presentationTimeUs] (absolute, the consumer's timebase —
     * timeline µs in preview, composition µs in export, matching the shipped ColorAdjust pattern).
     * Outside `[windowStartUs, windowEndUs)` returns [TransformValues.IDENTITY] without querying
     * the provider. Inside the window every key is read from [provider] (the resolver guarantees
     * all keys are present).
     */
    fun resolveValues(
        presentationTimeUs: Long,
        windowStartUs: Long,
        windowEndUs: Long,
        provider: ParamProvider
    ): TransformValues {
        if (presentationTimeUs < windowStartUs || presentationTimeUs >= windowEndUs) {
            return TransformValues.IDENTITY
        }
        return TransformValues(
            positionX = provider.valueAt(AnimationPropertyKeys.POSITION_X, presentationTimeUs),
            positionY = provider.valueAt(AnimationPropertyKeys.POSITION_Y, presentationTimeUs),
            scaleX = provider.valueAt(AnimationPropertyKeys.SCALE_X, presentationTimeUs),
            scaleY = provider.valueAt(AnimationPropertyKeys.SCALE_Y, presentationTimeUs),
            rotationDeg = provider.valueAt(AnimationPropertyKeys.ROTATION, presentationTimeUs),
            opacity = provider.valueAt(AnimationPropertyKeys.OPACITY, presentationTimeUs),
            pivotX = provider.valueAt(AnimationPropertyKeys.ANCHOR_X, presentationTimeUs),
            pivotY = provider.valueAt(AnimationPropertyKeys.ANCHOR_Y, presentationTimeUs)
        )
    }

    /**
     * Canonical composition — the ONLY place TRS ordering exists:
     *   M = T(position) · T(pivot) · R(rotation, aspect) · S(scaleX, scaleY) · T(-pivot)
     *   v' = position + pivot + R · S · (v - pivot)
     *
     * [aspect] = frameWidth / frameHeight; rotation is corrected to that aspect so it does not
     * skew. Pivot (anchor in [0,1]) is converted to NDC: ndc = anchor * 2 - 1.
     */
    fun composeMatrix(values: TransformValues, aspect: Float): Mat3 {
        val a = if (aspect <= 0f) 1f else aspect
        val pivotNdcX = values.pivotX * 2f - 1f
        val pivotNdcY = values.pivotY * 2f - 1f

        // Aspect-corrected rotation: work in a square space (x scaled by aspect), rotate, undo.
        val rotation = Mat3.scale(1f / a, 1f) * Mat3.rotate(values.rotationDeg) * Mat3.scale(a, 1f)

        return Mat3.translate(values.positionX, values.positionY) *
            Mat3.translate(pivotNdcX, pivotNdcY) *
            rotation *
            Mat3.scale(values.scaleX, values.scaleY) *
            Mat3.translate(-pivotNdcX, -pivotNdcY)
    }

    /** Convenience passthrough (single place for opacity ownership). */
    fun opacityOf(values: TransformValues): Float = values.opacity
}
