package com.clipforge.ai.core.effects

/**
 * Pure keyframe interpolation (V13 foundation; no authoring UI yet).
 *
 * Semantics:
 *  - frames must be non-empty and STRICTLY increasing in time (rejected otherwise),
 *  - t before the first frame clamps to the first value; after the last, to the last,
 *  - between frames: the leading frame's easing shapes the interpolation fraction
 *    (LINEAR, or the house smoothstep t*t*(3-2t)),
 *  - lookup is binary search (frames lists stay small, but render paths call this
 *    per frame per key).
 */
object KeyframeSampler {

    fun sample(frames: List<Keyframe>, tUs: Long): Float {
        requireSorted(frames)
        if (frames.size == 1 || tUs <= frames.first().timeUs) return frames.first().value
        if (tUs >= frames.last().timeUs) return frames.last().value

        // Binary search for the last frame with timeUs <= tUs.
        var lo = 0
        var hi = frames.size - 1
        while (lo + 1 < hi) {
            val mid = (lo + hi) ushr 1
            if (frames[mid].timeUs <= tUs) lo = mid else hi = mid
        }
        val from = frames[lo]
        val to = frames[hi]
        val span = (to.timeUs - from.timeUs).toFloat()
        val rawFraction = ((tUs - from.timeUs).toFloat() / span).coerceIn(0f, 1f)
        val fraction = applyEasing(from.easing, rawFraction)
        return from.value + ((to.value - from.value) * fraction)
    }

    /**
     * Maps a raw [0,1] fraction through an easing curve. LINEAR and SMOOTHSTEP are byte-for-byte
     * the original behavior. Every curve is exact identity at f=0 and f=1 (so keyframe endpoints
     * land on their values); overshoot curves may leave [0,1] in between by design.
     */
    fun applyEasing(easing: KeyframeEasing, f: Float): Float = when (easing) {
        KeyframeEasing.LINEAR -> f
        KeyframeEasing.SMOOTHSTEP -> f * f * (3f - 2f * f)
        KeyframeEasing.CUBIC_IN -> f * f * f
        KeyframeEasing.CUBIC_OUT -> {
            val a = 1f - f
            1f - a * a * a
        }
        KeyframeEasing.CUBIC_IN_OUT ->
            if (f < 0.5f) 4f * f * f * f
            else {
                val a = -2f * f + 2f
                1f - (a * a * a) / 2f
            }
        KeyframeEasing.BACK_OUT -> {
            val c1 = 1.70158f
            val c3 = c1 + 1f
            val a = f - 1f
            1f + c3 * a * a * a + c1 * a * a
        }
        KeyframeEasing.ELASTIC_OUT -> when {
            f <= 0f -> 0f
            f >= 1f -> 1f
            else -> {
                val c4 = (2.0 * Math.PI / 3.0).toFloat()
                (Math.pow(2.0, -10.0 * f).toFloat() *
                    kotlin.math.sin((f * 10f - 0.75f) * c4)) + 1f
            }
        }
        KeyframeEasing.BOUNCE_OUT -> bounceOut(f)
    }

    private fun bounceOut(x: Float): Float {
        val n1 = 7.5625f
        val d1 = 2.75f
        var t = x
        return when {
            t < 1f / d1 -> n1 * t * t
            t < 2f / d1 -> { t -= 1.5f / d1; n1 * t * t + 0.75f }
            t < 2.5f / d1 -> { t -= 2.25f / d1; n1 * t * t + 0.9375f }
            else -> { t -= 2.625f / d1; n1 * t * t + 0.984375f }
        }
    }

    /** Throws [IllegalArgumentException] for empty or non-strictly-increasing tracks. */
    fun requireSorted(frames: List<Keyframe>) {
        require(frames.isNotEmpty()) { "Keyframe track must not be empty" }
        for (i in 1 until frames.size) {
            require(frames[i].timeUs > frames[i - 1].timeUs) {
                "Keyframe track must be strictly increasing in time: " +
                    "frame[$i].timeUs=${frames[i].timeUs} <= frame[${i - 1}].timeUs=${frames[i - 1].timeUs}"
            }
        }
    }
}
