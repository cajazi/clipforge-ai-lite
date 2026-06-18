package com.clipforge.ai.validation.c9.support

import com.clipforge.ai.core.animation.TransformMath
import com.clipforge.ai.core.effects.ParamProvider
import kotlin.math.abs

/**
 * C9.0 support harness: captures [TransformMath] matrix + opacity at sample points for a given
 * [ParamProvider]. Pure wrapper around the forbidden-to-modify [TransformMath] source of truth —
 * does not alter its math, only observes it.
 */
object MatrixSampler {
    data class Sample(val timeUs: Long, val matrix: TransformMath.Mat3, val opacity: Float)

    /** Sampling rule (C9.0 spec): every frame under 500ms, otherwise max 16ms interval. */
    fun defaultTimestampsUs(windowStartUs: Long, windowEndUs: Long): List<Long> {
        if (windowStartUs >= windowEndUs) return emptyList()
        val durationUs = windowEndUs - windowStartUs
        val stepUs = if (durationUs < 500_000L) 8_000L else 16_000L
        val samples = sortedSetOf<Long>()
        var t = windowStartUs
        while (t < windowEndUs) {
            samples += t
            t += stepUs
        }
        samples += windowEndUs - 1L
        return samples.toList()
    }

    fun sample(
        provider: ParamProvider,
        windowStartUs: Long,
        windowEndUs: Long,
        aspect: Float,
        timestampsUs: List<Long> = defaultTimestampsUs(windowStartUs, windowEndUs)
    ): List<Sample> = timestampsUs.map { t ->
        val values = TransformMath.resolveValues(t, windowStartUs, windowEndUs, provider)
        Sample(
            timeUs = t,
            matrix = TransformMath.composeMatrix(values, aspect),
            opacity = TransformMath.opacityOf(values)
        )
    }

    /** Largest absolute component-wise delta between two matrices. */
    fun matrixDelta(a: TransformMath.Mat3, b: TransformMath.Mat3): Float = maxOf(
        abs(a.m00 - b.m00), abs(a.m01 - b.m01), abs(a.m02 - b.m02),
        abs(a.m10 - b.m10), abs(a.m11 - b.m11), abs(a.m12 - b.m12)
    )
}
