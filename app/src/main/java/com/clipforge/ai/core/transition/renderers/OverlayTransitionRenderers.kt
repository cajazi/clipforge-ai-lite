package com.clipforge.ai.core.transition.renderers

import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.EditedMediaItem
import com.clipforge.ai.core.gl.CubeBitmapOverlay
import com.clipforge.ai.core.gl.CubeDirection
import com.clipforge.ai.core.gl.CubeGlEffect
import com.clipforge.ai.core.gl.BlurredCrossfadeBitmapOverlay
import com.clipforge.ai.core.gl.CrossBlurGlEffect
import com.clipforge.ai.core.gl.CrossfadeBitmapOverlay
import com.clipforge.ai.core.gl.DipToColorOverlay
import com.clipforge.ai.core.gl.DirectionalBlurGlEffect
import com.clipforge.ai.core.gl.FilmBurnGlEffect
import com.clipforge.ai.core.gl.FilmBurnMode
import com.clipforge.ai.core.gl.FlipBitmapOverlay
import com.clipforge.ai.core.gl.FlipDirection
import com.clipforge.ai.core.gl.FlipGlEffect
import com.clipforge.ai.core.gl.FlashColorOverlay
import com.clipforge.ai.core.gl.FlashRevealOverlay
import com.clipforge.ai.core.gl.GlitchMode
import com.clipforge.ai.core.gl.GlitchProGlEffect
import com.clipforge.ai.core.gl.PageTurnDirection
import com.clipforge.ai.core.gl.PageTurnGlEffect
import com.clipforge.ai.core.gl.PushGlEffect
import com.clipforge.ai.core.gl.RotationBitmapOverlay
import com.clipforge.ai.core.gl.RotationGlEffect
import com.clipforge.ai.core.gl.RotationMode
import com.clipforge.ai.core.gl.SlideOverlay
import com.clipforge.ai.core.gl.WipeDirection
import com.clipforge.ai.core.gl.WipeGlEffect
import com.clipforge.ai.core.gl.ZoomOverlay
import com.clipforge.ai.core.transition.SegmentContext
import com.clipforge.ai.core.transition.TransitionRenderer

@UnstableApi
private fun blurVectorForDirection(raw: String, prefix: String): Pair<Float, Float> {
    val dirName = raw.removePrefix(prefix).ifBlank { "LEFT" }
    val dir = runCatching { SlideOverlay.Direction.valueOf(dirName) }
        .getOrDefault(SlideOverlay.Direction.LEFT)
    return when (dir) {
        SlideOverlay.Direction.LEFT -> 1f to 0f
        SlideOverlay.Direction.RIGHT -> -1f to 0f
        SlideOverlay.Direction.UP -> 0f to 1f
        SlideOverlay.Direction.DOWN -> 0f to -1f
    }
}

@UnstableApi
private fun pushVectorForDirection(raw: String, prefix: String): Pair<Float, Float> {
    val dir = SlideOverlay.Direction.valueOf(raw.removePrefix(prefix))
    return when (dir) {
        SlideOverlay.Direction.LEFT -> -1f to 0f
        SlideOverlay.Direction.RIGHT -> 1f to 0f
        SlideOverlay.Direction.UP -> 0f to 1f
        SlideOverlay.Direction.DOWN -> 0f to -1f
    }
}

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

/** Flash white/black/warm/blue. Hard A/B swap hidden under a full-screen burst. */
@UnstableApi
class FlashTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.slideProfileCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Flash cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }

        val colorInt = ctx.param(TransitionParamKeys.FLASH_COLOR_INT)?.toIntOrNull()
            ?: android.graphics.Color.WHITE
        val reveal = FlashRevealOverlay(cache, ctx.compositionStartUs, ctx.compositionEndUs)
        val flash = FlashColorOverlay(colorInt, ctx.compositionStartUs, ctx.compositionEndUs)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, OverlayEffect(listOf(reveal, flash))))
    }
}

