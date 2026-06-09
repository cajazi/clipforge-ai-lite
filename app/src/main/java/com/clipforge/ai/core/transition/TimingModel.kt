package com.clipforge.ai.core.transition

/**
 * How a transition occupies time at a clip boundary — the *generic* consumption rule that
 * replaces the per-type `boundaryConsumption()` switch in CrossfadeRenderPlan.
 *
 * The render plan asks the timing model how much timeline a boundary consumes; it never
 * needs to know the concrete transition type. Export timing was fixed (commit 6a54e5d) so
 * that a boundary CONSUMES its window (total = A + B - consumed), and these models encode
 * exactly that contract:
 *
 * - [Overlap]: A's tail and B's head overlap for the full [durationMs]; the boundary
 *   consumes `durationMs` (Crossfade / Slide / Zoom / WhipPan today).
 * - [SequentialDip]: A fades to a color over `durationMs/2`, then B fades up over
 *   `durationMs/2`; the boundary consumes `durationMs/2` of *each* neighbor's edge
 *   (Fade Black / Fade White today).
 *
 * Phase A: pure math, not yet wired into the render plan (that is a later, protected-file
 * phase). Values here mirror the existing CrossfadeRenderPlan semantics 1:1.
 */
sealed interface TimingModel {

    /** Milliseconds consumed from the clip edges at this boundary for a given duration. */
    fun consumptionMs(durationMs: Long): Long

    /** A-tail overlaps B-head for the whole window. Consumes the full duration. */
    data object Overlap : TimingModel {
        override fun consumptionMs(durationMs: Long): Long = durationMs.coerceAtLeast(0L)
    }

    /** Through-color dip: half on A's tail, half on B's head. Consumes half. */
    data object SequentialDip : TimingModel {
        override fun consumptionMs(durationMs: Long): Long = (durationMs / 2L).coerceAtLeast(0L)
    }
}
