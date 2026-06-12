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
        val fraction = when (from.easing) {
            KeyframeEasing.LINEAR -> rawFraction
            KeyframeEasing.SMOOTHSTEP -> rawFraction * rawFraction * (3f - 2f * rawFraction)
        }
        return from.value + ((to.value - from.value) * fraction)
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
