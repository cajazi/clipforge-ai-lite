package com.clipforge.ai.core.transition.renderers

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.EditedMediaItem
import com.clipforge.ai.core.gl.CrossfadeBitmapOverlay
import com.clipforge.ai.core.gl.DipToColorOverlay
import com.clipforge.ai.core.gl.DirectionalBlurGlEffect
import com.clipforge.ai.core.gl.SlideOverlay
import com.clipforge.ai.core.gl.ZoomOverlay
import com.clipforge.ai.core.transition.SegmentContext
import com.clipforge.ai.core.transition.TransitionRenderer

/**
 * Overlay-backed [TransitionRenderer]s — thin adapters that reproduce the exact item/overlay
 * construction of the matching CrossfadeExecutor arms, so the registry can emit byte-equivalent
 * output. No new visual behavior; this is the migration bridge for the existing families.
 *
 * Cache lifecycle: each renderer builds its cache, validates non-empty (mirroring the
 * executor's `IllegalStateException` guard), and hands `{ cache.release() }` to
 * `registerCleanup`. The caller owns when cleanup runs (after export), matching the legacy
 * `caches` list.
 */

/** Dissolve / Cross-Dissolve. Mirrors Op.Crossfade. */
@UnstableApi
class CrossfadeTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.crossfadeCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Crossfade cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }
        val overlay = CrossfadeBitmapOverlay(cache, ctx.compositionStartUs, ctx.compositionEndUs)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, OverlayEffect(listOf(overlay))))
    }
}

/** Fade Black / Fade White. Mirrors Op.DipToColor (two sequential half items, no cache). */
@UnstableApi
class DipToColorTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val half = (ctx.param(TransitionParamKeys.HALF_DURATION_MS)?.toLongOrNull()
            ?: (ctx.durationMs / 2L)).coerceAtLeast(0L)
        val colorInt = ctx.param(TransitionParamKeys.COLOR_INT)?.toIntOrNull() ?: android.graphics.Color.BLACK
        val bHeadEndMs = ctx.param(TransitionParamKeys.B_HEAD_END_MS)?.toLongOrNull()
            ?: (ctx.bHeadStartMs + half)

        val fadeOutStartUs = ctx.compositionStartUs
        val fadeOutEndUs = fadeOutStartUs + half * 1000L
        val overlayA = DipToColorOverlay(colorInt, fadeOutStartUs, fadeOutEndUs, fadeOut = true)
        val itemA = OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, OverlayEffect(listOf(overlayA)))

        val fadeInStartUs = fadeOutEndUs
        val fadeInEndUs = fadeInStartUs + half * 1000L
        val overlayB = DipToColorOverlay(colorInt, fadeInStartUs, fadeInEndUs, fadeOut = false)
        val itemB = OverlayRenderSupport.overlayItem(ctx.pathB, ctx.bHeadStartMs, bHeadEndMs, OverlayEffect(listOf(overlayB)))

        return listOf(itemA, itemB)
    }
}

/** Slide L/R/U/D. Mirrors Op.Slide. */
@UnstableApi
class SlideTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.slideProfileCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Slide cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }
        val raw = ctx.param(TransitionParamKeys.DIRECTION) ?: "SLIDE_LEFT"
        val dir = SlideOverlay.Direction.valueOf(raw.removePrefix("SLIDE_"))
        val overlay = SlideOverlay(cache, ctx.compositionStartUs, ctx.compositionEndUs, dir)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, OverlayEffect(listOf(overlay))))
    }
}

/** Zoom In/Out. Mirrors Op.Zoom. */
@UnstableApi
class ZoomTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.slideProfileCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Zoom cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }
        val raw = ctx.param(TransitionParamKeys.MODE) ?: "ZOOM_IN"
        val mode = ZoomOverlay.Mode.valueOf(raw.removePrefix("ZOOM_"))
        val overlay = ZoomOverlay(cache, ctx.compositionStartUs, ctx.compositionEndUs, mode)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, OverlayEffect(listOf(overlay))))
    }
}

/** Whip Pan L/R. Mirrors Op.WhipPan (directional blur GlEffect + slide overlay). */
@UnstableApi
class WhipPanTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.slideProfileCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Whip pan cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }
        val raw = ctx.param(TransitionParamKeys.DIRECTION) ?: "WHIP_PAN_LEFT"
        val dir = SlideOverlay.Direction.valueOf(raw.removePrefix("WHIP_PAN_"))
        val blurX = when (dir) {
            SlideOverlay.Direction.LEFT -> 1f
            SlideOverlay.Direction.RIGHT -> -1f
            SlideOverlay.Direction.UP, SlideOverlay.Direction.DOWN -> 0f
        }
        val blurY = when (dir) {
            SlideOverlay.Direction.UP -> 1f
            SlideOverlay.Direction.DOWN -> -1f
            SlideOverlay.Direction.LEFT, SlideOverlay.Direction.RIGHT -> 0f
        }
        val blur = DirectionalBlurGlEffect(ctx.compositionStartUs, ctx.compositionEndUs, blurX, blurY)
        val overlay = SlideOverlay(cache, ctx.compositionStartUs, ctx.compositionEndUs, dir)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, blur, OverlayEffect(listOf(overlay))))
    }
}
