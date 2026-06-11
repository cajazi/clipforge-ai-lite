package com.clipforge.ai.core.transition

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import com.clipforge.ai.core.gl.CrossfadeRenderPlan
import com.clipforge.ai.core.transition.renderers.TransitionParamKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Dual-run parity diagnostic for Phase B.
 *
 * Walks the SAME render plan the executor uses and drives the registry adapters over each
 * transition op, asserting the adapter's emitted items match the op's expected windows and
 * item counts. It does NOT touch the live export path and is never auto-invoked — it is a
 * manual gate to confirm the adapters would produce equivalent output BEFORE the Phase D
 * executor flip.
 *
 * How to use it as a true dual-run:
 *  1. Run a normal legacy export and capture CROSSFADE_EXEC `op[i]=...` lines (windows) and
 *     `DONE durationMs`.
 *  2. Invoke [validate] for the same projectId; capture `PARITY_*` lines.
 *  3. Compare: the adapter's per-op windows/itemCount must equal the legacy op windows.
 *
 * WARNING: [validate] builds frame caches (MediaCodec) and BLOCKS for seconds per transition;
 * it runs on Dispatchers.IO and releases every cache it builds.
 */
@UnstableApi
object OverlayRendererParityHarness {

    private const val TAG = "XFADE_PARITY"

    data class OpResult(
        val index: Int,
        val family: String,
        val expectedItems: Int,
        val emittedItems: Int,
        val firstItemClipStartMs: Long?,
        val firstItemClipEndMs: Long?,
        val expectedClipStartMs: Long,
        val expectedClipEndMs: Long,
        val pass: Boolean,
        val error: String? = null
    )

