package com.clipforge.ai.core.transition

/**
 * Reusable easing curve: maps linear progress t in [0,1] to eased progress in [0,1].
 *
 * Centralizes the easing math that is currently duplicated as inline literals across the
 * overlays (e.g. `t*t*(3-2t)` smoothstep in ZoomOverlay/SlideOverlay) and the preview
 * renderers. Both export renderers and preview renderers read their curve from the
 * descriptor so a transition's feel is defined once.
 *
 * Phase A: definitions only. Nothing is rewired to use these yet — existing transitions
 * keep their inline easing until they are migrated behind the registry.
 */
fun interface Easing {
    fun transform(t: Float): Float

    companion object {
        /** Identity. */
        val Linear: Easing = Easing { t -> t.coerceIn(0f, 1f) }

        /** Hermite smoothstep `t*t*(3-2t)` — the established ClipForge "soft" curve. */
        val Smoothstep: Easing = Easing { t ->
            val c = t.coerceIn(0f, 1f)
            c * c * (3f - 2f * c)
        }

        /** Cubic ease-in-out — punchier, CapCut-leaning acceleration. */
        val CubicInOut: Easing = Easing { t ->
            val c = t.coerceIn(0f, 1f)
            if (c < 0.5f) 4f * c * c * c
            else 1f - Math.pow((-2f * c + 2f).toDouble(), 3.0).toFloat() / 2f
        }

        /** Exponential ease-out — fast start, long settle (kinetic transitions). */
        val ExpoOut: Easing = Easing { t ->
            val c = t.coerceIn(0f, 1f)
            if (c >= 1f) 1f else 1f - Math.pow(2.0, (-10f * c).toDouble()).toFloat()
        }
    }
}