/** Film Burn classic/warm/heavy. Dual-texture shader reveals B through an organic burn mask. */
@UnstableApi
class FilmBurnTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.slideProfileCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Film burn cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }
        val mode = filmBurnModeFor(ctx.param(TransitionParamKeys.FILM_BURN_MODE) ?: "FILM_BURN")
        val effect = FilmBurnGlEffect(ctx.compositionStartUs, ctx.compositionEndUs, cache, mode)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, effect))
    }

    private fun filmBurnModeFor(raw: String): FilmBurnMode = when (raw.uppercase()) {
        "FILM_BURN_WARM" -> FilmBurnMode.WARM
        "FILM_BURN_HEAVY" -> FilmBurnMode.HEAVY
        else -> FilmBurnMode.CLASSIC
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

/** Push L/R/U/D. Moves A out while B slides in over the same overlap window. */
@UnstableApi
class PushTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.slideProfileCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Push cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }
        val raw = ctx.param(TransitionParamKeys.DIRECTION) ?: "PUSH_LEFT"
        val dir = SlideOverlay.Direction.valueOf(raw.removePrefix("PUSH_"))
        val (pushX, pushY) = pushVectorForDirection(raw, "PUSH_")
        val push = PushGlEffect(ctx.compositionStartUs, ctx.compositionEndUs, pushX, pushY)
        val overlay = SlideOverlay(cache, ctx.compositionStartUs, ctx.compositionEndUs, dir)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, push, OverlayEffect(listOf(overlay))))
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

/** Spin / Rotate / Camera Roll. Rotates A with GL and rotates/scales cached B as overlay. */
@UnstableApi
class RotationTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.slideProfileCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Rotation cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }
        val raw = ctx.param(TransitionParamKeys.MODE) ?: "SPIN"
        val mode = rotationModeFor(raw)
        val effect = RotationGlEffect(ctx.compositionStartUs, ctx.compositionEndUs, mode)
        val overlay = RotationBitmapOverlay(cache, ctx.compositionStartUs, ctx.compositionEndUs, mode)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, effect, OverlayEffect(listOf(overlay))))
    }

    private fun rotationModeFor(raw: String): RotationMode = when (raw.uppercase()) {
        "ROTATE" -> RotationMode.ROTATE
        "CAMERA_ROLL" -> RotationMode.CAMERA_ROLL
        else -> RotationMode.SPIN
    }
}

/** Cube L/R/U/D. 2.5D approximation: A folds away while cached B expands in. */
@UnstableApi
class CubeTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.slideProfileCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Cube cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }
        val raw = ctx.param(TransitionParamKeys.DIRECTION) ?: "CUBE_LEFT"
        val direction = cubeDirectionFor(raw)
        val effect = CubeGlEffect(ctx.compositionStartUs, ctx.compositionEndUs, direction)
        val overlay = CubeBitmapOverlay(cache, ctx.compositionStartUs, ctx.compositionEndUs, direction)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, effect, OverlayEffect(listOf(overlay))))
    }

    private fun cubeDirectionFor(raw: String): CubeDirection = when (raw.uppercase()) {
        "CUBE_RIGHT" -> CubeDirection.RIGHT
        "CUBE_UP" -> CubeDirection.UP
        "CUBE_DOWN" -> CubeDirection.DOWN
        else -> CubeDirection.LEFT
    }
}

/** Flip L/R/U/D. Center-pivot card flip: A rotates out, cached B appears after midpoint. */
@UnstableApi
class FlipTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.slideProfileCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Flip cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }
        val raw = ctx.param(TransitionParamKeys.DIRECTION) ?: "FLIP_LEFT"
        val direction = flipDirectionFor(raw)
        val effect = FlipGlEffect(ctx.compositionStartUs, ctx.compositionEndUs, direction)
        val overlay = FlipBitmapOverlay(cache, ctx.compositionStartUs, ctx.compositionEndUs, direction)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, effect, OverlayEffect(listOf(overlay))))
    }

    private fun flipDirectionFor(raw: String): FlipDirection = when (raw.uppercase()) {
        "FLIP_RIGHT" -> FlipDirection.RIGHT
        "FLIP_UP" -> FlipDirection.UP
        "FLIP_DOWN" -> FlipDirection.DOWN
        else -> FlipDirection.LEFT
    }
}

/** Page Turn L/R/U/D. A curls away while animated cached B frames are sampled underneath. */
@UnstableApi
class PageTurnTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.slideProfileCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Page turn cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }
        val raw = ctx.param(TransitionParamKeys.DIRECTION) ?: "PAGE_TURN_LEFT"
        val direction = pageTurnDirectionFor(raw)
        val effect = PageTurnGlEffect(ctx.compositionStartUs, ctx.compositionEndUs, cache, direction)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, effect))
    }

    private fun pageTurnDirectionFor(raw: String): PageTurnDirection = when (raw.uppercase()) {
        "PAGE_TURN_RIGHT" -> PageTurnDirection.RIGHT
        "PAGE_TURN_UP" -> PageTurnDirection.UP
        "PAGE_TURN_DOWN" -> PageTurnDirection.DOWN
        else -> PageTurnDirection.LEFT
    }
}