    /** Returns one [OpResult] per transition op; logs PARITY_PASS/FAIL. Non-throwing. */
    suspend fun validate(context: Context, projectId: String): List<OpResult> = withContext(Dispatchers.IO) {
        // Ensure built-ins are present (idempotent, registry-only).
        TransitionRegistrations.registerBuiltIns()

        val ops = CrossfadeRenderPlan.build(context, projectId)
        val results = ArrayList<OpResult>()
        val cleanups = ArrayList<() -> Unit>()
        var runningTimeMs = 0L
        var index = 0

        try {
            for (op in ops) {
                when (op) {
                    is CrossfadeRenderPlan.Op.PlainClip -> {
                        runningTimeMs += (op.endMs - op.startMs).coerceAtLeast(0L)
                    }
                    is CrossfadeRenderPlan.Op.Crossfade ->
                        results += runOp(context, index, "CROSSFADE", op.pathA, op.aTailStartMs, op.aEndMs,
                            op.pathB, op.bHeadStartMs, op.crossfadeMs, runningTimeMs, TransitionRegistrations.DISSOLVE,
                            expectedItems = 1, params = emptyMap(), cleanups = cleanups).also { runningTimeMs += op.crossfadeMs }
                    is CrossfadeRenderPlan.Op.DipToColor ->
                        results += runOp(context, index, "DIP", op.pathA, op.aTailStartMs, op.aEndMs,
                            op.pathB, op.bHeadStartMs, op.halfDurationMs * 2L, runningTimeMs, TransitionRegistrations.FADE_BLACK,
                            expectedItems = 2,
                            params = mapOf(
                                TransitionParamKeys.HALF_DURATION_MS to op.halfDurationMs.toString(),
                                TransitionParamKeys.COLOR_INT to op.colorInt.toString(),
                                TransitionParamKeys.B_HEAD_END_MS to op.bHeadEndMs.toString()
                            ),
                            cleanups = cleanups).also { runningTimeMs += op.halfDurationMs * 2L }
                    is CrossfadeRenderPlan.Op.Flash ->
                        results += runOp(context, index, "FLASH", op.pathA, op.aTailStartMs, op.aEndMs,
                            op.pathB, op.bHeadStartMs, op.durationMs, runningTimeMs, flashId(op.type),
                            expectedItems = 1,
                            params = mapOf(TransitionParamKeys.FLASH_COLOR_INT to op.colorInt.toString()),
                            cleanups = cleanups).also { runningTimeMs += op.durationMs }
                    is CrossfadeRenderPlan.Op.Slide ->
                        results += runOp(context, index, "SLIDE", op.pathA, op.aTailStartMs, op.aEndMs,
                            op.pathB, op.bHeadStartMs, op.durationMs, runningTimeMs, TransitionRegistrations.SLIDE_LEFT,
                            expectedItems = 1, params = mapOf(TransitionParamKeys.DIRECTION to op.direction),
                            cleanups = cleanups).also { runningTimeMs += op.durationMs }
                    is CrossfadeRenderPlan.Op.Push ->
                        results += runOp(context, index, "PUSH", op.pathA, op.aTailStartMs, op.aEndMs,
                            op.pathB, op.bHeadStartMs, op.durationMs, runningTimeMs, pushId(op.direction),
                            expectedItems = 1, params = mapOf(TransitionParamKeys.DIRECTION to op.direction),
                            cleanups = cleanups).also { runningTimeMs += op.durationMs }
                    is CrossfadeRenderPlan.Op.Zoom ->
                        results += runOp(context, index, "ZOOM", op.pathA, op.aTailStartMs, op.aEndMs,
                            op.pathB, op.bHeadStartMs, op.durationMs, runningTimeMs, TransitionRegistrations.ZOOM_IN,
                            expectedItems = 1, params = mapOf(TransitionParamKeys.MODE to op.mode),
                            cleanups = cleanups).also { runningTimeMs += op.durationMs }
                    is CrossfadeRenderPlan.Op.Rotation ->
                        results += runOp(context, index, "ROTATION", op.pathA, op.aTailStartMs, op.aEndMs,
                            op.pathB, op.bHeadStartMs, op.durationMs, runningTimeMs, rotationId(op.mode),
                            expectedItems = 1, params = mapOf(TransitionParamKeys.MODE to op.mode),
                            cleanups = cleanups).also { runningTimeMs += op.durationMs }
                    is CrossfadeRenderPlan.Op.Cube ->
                        results += runOp(context, index, "CUBE", op.pathA, op.aTailStartMs, op.aEndMs,
                            op.pathB, op.bHeadStartMs, op.durationMs, runningTimeMs, cubeId(op.direction),
                            expectedItems = 1, params = mapOf(TransitionParamKeys.DIRECTION to op.direction),
                            cleanups = cleanups).also { runningTimeMs += op.durationMs }
                    is CrossfadeRenderPlan.Op.Flip ->
                        results += runOp(context, index, "FLIP", op.pathA, op.aTailStartMs, op.aEndMs,
                            op.pathB, op.bHeadStartMs, op.durationMs, runningTimeMs, flipId(op.direction),
                            expectedItems = 1, params = mapOf(TransitionParamKeys.DIRECTION to op.direction),
                            cleanups = cleanups).also { runningTimeMs += op.durationMs }
                    is CrossfadeRenderPlan.Op.PageTurn ->
                        results += runOp(context, index, "PAGE_TURN", op.pathA, op.aTailStartMs, op.aEndMs,
                            op.pathB, op.bHeadStartMs, op.durationMs, runningTimeMs, pageTurnId(op.direction),
                            expectedItems = 1, params = mapOf(TransitionParamKeys.DIRECTION to op.direction),
                            cleanups = cleanups).also { runningTimeMs += op.durationMs }
                    is CrossfadeRenderPlan.Op.WhipPan ->
                        results += runOp(context, index, "WHIP_PAN", op.pathA, op.aTailStartMs, op.aEndMs,
                            op.pathB, op.bHeadStartMs, op.durationMs, runningTimeMs, whipPanId(op.direction),
                            expectedItems = 1, params = mapOf(TransitionParamKeys.DIRECTION to op.direction),
                            cleanups = cleanups).also { runningTimeMs += op.durationMs }
                    is CrossfadeRenderPlan.Op.MotionBlur ->
                        results += runOp(context, index, "MOTION_BLUR", op.pathA, op.aTailStartMs, op.aEndMs,
                            op.pathB, op.bHeadStartMs, op.durationMs, runningTimeMs, motionBlurId(op.direction),
                            expectedItems = 1, params = mapOf(TransitionParamKeys.DIRECTION to op.direction),
                            cleanups = cleanups).also { runningTimeMs += op.durationMs }
                }
                index++
            }
        } finally {
            cleanups.forEach { runCatching { it() } }
        }

        val passed = results.count { it.pass }
        Log.d(TAG, "PARITY_SUMMARY projectId=$projectId transitions=${results.size} passed=$passed failed=${results.size - passed}")
        results
    }

    private fun whipPanId(direction: String): TransitionId = when (direction.uppercase()) {
        "WHIP_PAN_RIGHT" -> TransitionRegistrations.WHIP_PAN_RIGHT
        "WHIP_PAN_UP" -> TransitionRegistrations.WHIP_PAN_UP
        "WHIP_PAN_DOWN" -> TransitionRegistrations.WHIP_PAN_DOWN
        else -> TransitionRegistrations.WHIP_PAN_LEFT
    }

    private fun pushId(direction: String): TransitionId = when (direction.uppercase()) {
        "PUSH_RIGHT" -> TransitionRegistrations.PUSH_RIGHT
        "PUSH_UP" -> TransitionRegistrations.PUSH_UP
        "PUSH_DOWN" -> TransitionRegistrations.PUSH_DOWN
        else -> TransitionRegistrations.PUSH_LEFT
    }

