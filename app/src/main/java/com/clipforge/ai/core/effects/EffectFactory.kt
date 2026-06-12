package com.clipforge.ai.core.effects

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.GlEffect

/**
 * Creates the render-side effect for one effect item window.
 *
 * One factory, two consumers (A' architecture): the preview player and the export
 * pipeline both call this with windows in THEIR OWN timebase:
 *  - preview passes timeline microseconds unchanged (C0/E3: playback pts is continuous
 *    playlist time == timeline time),
 *  - export passes TimelineToCompositionTimeMap-converted microseconds.
 * The factory is timebase-agnostic; produced [GlEffect]s must be identity outside the
 * window (the established transition-shader gating pattern).
 *
 * This is deliberately the ONLY Media3-importing contract in core/effects so the rest of
 * the package stays JVM-unit-testable.
 */
@UnstableApi
fun interface EffectFactory {
    fun create(windowStartUs: Long, windowEndUs: Long, params: ParamProvider): GlEffect
}
