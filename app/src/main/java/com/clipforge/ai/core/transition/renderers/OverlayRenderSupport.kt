package com.clipforge.ai.core.transition.renderers

import android.net.Uri
import androidx.media3.common.Effect
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import com.clipforge.ai.core.gl.CrossfadeFrameCache
import java.io.File

/**
 * Shared helpers for the overlay-backed transition renderers.
 *
 * These are FAITHFUL MIRRORS of the corresponding private helpers and constants in
 * CrossfadeExecutor. They are duplicated here ON PURPOSE: Phase B must not modify the
 * executor (protected, §4), and the adapters must produce byte-equivalent items to the
 * legacy path so the dual-run parity gate is meaningful. Phase D unifies these — until
 * then, any change to the executor constants MUST be reflected here (see ParityChecklist).
 */
@UnstableApi
internal object OverlayRenderSupport {

    /** Mirror of CrossfadeExecutor.pathToUri. */
    fun pathToUri(path: String): Uri =
        if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)

    /** Mirror of CrossfadeExecutor.clip. */
    fun clip(path: String, startMs: Long, endMs: Long): MediaItem =
        MediaItem.Builder()
            .setUri(pathToUri(path))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

    /** Build an A-tail item carrying the given video effects (overlay, blur, ...). */
    fun overlayItem(
        path: String,
        startMs: Long,
        endMs: Long,
        vararg videoEffects: Effect
    ): EditedMediaItem =
        EditedMediaItem.Builder(clip(path, startMs, endMs))
            .setEffects(Effects(emptyList(), videoEffects.toList()))
            .build()

    /** Mirror of CrossfadeExecutor.slideCacheFps. */
    fun slideCacheFps(durationMs: Long): Int {
        if (durationMs <= 0L) return MAX_SLIDE_CACHE_FPS
        val budgetedFps = ((MAX_SLIDE_CACHE_FRAMES * 1000L) / durationMs)
            .toInt()
            .coerceAtLeast(MIN_SLIDE_CACHE_FPS)
        return minOf(MAX_SLIDE_CACHE_FPS, budgetedFps)
    }

    /** Crossfade/dissolve cache profile — mirrors the executor's default-ctor usage. */
    fun crossfadeCache(pathB: String, bHeadStartMs: Long, durationMs: Long): CrossfadeFrameCache =
        CrossfadeFrameCache(
            clipPath = pathB,
            startUs = bHeadStartMs * 1000L,
            windowUs = durationMs * 1000L
        )

    /** Slide/Zoom/WhipPan bounded cache profile — mirrors the executor's SLIDE_* constants. */
    fun slideProfileCache(pathB: String, bHeadStartMs: Long, durationMs: Long): CrossfadeFrameCache =
        CrossfadeFrameCache(
            clipPath = pathB,
            startUs = bHeadStartMs * 1000L,
            windowUs = durationMs * 1000L,
            fps = slideCacheFps(durationMs),
            maxDimension = SLIDE_CACHE_MAX_DIMENSION,
            minCoveragePercent = SLIDE_MIN_COVERAGE_PERCENT,
            fallbackCoveragePercent = SLIDE_FAST_SAFE_COVERAGE_PERCENT,
            maxEstimatedBytes = SLIDE_MAX_CACHE_BYTES
        )

    // --- Mirrored constants (keep in lockstep with CrossfadeExecutor) ---
    private const val MIN_SLIDE_CACHE_FPS = 15
    private const val MAX_SLIDE_CACHE_FPS = 30
    private const val MAX_SLIDE_CACHE_FRAMES = 72
    private const val SLIDE_CACHE_MAX_DIMENSION = 720
    private const val SLIDE_MIN_COVERAGE_PERCENT = 95f
    private const val SLIDE_FAST_SAFE_COVERAGE_PERCENT = 80f
    private const val SLIDE_MAX_CACHE_BYTES = 45L * 1024L * 1024L
}

/** Param keys carried on [com.clipforge.ai.core.transition.SegmentContext.params]. */
internal object TransitionParamKeys {
    const val DIRECTION = "direction"   // raw op.direction, e.g. "SLIDE_LEFT" / "WHIP_PAN_RIGHT"
    const val MODE = "mode"             // raw op.mode, e.g. "ZOOM_IN"
    const val COLOR_INT = "colorInt"    // dip color as Int string
    const val HALF_DURATION_MS = "halfDurationMs"
    const val B_HEAD_END_MS = "bHeadEndMs"
}
