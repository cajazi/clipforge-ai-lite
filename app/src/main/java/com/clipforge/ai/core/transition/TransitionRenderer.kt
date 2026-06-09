package com.clipforge.ai.core.transition

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.EditedMediaItem

/**
 * Everything a [TransitionRenderer] needs to bake one boundary segment, modelled directly
 * on the locals the executor already computes per op (paths, A-tail window, B-head start,
 * duration, the composition-global offset, and output canvas). Keeping this a plain data
 * holder is what lets the executor delegate without a per-type `when`.
 *
 * It is deliberately Media3-light: a renderer constructs its own frame cache / effects from
 * these primitives, exactly as the current executor arms do today.
 *
 * @param context             Android context (for MediaCodec / cache building)
 * @param outputWidthPx       export canvas width (e.g. 720)
 * @param outputHeightPx      export canvas height (e.g. 1280)
 * @param pathA               outgoing clip path
 * @param aTailStartMs        start of A's tail window (source ms)
 * @param aEndMs              end of A's tail window (source ms, == clip end)
 * @param pathB               incoming clip path
 * @param bHeadStartMs        start of B's head window (source ms, == trimStart)
 * @param durationMs          transition duration at this boundary
 * @param compositionStartUs  this segment's start on the global composition timeline
 *                            (== runningTimeMs * 1000). Overlays MUST use this for their
 *                            fade window — segment-local 0 was the historical crossfade bug.
 * @param params              free-form per-family parameters (e.g. "direction"->"LEFT",
 *                            "mode"->"IN", "colorInt"->"-16777216"). Typed accessors arrive
 *                            with concrete renderers; kept open here to avoid leaking
 *                            family specifics into the framework core.
 */
@UnstableApi
data class SegmentContext(
    val context: Context,
    val outputWidthPx: Int,
    val outputHeightPx: Int,
    val pathA: String,
    val aTailStartMs: Long,
    val aEndMs: Long,
    val pathB: String,
    val bHeadStartMs: Long,
    val durationMs: Long,
    val compositionStartUs: Long,
    val params: Map<String, String> = emptyMap()
) {
    val compositionEndUs: Long get() = compositionStartUs + durationMs * 1000L
    fun param(key: String): String? = params[key]
}

/**
 * Export strategy for a single transition family. The executor calls [emit] and appends the
 * returned items to the sequence; it does not branch on the concrete transition type.
 *
 * Contract:
 * - [emit] returns the [EditedMediaItem]s for this boundary segment, in composition order.
 * - It must build/own any frame cache it needs and hand teardown to [registerCleanup]; the
 *   caller (the executor, after the Phase D flip) owns *when* cleanup runs — typically after
 *   the transformer completes — exactly as the legacy `caches` list does today. The cleanup
 *   registrar keeps the framework core decoupled from `core/gl` (no cache type leaks here).
 * - It must use [SegmentContext.compositionStartUs] for overlay fade windows (segment-local
 *   0 was the historical crossfade bug).
 * - [emit] may BLOCK (it builds frame caches via MediaCodec) and MUST be called off the main
 *   thread — the executor calls it inside `Dispatchers.IO`.
 *
 * Two implementations are planned: an overlay-backed adapter wrapping today's BitmapOverlay
 * families (Phase B, zero behavior change) and a shader-backed renderer for CapCut-feel
 * effects (later). Both satisfy this one interface.
 */
@UnstableApi
interface TransitionRenderer {

    /** True if this renderer can actually bake the effect into the export. */
    val supportsExport: Boolean

    /**
     * Build the export items for one boundary segment.
     *
     * @param ctx             window/path/output primitives for this boundary
     * @param registerCleanup hand back a teardown lambda (e.g. `{ cache.release() }`); the
     *                        caller runs it after export completes. May be called 0..n times.
     */
    fun emit(ctx: SegmentContext, registerCleanup: (() -> Unit) -> Unit): List<EditedMediaItem>
}