    private fun rotationId(mode: String): TransitionId = when (mode.uppercase()) {
        "ROTATE" -> TransitionRegistrations.ROTATE
        "CAMERA_ROLL" -> TransitionRegistrations.CAMERA_ROLL
        else -> TransitionRegistrations.SPIN
    }

    private fun cubeId(direction: String): TransitionId = when (direction.uppercase()) {
        "CUBE_RIGHT" -> TransitionRegistrations.CUBE_RIGHT
        "CUBE_UP" -> TransitionRegistrations.CUBE_UP
        "CUBE_DOWN" -> TransitionRegistrations.CUBE_DOWN
        else -> TransitionRegistrations.CUBE_LEFT
    }

    private fun flipId(direction: String): TransitionId = when (direction.uppercase()) {
        "FLIP_RIGHT" -> TransitionRegistrations.FLIP_RIGHT
        "FLIP_UP" -> TransitionRegistrations.FLIP_UP
        "FLIP_DOWN" -> TransitionRegistrations.FLIP_DOWN
        else -> TransitionRegistrations.FLIP_LEFT
    }

    private fun pageTurnId(direction: String): TransitionId = when (direction.uppercase()) {
        "PAGE_TURN_RIGHT" -> TransitionRegistrations.PAGE_TURN_RIGHT
        "PAGE_TURN_UP" -> TransitionRegistrations.PAGE_TURN_UP
        "PAGE_TURN_DOWN" -> TransitionRegistrations.PAGE_TURN_DOWN
        else -> TransitionRegistrations.PAGE_TURN_LEFT
    }

    private fun motionBlurId(direction: String): TransitionId = when (direction.uppercase()) {
        "MOTION_BLUR_RIGHT" -> TransitionRegistrations.MOTION_BLUR_RIGHT
        "MOTION_BLUR_UP" -> TransitionRegistrations.MOTION_BLUR_UP
        "MOTION_BLUR_DOWN" -> TransitionRegistrations.MOTION_BLUR_DOWN
        else -> TransitionRegistrations.MOTION_BLUR_LEFT
    }

    private fun flashId(type: String): TransitionId = when (type.uppercase()) {
        "FLASH_BLACK" -> TransitionRegistrations.FLASH_BLACK
        "FLASH_WARM" -> TransitionRegistrations.FLASH_WARM
        "FLASH_BLUE" -> TransitionRegistrations.FLASH_BLUE
        else -> TransitionRegistrations.FLASH_WHITE
    }

    private fun runOp(
        context: Context,
        index: Int,
        family: String,
        pathA: String,
        aTailStartMs: Long,
        aEndMs: Long,
        pathB: String,
        bHeadStartMs: Long,
        durationMs: Long,
        runningTimeMs: Long,
        id: TransitionId,
        expectedItems: Int,
        params: Map<String, String>,
        cleanups: MutableList<() -> Unit>
    ): OpResult {
        val reg = TransitionRegistry.get(id)
        val renderer = reg?.renderer
        return try {
            requireNotNull(renderer) { "no renderer registered for $id" }
            val ctx = SegmentContext(
                context = context,
                outputWidthPx = 720,
                outputHeightPx = 1280,
                pathA = pathA,
                aTailStartMs = aTailStartMs,
                aEndMs = aEndMs,
                pathB = pathB,
                bHeadStartMs = bHeadStartMs,
                durationMs = durationMs,
                compositionStartUs = runningTimeMs * 1000L,
                params = params
            )
            val items = renderer.emit(ctx) { cleanups.add(it) }
            val firstClip = items.firstOrNull()?.mediaItem?.clippingConfiguration
            val startMs = firstClip?.startPositionMs
            val endMs = firstClip?.endPositionMs
            val pass = items.size == expectedItems && startMs == aTailStartMs && endMs == aEndMs
            OpResult(index, family, expectedItems, items.size, startMs, endMs, aTailStartMs, aEndMs, pass).also {
                Log.d(TAG, if (pass) "PARITY_PASS op[$index]=$family items=${items.size} clip=[$startMs..$endMs]"
                else "PARITY_FAIL op[$index]=$family emitted=${items.size}/$expectedItems clip=[$startMs..$endMs] expected=[$aTailStartMs..$aEndMs]")
            }
        } catch (t: Throwable) {
            Log.e(TAG, "PARITY_FAIL op[$index]=$family threw", t)
            OpResult(index, family, expectedItems, 0, null, null, aTailStartMs, aEndMs, pass = false, error = t.message)
        }
    }
}