/** Blur/Gaussian Blur. A peaks blur near midpoint while cached B crossfades from blur to sharp. */
@UnstableApi
class BlurTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.crossfadeCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Blur cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }
        val mode = ctx.param(TransitionParamKeys.BLUR_MODE) ?: "BLUR"
        val maxBlur = if (mode.equals("GAUSSIAN_BLUR", ignoreCase = true)) 24f else 18f
        val effect = CrossBlurGlEffect(ctx.compositionStartUs, ctx.compositionEndUs, maxBlurTexels = maxBlur)
        val overlay = BlurredCrossfadeBitmapOverlay(
            cache = cache,
            fadeStartUs = ctx.compositionStartUs,
            fadeEndUs = ctx.compositionEndUs,
            maxDownsample = if (mode.equals("GAUSSIAN_BLUR", ignoreCase = true)) 0.14f else 0.20f
        )
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, effect, OverlayEffect(listOf(overlay))))
    }
}

/** Whip Pan L/R/U/D. Mirrors Op.WhipPan (directional blur GlEffect + slide overlay). */
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
        val (blurX, blurY) = blurVectorForDirection(raw, "WHIP_PAN_")
        val blur = DirectionalBlurGlEffect(ctx.compositionStartUs, ctx.compositionEndUs, blurX, blurY)
        val overlay = SlideOverlay(cache, ctx.compositionStartUs, ctx.compositionEndUs, dir)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, blur, OverlayEffect(listOf(overlay))))
    }
}

/** Motion Blur L/R/U/D. Blurs A's tail while B dissolves in-place over the overlap. */
@UnstableApi
class MotionBlurTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.crossfadeCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Motion blur cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }
        val raw = ctx.param(TransitionParamKeys.DIRECTION) ?: "MOTION_BLUR_LEFT"
        val (blurX, blurY) = blurVectorForDirection(raw, "MOTION_BLUR_")
        val blur = DirectionalBlurGlEffect(ctx.compositionStartUs, ctx.compositionEndUs, blurX, blurY)
        val overlay = CrossfadeBitmapOverlay(cache, ctx.compositionStartUs, ctx.compositionEndUs)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, blur, OverlayEffect(listOf(overlay))))
    }
}

/** Wipe L/R/U/D. Reveals cached B under static A with a soft directional edge. */
@UnstableApi
class WipeTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.slideProfileCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Wipe cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }
        val direction = wipeDirectionFor(ctx.param(TransitionParamKeys.DIRECTION) ?: "WIPE")
        val effect = WipeGlEffect(ctx.compositionStartUs, ctx.compositionEndUs, cache, direction)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, effect))
    }

    private fun wipeDirectionFor(raw: String): WipeDirection = when (raw.uppercase()) {
        "WIPE_RIGHT" -> WipeDirection.RIGHT
        "WIPE_UP" -> WipeDirection.UP
        "WIPE_DOWN" -> WipeDirection.DOWN
        else -> WipeDirection.LEFT
    }
}

/** Glitch Pro/Digital/RGB/Scanline. Shader-only burst corruption hides a midpoint A/B swap. */
@UnstableApi
class GlitchProTransitionRenderer : TransitionRenderer {
    override val supportsExport = true
    override fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem> {
        val cache = OverlayRenderSupport.slideProfileCache(ctx.pathB, ctx.bHeadStartMs, ctx.durationMs)
        cache.build()
        check(!cache.isEmpty()) { "Glitch Pro cache empty pathB=${ctx.pathB}" }
        registerCleanup { cache.release() }
        val mode = glitchModeFor(ctx.param(TransitionParamKeys.MODE) ?: "GLITCH_PRO")
        val effect = GlitchProGlEffect(ctx.compositionStartUs, ctx.compositionEndUs, cache, mode)
        return listOf(OverlayRenderSupport.overlayItem(ctx.pathA, ctx.aTailStartMs, ctx.aEndMs, effect))
    }

    private fun glitchModeFor(raw: String): GlitchMode = when (raw.uppercase()) {
        "GLITCH_DIGITAL" -> GlitchMode.DIGITAL
        "GLITCH_RGB" -> GlitchMode.RGB
        "GLITCH_SCANLINE" -> GlitchMode.SCANLINE
        else -> GlitchMode.PRO
    }
}
