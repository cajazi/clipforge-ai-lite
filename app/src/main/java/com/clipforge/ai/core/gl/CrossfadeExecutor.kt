package com.clipforge.ai.core.gl

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.effect.Presentation
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.clipforge.ai.core.transition.SegmentContext
import com.clipforge.ai.core.transition.TransitionId
import com.clipforge.ai.core.transition.TransitionRegistrations
import com.clipforge.ai.core.transition.TransitionRegistry
import com.clipforge.ai.core.transition.renderers.TransitionParamKeys
import com.clipforge.ai.data.local.database.ClipForgeDatabase
import com.clipforge.ai.domain.model.AspectRatio
import com.clipforge.ai.domain.model.ExportQuality
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Executor: turns a CrossfadeRenderPlan into one concatenated EditedMediaItemSequence
 * and renders it. Plain ops -> plain items; Crossfade ops -> clip A's tail carrying an
 * OverlayEffect with clip B's head, alpha-ramped.
 *
 * CRITICAL invariants learned the hard way:
 *  - Each crossfade overlay's fade window must be COMPOSITION-GLOBAL: it equals the
 *    running composition time where that item starts. Tracked via runningTimeMs as we
 *    walk the plan, so crossfades after the first get the right offset.
 *  - All frame caches are pre-built BEFORE transformer.start() (off the render thread).
 *    Lazy build during getBitmap() stalls the pipeline and trips the 10s muxer watchdog.
 *
 * Interim fast path: CrossfadeFrameCache (MediaMetadataRetriever). Later -> MediaCodec.
 */
@UnstableApi
object CrossfadeExecutor {

    private const val TAG = "CROSSFADE_EXEC"
    private val REAL_CROSSFADE_TYPES = setOf("DISSOLVE", "CROSS_DISSOLVE")

    /**
     * PHASE D EXECUTOR FLIP — registry-driven transition dispatch.
     *
     * When false (default), every transition op is built by the legacy per-family `when`
     * arms below — fully retained, zero behavior change. When true, transition ops are
     * dispatched through TransitionRegistry instead (PlainClip always stays legacy/inline).
     *
     * This is the master rollback switch: flip to false to instantly revert to legacy. Do
     * NOT enable in production until the §4 A/B validation matrix passes on device.
     */
    private const val USE_REGISTRY_DISPATCH = true

    /**
     * Flat, op-type-aware extraction of everything the generic dispatch needs. This is the
     * ONLY place that knows the concrete Op subtypes — it returns DATA, never build logic,
     * so the dispatch site stays open/closed. Returns null for PlainClip (always legacy).
     *
     * occupiedMs == composition time the op advances runningTimeMs by == the FULL transition
     * duration for every family (overlap = duration; dip = half*2). runningTimeMs ownership
     * therefore stays entirely with the executor.
     */
    private data class Dispatch(
        val id: TransitionId,
        val pathA: String,
        val aTailStartMs: Long,
        val aEndMs: Long,
        val pathB: String,
        val bHeadStartMs: Long,
        val windowDurationMs: Long,
        val occupiedMs: Long,
        val params: Map<String, String>
    )

    private fun dispatchFor(op: CrossfadeRenderPlan.Op): Dispatch? = when (op) {
        is CrossfadeRenderPlan.Op.PlainClip -> null
        is CrossfadeRenderPlan.Op.Crossfade -> Dispatch(
            id = TransitionRegistrations.DISSOLVE,
            pathA = op.pathA, aTailStartMs = op.aTailStartMs, aEndMs = op.aEndMs,
            pathB = op.pathB, bHeadStartMs = op.bHeadStartMs,
            windowDurationMs = op.crossfadeMs, occupiedMs = op.crossfadeMs,
            params = emptyMap()
        )
        is CrossfadeRenderPlan.Op.DipToColor -> Dispatch(
            id = if (op.colorInt == android.graphics.Color.WHITE) TransitionRegistrations.FADE_WHITE
            else TransitionRegistrations.FADE_BLACK,
            pathA = op.pathA, aTailStartMs = op.aTailStartMs, aEndMs = op.aEndMs,
            pathB = op.pathB, bHeadStartMs = op.bHeadStartMs,
            windowDurationMs = op.halfDurationMs * 2L, occupiedMs = op.halfDurationMs * 2L,
            params = mapOf(
                TransitionParamKeys.HALF_DURATION_MS to op.halfDurationMs.toString(),
                TransitionParamKeys.COLOR_INT to op.colorInt.toString(),
                TransitionParamKeys.B_HEAD_END_MS to op.bHeadEndMs.toString()
            )
        )
        is CrossfadeRenderPlan.Op.Flash -> Dispatch(
            id = flashIdFor(op.type),
            pathA = op.pathA, aTailStartMs = op.aTailStartMs, aEndMs = op.aEndMs,
            pathB = op.pathB, bHeadStartMs = op.bHeadStartMs,
            windowDurationMs = op.durationMs, occupiedMs = op.durationMs,
            params = mapOf(TransitionParamKeys.FLASH_COLOR_INT to op.colorInt.toString())
        )
        is CrossfadeRenderPlan.Op.Slide -> Dispatch(
            id = when (op.direction.uppercase()) {
                "SLIDE_RIGHT" -> TransitionRegistrations.SLIDE_RIGHT
                "SLIDE_UP" -> TransitionRegistrations.SLIDE_UP
                "SLIDE_DOWN" -> TransitionRegistrations.SLIDE_DOWN
                else -> TransitionRegistrations.SLIDE_LEFT
            },
            pathA = op.pathA, aTailStartMs = op.aTailStartMs, aEndMs = op.aEndMs,
            pathB = op.pathB, bHeadStartMs = op.bHeadStartMs,
            windowDurationMs = op.durationMs, occupiedMs = op.durationMs,
            params = mapOf(TransitionParamKeys.DIRECTION to op.direction)
        )
        is CrossfadeRenderPlan.Op.Push -> Dispatch(
            id = when (op.direction.uppercase()) {
                "PUSH_RIGHT" -> TransitionRegistrations.PUSH_RIGHT
                "PUSH_UP" -> TransitionRegistrations.PUSH_UP
                "PUSH_DOWN" -> TransitionRegistrations.PUSH_DOWN
                else -> TransitionRegistrations.PUSH_LEFT
            },
            pathA = op.pathA, aTailStartMs = op.aTailStartMs, aEndMs = op.aEndMs,
            pathB = op.pathB, bHeadStartMs = op.bHeadStartMs,
            windowDurationMs = op.durationMs, occupiedMs = op.durationMs,
            params = mapOf(TransitionParamKeys.DIRECTION to op.direction)
        )
        is CrossfadeRenderPlan.Op.Zoom -> Dispatch(
            id = if (op.mode.uppercase() == "ZOOM_OUT") TransitionRegistrations.ZOOM_OUT
            else TransitionRegistrations.ZOOM_IN,
            pathA = op.pathA, aTailStartMs = op.aTailStartMs, aEndMs = op.aEndMs,
            pathB = op.pathB, bHeadStartMs = op.bHeadStartMs,
            windowDurationMs = op.durationMs, occupiedMs = op.durationMs,
            params = mapOf(TransitionParamKeys.MODE to op.mode)
        )
        is CrossfadeRenderPlan.Op.Rotation -> Dispatch(
            id = when (op.mode.uppercase()) {
                "ROTATE" -> TransitionRegistrations.ROTATE
                "CAMERA_ROLL" -> TransitionRegistrations.CAMERA_ROLL
                else -> TransitionRegistrations.SPIN
            },
            pathA = op.pathA, aTailStartMs = op.aTailStartMs, aEndMs = op.aEndMs,
            pathB = op.pathB, bHeadStartMs = op.bHeadStartMs,
            windowDurationMs = op.durationMs, occupiedMs = op.durationMs,
            params = mapOf(TransitionParamKeys.MODE to op.mode)
        )
        is CrossfadeRenderPlan.Op.Cube -> Dispatch(
            id = when (op.direction.uppercase()) {
                "CUBE_RIGHT" -> TransitionRegistrations.CUBE_RIGHT
                "CUBE_UP" -> TransitionRegistrations.CUBE_UP
                "CUBE_DOWN" -> TransitionRegistrations.CUBE_DOWN
                else -> TransitionRegistrations.CUBE_LEFT
            },
            pathA = op.pathA, aTailStartMs = op.aTailStartMs, aEndMs = op.aEndMs,
            pathB = op.pathB, bHeadStartMs = op.bHeadStartMs,
            windowDurationMs = op.durationMs, occupiedMs = op.durationMs,
            params = mapOf(TransitionParamKeys.DIRECTION to op.direction)
        )
        is CrossfadeRenderPlan.Op.Flip -> Dispatch(
            id = when (op.direction.uppercase()) {
                "FLIP_RIGHT" -> TransitionRegistrations.FLIP_RIGHT
                "FLIP_UP" -> TransitionRegistrations.FLIP_UP
                "FLIP_DOWN" -> TransitionRegistrations.FLIP_DOWN
                else -> TransitionRegistrations.FLIP_LEFT
            },
            pathA = op.pathA, aTailStartMs = op.aTailStartMs, aEndMs = op.aEndMs,
            pathB = op.pathB, bHeadStartMs = op.bHeadStartMs,
            windowDurationMs = op.durationMs, occupiedMs = op.durationMs,
            params = mapOf(TransitionParamKeys.DIRECTION to op.direction)
        )
        is CrossfadeRenderPlan.Op.PageTurn -> Dispatch(
            id = when (op.direction.uppercase()) {
                "PAGE_TURN_RIGHT" -> TransitionRegistrations.PAGE_TURN_RIGHT
                "PAGE_TURN_UP" -> TransitionRegistrations.PAGE_TURN_UP
                "PAGE_TURN_DOWN" -> TransitionRegistrations.PAGE_TURN_DOWN
                else -> TransitionRegistrations.PAGE_TURN_LEFT
            },
            pathA = op.pathA, aTailStartMs = op.aTailStartMs, aEndMs = op.aEndMs,
            pathB = op.pathB, bHeadStartMs = op.bHeadStartMs,
            windowDurationMs = op.durationMs, occupiedMs = op.durationMs,
            params = mapOf(TransitionParamKeys.DIRECTION to op.direction)
        )
        is CrossfadeRenderPlan.Op.WhipPan -> Dispatch(
            id = when (op.direction.uppercase()) {
                "WHIP_PAN_RIGHT" -> TransitionRegistrations.WHIP_PAN_RIGHT
                "WHIP_PAN_UP" -> TransitionRegistrations.WHIP_PAN_UP
                "WHIP_PAN_DOWN" -> TransitionRegistrations.WHIP_PAN_DOWN
                else -> TransitionRegistrations.WHIP_PAN_LEFT
            },
            pathA = op.pathA, aTailStartMs = op.aTailStartMs, aEndMs = op.aEndMs,
            pathB = op.pathB, bHeadStartMs = op.bHeadStartMs,
            windowDurationMs = op.durationMs, occupiedMs = op.durationMs,
            params = mapOf(TransitionParamKeys.DIRECTION to op.direction)
        )
        is CrossfadeRenderPlan.Op.MotionBlur -> Dispatch(
            id = when (op.direction.uppercase()) {
                "MOTION_BLUR_RIGHT" -> TransitionRegistrations.MOTION_BLUR_RIGHT
                "MOTION_BLUR_UP" -> TransitionRegistrations.MOTION_BLUR_UP
                "MOTION_BLUR_DOWN" -> TransitionRegistrations.MOTION_BLUR_DOWN
                else -> TransitionRegistrations.MOTION_BLUR_LEFT
            },
            pathA = op.pathA, aTailStartMs = op.aTailStartMs, aEndMs = op.aEndMs,
            pathB = op.pathB, bHeadStartMs = op.bHeadStartMs,
            windowDurationMs = op.durationMs, occupiedMs = op.durationMs,
            params = mapOf(TransitionParamKeys.DIRECTION to op.direction)
        )
    }

    sealed class Result {
        data class Done(val outputPath: String, val bytes: Long, val durationMs: Long) : Result()
        data class Error(val message: String) : Result()
    }

    private fun pathToUri(path: String): Uri =
        if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)

    private fun clip(path: String, startMs: Long, endMs: Long): MediaItem =
        MediaItem.Builder()
            .setUri(pathToUri(path))
            .setClippingConfiguration(
                MediaItem.ClippingConfiguration.Builder()
                    .setStartPositionMs(startMs)
                    .setEndPositionMs(endMs)
                    .build()
            )
            .build()

    private fun readDurationMs(path: String): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(path)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } catch (e: Exception) {
            0L
        } finally {
            try { mmr.release() } catch (_: Exception) {}
        }
    }

    private fun slideCacheFps(durationMs: Long): Int {
        if (durationMs <= 0L) return MAX_SLIDE_CACHE_FPS
        val budgetedFps = ((MAX_SLIDE_CACHE_FRAMES * 1000L) / durationMs)
            .toInt()
            .coerceAtLeast(MIN_SLIDE_CACHE_FPS)
        return minOf(MAX_SLIDE_CACHE_FPS, budgetedFps)
    }

    private fun blurVectorForDirection(raw: String, prefix: String): Pair<Float, Float> {
        val dir = SlideOverlay.Direction.valueOf(raw.removePrefix(prefix))
        return when (dir) {
            SlideOverlay.Direction.LEFT -> 1f to 0f
            SlideOverlay.Direction.RIGHT -> -1f to 0f
            SlideOverlay.Direction.UP -> 0f to 1f
            SlideOverlay.Direction.DOWN -> 0f to -1f
        }
    }

    private fun pushVectorForDirection(raw: String, prefix: String): Pair<Float, Float> {
        val dir = SlideOverlay.Direction.valueOf(raw.removePrefix(prefix))
        return when (dir) {
            SlideOverlay.Direction.LEFT -> -1f to 0f
            SlideOverlay.Direction.RIGHT -> 1f to 0f
            SlideOverlay.Direction.UP -> 0f to 1f
            SlideOverlay.Direction.DOWN -> 0f to -1f
        }
    }

    private fun rotationModeFor(raw: String): RotationMode = when (raw.uppercase()) {
        "ROTATE" -> RotationMode.ROTATE
        "CAMERA_ROLL" -> RotationMode.CAMERA_ROLL
        else -> RotationMode.SPIN
    }

    private fun cubeDirectionFor(raw: String): CubeDirection = when (raw.uppercase()) {
        "CUBE_RIGHT" -> CubeDirection.RIGHT
        "CUBE_UP" -> CubeDirection.UP
        "CUBE_DOWN" -> CubeDirection.DOWN
        else -> CubeDirection.LEFT
    }

    private fun flipDirectionFor(raw: String): FlipDirection = when (raw.uppercase()) {
        "FLIP_RIGHT" -> FlipDirection.RIGHT
        "FLIP_UP" -> FlipDirection.UP
        "FLIP_DOWN" -> FlipDirection.DOWN
        else -> FlipDirection.LEFT
    }

    private fun pageTurnDirectionFor(raw: String): PageTurnDirection = when (raw.uppercase()) {
        "PAGE_TURN_RIGHT" -> PageTurnDirection.RIGHT
        "PAGE_TURN_UP" -> PageTurnDirection.UP
        "PAGE_TURN_DOWN" -> PageTurnDirection.DOWN
        else -> PageTurnDirection.LEFT
    }

    private fun flashIdFor(raw: String): TransitionId = when (raw.uppercase()) {
        "FLASH_BLACK" -> TransitionRegistrations.FLASH_BLACK
        "FLASH_WARM" -> TransitionRegistrations.FLASH_WARM
        "FLASH_BLUE" -> TransitionRegistrations.FLASH_BLUE
        else -> TransitionRegistrations.FLASH_WHITE
    }

    private fun describeOp(index: Int, op: CrossfadeRenderPlan.Op): String =
        when (op) {
            is CrossfadeRenderPlan.Op.PlainClip ->
                "op[$index]=PLAIN path=${op.path} startMs=${op.startMs} endMs=${op.endMs} durationMs=${op.endMs - op.startMs}"
            is CrossfadeRenderPlan.Op.Crossfade ->
                "op[$index]=XFADE pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} durationMs=${op.crossfadeMs}"
            is CrossfadeRenderPlan.Op.DipToColor ->
                "op[$index]=DIP pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} bHeadEndMs=${op.bHeadEndMs} halfMs=${op.halfDurationMs} color=${op.colorInt}"
            is CrossfadeRenderPlan.Op.Flash ->
                "op[$index]=FLASH pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} durationMs=${op.durationMs} type=${op.type} color=${op.colorInt}"
            is CrossfadeRenderPlan.Op.Slide ->
                "op[$index]=SLIDE pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} durationMs=${op.durationMs} direction=${op.direction}"
            is CrossfadeRenderPlan.Op.Push ->
                "op[$index]=PUSH pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} durationMs=${op.durationMs} direction=${op.direction}"
            is CrossfadeRenderPlan.Op.Zoom ->
                "op[$index]=ZOOM pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} durationMs=${op.durationMs} mode=${op.mode}"
            is CrossfadeRenderPlan.Op.Rotation ->
                "op[$index]=ROTATION pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} durationMs=${op.durationMs} mode=${op.mode}"
            is CrossfadeRenderPlan.Op.Cube ->
                "op[$index]=CUBE pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} durationMs=${op.durationMs} direction=${op.direction}"
            is CrossfadeRenderPlan.Op.Flip ->
                "op[$index]=FLIP pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} durationMs=${op.durationMs} direction=${op.direction}"
            is CrossfadeRenderPlan.Op.PageTurn ->
                "op[$index]=PAGE_TURN pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} durationMs=${op.durationMs} direction=${op.direction}"
            is CrossfadeRenderPlan.Op.WhipPan ->
                "op[$index]=WHIP_PAN pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} durationMs=${op.durationMs} direction=${op.direction}"
            is CrossfadeRenderPlan.Op.MotionBlur ->
                "op[$index]=MOTION_BLUR pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} durationMs=${op.durationMs} direction=${op.direction}"
        }

    /**
     * GENERALIZED entry: walk the whole render plan, build all crossfade segments with
     * correct cumulative composition offsets, concatenate, render. Handles any number of
     * clips and dissolve boundaries. Must be called from a coroutine on Main.
     */
    /** Map a project's stored aspectRatio + exportQuality strings to even output dims.
     *  Short side = quality (720/1080); long side derived from the ratio. Defaults 720x1280. */
    private fun outputDimensions(aspectRatioName: String?, exportQualityName: String?): Pair<Int, Int> {
        val ratio = try { AspectRatio.valueOf(aspectRatioName ?: "") } catch (_: Exception) { AspectRatio.RATIO_9_16 }
        val quality = try { ExportQuality.valueOf(exportQualityName ?: "") } catch (_: Exception) { ExportQuality.QUALITY_720P }
        val shortSide = quality.height // 720 or 1080
        val wR = ratio.widthRatio
        val hR = ratio.heightRatio
        var w: Int
        var h: Int
        if (wR <= hR) { w = shortSide; h = shortSide * hR / wR } // portrait/square
        else { h = shortSide; w = shortSide * wR / hR }          // landscape
        w = (w / 2) * 2
        h = (h / 2) * 2
        return w to h
    }
    suspend fun renderProjectTimeline(
        context: Context,
        projectId: String,
        onStage: (String) -> Unit = {},
        onProgress: (Int) -> Unit,
        onResult: (Result) -> Unit
    ) {
        Log.d(
            TAG,
            "RENDER_PROJECT_START projectId=$projectId device=${Build.MANUFACTURER}/${Build.MODEL} " +
                "sdk=${Build.VERSION.SDK_INT} thread=${Thread.currentThread().name}"
        )
        val ops = CrossfadeRenderPlan.build(context, projectId)
        Log.d(TAG, "PLAN_BUILT projectId=$projectId opCount=${ops.size}")
        ops.forEachIndexed { index, op -> Log.d(TAG, describeOp(index, op)) }
        val projectRow = try { ClipForgeDatabase.getInstance(context).projectDao().getProjectById(projectId) } catch (_: Exception) { null }
        val (outW, outH) = outputDimensions(projectRow?.aspectRatio, projectRow?.exportQuality)
        Log.d(TAG, "output dims ${outW}x${outH} from aspect=${projectRow?.aspectRatio} quality=${projectRow?.exportQuality}")
        if (ops.isEmpty()) {
            onResult(Result.Error("empty render plan"))
            return
        }

        val outputFile = File(context.getExternalFilesDir(null), "xfade_timeline_${System.currentTimeMillis()}.mp4")
        if (outputFile.exists()) outputFile.delete()
        Log.d(TAG, "OUTPUT_FILE path=${outputFile.absolutePath} parentExists=${outputFile.parentFile?.exists()} parentWritable=${outputFile.parentFile?.canWrite()}")

        // Pre-build all crossfade caches off the render thread, computing each item's
        // composition offset from the running timeline position.
        data class BuiltItem(val item: EditedMediaItem)
        val items = ArrayList<EditedMediaItem>()
        val caches = ArrayList<CrossfadeFrameCache>()
        // Phase D: cleanup lambdas registered by registry-dispatched renderers (e.g. cache
        // release). Empty and unused when USE_REGISTRY_DISPATCH is false.
        val dispatchCleanups = ArrayList<() -> Unit>()
        var runningTimeMs = 0L

        if (USE_REGISTRY_DISPATCH) TransitionRegistrations.registerBuiltIns()

        onStage("Preparing transitions...")
        withContext(Dispatchers.IO) {
            for ((index, op) in ops.withIndex()) {
                Log.d(TAG, "BUILD_ITEM_BEFORE ${describeOp(index, op)} runningTimeMs=$runningTimeMs thread=${Thread.currentThread().name}")
                try {
                    val dispatch = if (USE_REGISTRY_DISPATCH) dispatchFor(op) else null
                    if (dispatch != null) {
                        val reg = TransitionRegistry.get(dispatch.id)
                            ?: throw IllegalStateException("no registry entry for ${dispatch.id} (op index=$index)")
                        val renderer = reg.renderer
                            ?: throw IllegalStateException("no renderer for ${dispatch.id} (op index=$index)")
                        onStage(reg.descriptor.stageMessage)
                        val ctx = SegmentContext(
                            context = context,
                            outputWidthPx = outW,
                            outputHeightPx = outH,
                            pathA = dispatch.pathA,
                            aTailStartMs = dispatch.aTailStartMs,
                            aEndMs = dispatch.aEndMs,
                            pathB = dispatch.pathB,
                            bHeadStartMs = dispatch.bHeadStartMs,
                            durationMs = dispatch.windowDurationMs,
                            compositionStartUs = runningTimeMs * 1000L,
                            params = dispatch.params
                        )
                        Log.d(TAG, "DISPATCH index=$index id=${dispatch.id} @t=$runningTimeMs windowMs=${dispatch.windowDurationMs} occupiedMs=${dispatch.occupiedMs} params=${dispatch.params}")
                        val emitted = renderer.emit(ctx) { dispatchCleanups.add(it) }
                        items.addAll(emitted)
                        runningTimeMs += dispatch.occupiedMs
                    } else when (op) {
                    is CrossfadeRenderPlan.Op.PlainClip -> {
                        val durMs = (op.endMs - op.startMs).coerceAtLeast(0L)
                        Log.d(TAG, "PLAIN_ITEM_CREATE_BEFORE index=$index durMs=$durMs")
                        items.add(EditedMediaItem.Builder(clip(op.path, op.startMs, op.endMs)).build())
                        Log.d(TAG, "PLAIN_ITEM_CREATE_AFTER index=$index itemCount=${items.size}")
                        Log.d(TAG, "PLAIN ${op.path.substringAfterLast('/')} [${op.startMs}..${op.endMs}] @t=$runningTimeMs dur=$durMs")
                        runningTimeMs += durMs
                    }
                    is CrossfadeRenderPlan.Op.Crossfade -> {
                        onStage("Preparing dissolve transition...")
                        val fadeStartUs = runningTimeMs * 1000L
                        val fadeEndUs = (runningTimeMs + op.crossfadeMs) * 1000L
                        val cache = CrossfadeFrameCache(
                            clipPath = op.pathB,
                            startUs = op.bHeadStartMs * 1000L,
                            windowUs = op.crossfadeMs * 1000L
                        )
                        Log.d(TAG, "XFADE_CACHE_BUILD_BEFORE index=$index pathB=${op.pathB} startUs=${op.bHeadStartMs * 1000L} windowUs=${op.crossfadeMs * 1000L}")
                        cache.build()
                        Log.d(TAG, "XFADE_CACHE_BUILD_AFTER index=$index empty=${cache.isEmpty()}")
                        if (cache.isEmpty()) {
                            throw IllegalStateException("Crossfade cache empty for op index=$index pathB=${op.pathB}")
                        }
                        caches.add(cache)
                        Log.d(TAG, "XFADE_OVERLAY_CREATE_BEFORE index=$index fadeStartUs=$fadeStartUs fadeEndUs=$fadeEndUs")
                        val overlay = CrossfadeBitmapOverlay(cache, fadeStartUs, fadeEndUs)
                        Log.d(TAG, "XFADE_OVERLAY_CREATE_AFTER index=$index")
                        Log.d(TAG, "XFADE_ITEM_CREATE_BEFORE index=$index")
                        items.add(
                            EditedMediaItem.Builder(clip(op.pathA, op.aTailStartMs, op.aEndMs))
                                .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(overlay)))))
                                .build()
                        )
                        Log.d(TAG, "XFADE_ITEM_CREATE_AFTER index=$index itemCount=${items.size}")
                        Log.d(TAG, "XFADE ${op.crossfadeMs}ms A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}] B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}] @t=$runningTimeMs fade=[$fadeStartUs..$fadeEndUs] cacheFrames=${if (cache.isEmpty()) 0 else 1}")
                        runningTimeMs += op.crossfadeMs
                    }
                    is CrossfadeRenderPlan.Op.DipToColor -> {
                        onStage("Preparing fade transition...")
                        val half = op.halfDurationMs
                        // A-tail fades down to the color.
                        val fadeOutStartUs = runningTimeMs * 1000L
                        val fadeOutEndUs = (runningTimeMs + half) * 1000L
                        Log.d(TAG, "DIP_OVERLAY_A_CREATE_BEFORE index=$index fadeOutStartUs=$fadeOutStartUs fadeOutEndUs=$fadeOutEndUs")
                        val overlayA = DipToColorOverlay(op.colorInt, fadeOutStartUs, fadeOutEndUs, fadeOut = true)
                        Log.d(TAG, "DIP_ITEM_A_CREATE_BEFORE index=$index")
                        items.add(
                            EditedMediaItem.Builder(clip(op.pathA, op.aTailStartMs, op.aEndMs))
                                .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(overlayA)))))
                                .build()
                        )
                        Log.d(TAG, "DIP_ITEM_A_CREATE_AFTER index=$index itemCount=${items.size}")
                        runningTimeMs += half
                        // B-head fades up from the color.
                        val fadeInStartUs = runningTimeMs * 1000L
                        val fadeInEndUs = (runningTimeMs + half) * 1000L
                        Log.d(TAG, "DIP_OVERLAY_B_CREATE_BEFORE index=$index fadeInStartUs=$fadeInStartUs fadeInEndUs=$fadeInEndUs")
                        val overlayB = DipToColorOverlay(op.colorInt, fadeInStartUs, fadeInEndUs, fadeOut = false)
                        Log.d(TAG, "DIP_ITEM_B_CREATE_BEFORE index=$index")
                        items.add(
                            EditedMediaItem.Builder(clip(op.pathB, op.bHeadStartMs, op.bHeadEndMs))
                                .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(overlayB)))))
                                .build()
                        )
                        Log.d(TAG, "DIP_ITEM_B_CREATE_AFTER index=$index itemCount=${items.size}")
                        Log.d(TAG, "DIP color=${op.colorInt} half=${half}ms A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}]@$fadeOutStartUs B=${op.pathB.substringAfterLast('/')}[${op.bHeadStartMs}..${op.bHeadEndMs}]@$fadeInStartUs fallback=NO")
                        runningTimeMs += half
                    }
                    is CrossfadeRenderPlan.Op.Flash -> {
                        onStage("Preparing flash transition...")
                        val flashStartUs = runningTimeMs * 1000L
                        val flashEndUs = (runningTimeMs + op.durationMs) * 1000L
                        val flashFps = slideCacheFps(op.durationMs)
                        Log.d(
                            TAG,
                            "FLASH_CACHE_PROFILE index=$index durationMs=${op.durationMs} fps=$flashFps " +
                                "maxDimension=$SLIDE_CACHE_MAX_DIMENSION minCoverage=$SLIDE_MIN_COVERAGE_PERCENT " +
                                "fallbackCoverage=$SLIDE_FAST_SAFE_COVERAGE_PERCENT maxFrames=$MAX_SLIDE_CACHE_FRAMES " +
                                "maxBytes=$SLIDE_MAX_CACHE_BYTES"
                        )
                        val cache = CrossfadeFrameCache(
                            clipPath = op.pathB,
                            startUs = op.bHeadStartMs * 1000L,
                            windowUs = op.durationMs * 1000L,
                            fps = flashFps,
                            maxDimension = SLIDE_CACHE_MAX_DIMENSION,
                            minCoveragePercent = SLIDE_MIN_COVERAGE_PERCENT,
                            fallbackCoveragePercent = SLIDE_FAST_SAFE_COVERAGE_PERCENT,
                            maxEstimatedBytes = SLIDE_MAX_CACHE_BYTES
                        )
                        Log.d(TAG, "FLASH_CACHE_BUILD_BEFORE index=$index pathB=${op.pathB} startUs=${op.bHeadStartMs * 1000L} windowUs=${op.durationMs * 1000L}")
                        cache.build()
                        Log.d(TAG, "FLASH_CACHE_BUILD_AFTER index=$index empty=${cache.isEmpty()}")
                        if (cache.isEmpty()) {
                            throw IllegalStateException("Flash cache empty for op index=$index pathB=${op.pathB}")
                        }
                        caches.add(cache)

                        val reveal = FlashRevealOverlay(cache, flashStartUs, flashEndUs)
                        val flash = FlashColorOverlay(op.colorInt, flashStartUs, flashEndUs)
                        Log.d(TAG, "FLASH_ITEM_CREATE_BEFORE index=$index type=${op.type} color=${op.colorInt} pathA=${op.pathA} clip=[${op.aTailStartMs}..${op.aEndMs}]")
                        items.add(
                            EditedMediaItem.Builder(clip(op.pathA, op.aTailStartMs, op.aEndMs))
                                .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(reveal, flash)))))
                                .build()
                        )
                        Log.d(TAG, "FLASH_ITEM_CREATE_AFTER index=$index itemCount=${items.size}")
                        Log.d(TAG, "FLASH type=${op.type} color=${op.colorInt} ${op.durationMs}ms A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}] B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}] @t=$runningTimeMs flash=[$flashStartUs..$flashEndUs] fallback=NO")
                        runningTimeMs += op.durationMs
                    }
                    is CrossfadeRenderPlan.Op.Slide -> {
                        onStage("Preparing slide transition...")
                        val slideStartUs = runningTimeMs * 1000L
                        val slideEndUs = (runningTimeMs + op.durationMs) * 1000L
                        val slideFps = slideCacheFps(op.durationMs)
                        Log.d(
                            TAG,
                            "SLIDE_CACHE_PROFILE index=$index durationMs=${op.durationMs} fps=$slideFps " +
                                "maxDimension=$SLIDE_CACHE_MAX_DIMENSION minCoverage=$SLIDE_MIN_COVERAGE_PERCENT " +
                                "fallbackCoverage=$SLIDE_FAST_SAFE_COVERAGE_PERCENT maxFrames=$MAX_SLIDE_CACHE_FRAMES " +
                                "maxBytes=$SLIDE_MAX_CACHE_BYTES"
                        )
                        val cache = CrossfadeFrameCache(
                            clipPath = op.pathB,
                            startUs = op.bHeadStartMs * 1000L,
                            windowUs = op.durationMs * 1000L,
                            fps = slideFps,
                            maxDimension = SLIDE_CACHE_MAX_DIMENSION,
                            minCoveragePercent = SLIDE_MIN_COVERAGE_PERCENT,
                            fallbackCoveragePercent = SLIDE_FAST_SAFE_COVERAGE_PERCENT,
                            maxEstimatedBytes = SLIDE_MAX_CACHE_BYTES
                        )
                        Log.d(TAG, "SLIDE_CACHE_BUILD_BEFORE index=$index pathB=${op.pathB} startUs=${op.bHeadStartMs * 1000L} windowUs=${op.durationMs * 1000L}")
                        cache.build()
                        Log.d(TAG, "SLIDE_CACHE_BUILD_AFTER index=$index empty=${cache.isEmpty()}")
                        if (cache.isEmpty()) {
                            throw IllegalStateException("Slide cache empty for op index=$index pathB=${op.pathB}")
                        }
                        caches.add(cache)
                        val directionName = op.direction.removePrefix("SLIDE_")
                        Log.d(TAG, "SLIDE_DIRECTION_PARSE_BEFORE index=$index raw=${op.direction} parsed=$directionName")
                        val dir = SlideOverlay.Direction.valueOf(directionName)
                        Log.d(TAG, "SLIDE_DIRECTION_PARSE_AFTER index=$index dir=$dir")
                        Log.d(TAG, "SLIDE_OVERLAY_CREATE_BEFORE index=$index slideStartUs=$slideStartUs slideEndUs=$slideEndUs dir=$dir")
                        val overlay = SlideOverlay(cache, slideStartUs, slideEndUs, dir)
                        Log.d(TAG, "SLIDE_OVERLAY_CREATE_AFTER index=$index")
                        Log.d(TAG, "SLIDE_ITEM_CREATE_BEFORE index=$index pathA=${op.pathA} clip=[${op.aTailStartMs}..${op.aEndMs}]")
                        items.add(
                            EditedMediaItem.Builder(clip(op.pathA, op.aTailStartMs, op.aEndMs))
                                .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(overlay)))))
                                .build()
                        )
                        Log.d(TAG, "SLIDE_ITEM_CREATE_AFTER index=$index itemCount=${items.size}")
                        Log.d(TAG, "SLIDE dir=${op.direction} ${op.durationMs}ms A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}] B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}] @t=$runningTimeMs slide=[$slideStartUs..$slideEndUs] fallback=NO")
                        runningTimeMs += op.durationMs
                    }
                    is CrossfadeRenderPlan.Op.Push -> {
                        onStage("Preparing push transition...")
                        val pushStartUs = runningTimeMs * 1000L
                        val pushEndUs = (runningTimeMs + op.durationMs) * 1000L
                        val pushFps = slideCacheFps(op.durationMs)
                        Log.d(
                            TAG,
                            "PUSH_CACHE_PROFILE index=$index durationMs=${op.durationMs} fps=$pushFps " +
                                "maxDimension=$SLIDE_CACHE_MAX_DIMENSION minCoverage=$SLIDE_MIN_COVERAGE_PERCENT " +
                                "fallbackCoverage=$SLIDE_FAST_SAFE_COVERAGE_PERCENT maxFrames=$MAX_SLIDE_CACHE_FRAMES " +
                                "maxBytes=$SLIDE_MAX_CACHE_BYTES"
                        )
                        val cache = CrossfadeFrameCache(
                            clipPath = op.pathB,
                            startUs = op.bHeadStartMs * 1000L,
                            windowUs = op.durationMs * 1000L,
                            fps = pushFps,
                            maxDimension = SLIDE_CACHE_MAX_DIMENSION,
                            minCoveragePercent = SLIDE_MIN_COVERAGE_PERCENT,
                            fallbackCoveragePercent = SLIDE_FAST_SAFE_COVERAGE_PERCENT,
                            maxEstimatedBytes = SLIDE_MAX_CACHE_BYTES
                        )
                        Log.d(TAG, "PUSH_CACHE_BUILD_BEFORE index=$index pathB=${op.pathB} startUs=${op.bHeadStartMs * 1000L} windowUs=${op.durationMs * 1000L}")
                        cache.build()
                        Log.d(TAG, "PUSH_CACHE_BUILD_AFTER index=$index empty=${cache.isEmpty()}")
                        if (cache.isEmpty()) {
                            throw IllegalStateException("Push cache empty for op index=$index pathB=${op.pathB}")
                        }
                        caches.add(cache)

                        val directionName = op.direction.removePrefix("PUSH_")
                        Log.d(TAG, "PUSH_DIRECTION_PARSE_BEFORE index=$index raw=${op.direction} parsed=$directionName")
                        val dir = SlideOverlay.Direction.valueOf(directionName)
                        val (pushDirectionX, pushDirectionY) = pushVectorForDirection(op.direction, "PUSH_")
                        Log.d(TAG, "PUSH_DIRECTION_PARSE_AFTER index=$index dir=$dir pushDir=($pushDirectionX,$pushDirectionY)")
                        val pushEffect = PushGlEffect(
                            startTimeUs = pushStartUs,
                            endTimeUs = pushEndUs,
                            directionX = pushDirectionX,
                            directionY = pushDirectionY
                        )
                        val overlay = SlideOverlay(cache, pushStartUs, pushEndUs, dir)
                        Log.d(TAG, "PUSH_ITEM_CREATE_BEFORE index=$index pathA=${op.pathA} clip=[${op.aTailStartMs}..${op.aEndMs}]")
                        items.add(
                            EditedMediaItem.Builder(clip(op.pathA, op.aTailStartMs, op.aEndMs))
                                .setEffects(Effects(emptyList(), listOf(pushEffect, OverlayEffect(listOf(overlay)))))
                                .build()
                        )
                        Log.d(TAG, "PUSH_ITEM_CREATE_AFTER index=$index itemCount=${items.size}")
                        Log.d(TAG, "PUSH dir=${op.direction} ${op.durationMs}ms A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}] B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}] @t=$runningTimeMs push=[$pushStartUs..$pushEndUs] fallback=NO")
                        runningTimeMs += op.durationMs
                    }
                    is CrossfadeRenderPlan.Op.Zoom -> {
                        onStage("Preparing zoom transition...")
                        val zoomStartUs = runningTimeMs * 1000L
                        val zoomEndUs = (runningTimeMs + op.durationMs) * 1000L
                        val zoomFps = slideCacheFps(op.durationMs)
                        Log.d(
                            TAG,
                            "ZOOM_CACHE_PROFILE index=$index durationMs=${op.durationMs} fps=$zoomFps " +
                                "maxDimension=$SLIDE_CACHE_MAX_DIMENSION minCoverage=$SLIDE_MIN_COVERAGE_PERCENT " +
                                "fallbackCoverage=$SLIDE_FAST_SAFE_COVERAGE_PERCENT maxFrames=$MAX_SLIDE_CACHE_FRAMES " +
                                "maxBytes=$SLIDE_MAX_CACHE_BYTES"
                        )
                        val cache = CrossfadeFrameCache(
                            clipPath = op.pathB,
                            startUs = op.bHeadStartMs * 1000L,
                            windowUs = op.durationMs * 1000L,
                            fps = zoomFps,
                            maxDimension = SLIDE_CACHE_MAX_DIMENSION,
                            minCoveragePercent = SLIDE_MIN_COVERAGE_PERCENT,
                            fallbackCoveragePercent = SLIDE_FAST_SAFE_COVERAGE_PERCENT,
                            maxEstimatedBytes = SLIDE_MAX_CACHE_BYTES
                        )
                        Log.d(TAG, "ZOOM_CACHE_BUILD_BEFORE index=$index pathB=${op.pathB} startUs=${op.bHeadStartMs * 1000L} windowUs=${op.durationMs * 1000L}")
                        cache.build()
                        Log.d(TAG, "ZOOM_CACHE_BUILD_AFTER index=$index empty=${cache.isEmpty()}")
                        if (cache.isEmpty()) {
                            throw IllegalStateException("Zoom cache empty for op index=$index pathB=${op.pathB}")
                        }
                        caches.add(cache)
                        val modeName = op.mode.removePrefix("ZOOM_")
                        Log.d(TAG, "ZOOM_MODE_PARSE_BEFORE index=$index raw=${op.mode} parsed=$modeName")
                        val mode = ZoomOverlay.Mode.valueOf(modeName)
                        Log.d(TAG, "ZOOM_MODE_PARSE_AFTER index=$index mode=$mode")
                        Log.d(TAG, "ZOOM_OVERLAY_CREATE_BEFORE index=$index zoomStartUs=$zoomStartUs zoomEndUs=$zoomEndUs mode=$mode")
                        val overlay = ZoomOverlay(cache, zoomStartUs, zoomEndUs, mode)
                        Log.d(TAG, "ZOOM_OVERLAY_CREATE_AFTER index=$index")
                        Log.d(TAG, "ZOOM_ITEM_CREATE_BEFORE index=$index pathA=${op.pathA} clip=[${op.aTailStartMs}..${op.aEndMs}]")
                        items.add(
                            EditedMediaItem.Builder(clip(op.pathA, op.aTailStartMs, op.aEndMs))
                                .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(overlay)))))
                                .build()
                        )
                        Log.d(TAG, "ZOOM_ITEM_CREATE_AFTER index=$index itemCount=${items.size}")
                        Log.d(TAG, "ZOOM mode=${op.mode} ${op.durationMs}ms A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}] B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}] @t=$runningTimeMs zoom=[$zoomStartUs..$zoomEndUs] fallback=NO")
                        runningTimeMs += op.durationMs
                    }
                    is CrossfadeRenderPlan.Op.Rotation -> {
                        onStage("Preparing rotation transition...")
                        val rotationStartUs = runningTimeMs * 1000L
                        val rotationEndUs = (runningTimeMs + op.durationMs) * 1000L
                        val rotationFps = slideCacheFps(op.durationMs)
                        Log.d(
                            TAG,
                            "ROTATION_CACHE_PROFILE index=$index durationMs=${op.durationMs} fps=$rotationFps " +
                                "maxDimension=$SLIDE_CACHE_MAX_DIMENSION minCoverage=$SLIDE_MIN_COVERAGE_PERCENT " +
                                "fallbackCoverage=$SLIDE_FAST_SAFE_COVERAGE_PERCENT maxFrames=$MAX_SLIDE_CACHE_FRAMES " +
                                "maxBytes=$SLIDE_MAX_CACHE_BYTES"
                        )
                        val cache = CrossfadeFrameCache(
                            clipPath = op.pathB,
                            startUs = op.bHeadStartMs * 1000L,
                            windowUs = op.durationMs * 1000L,
                            fps = rotationFps,
                            maxDimension = SLIDE_CACHE_MAX_DIMENSION,
                            minCoveragePercent = SLIDE_MIN_COVERAGE_PERCENT,
                            fallbackCoveragePercent = SLIDE_FAST_SAFE_COVERAGE_PERCENT,
                            maxEstimatedBytes = SLIDE_MAX_CACHE_BYTES
                        )
                        Log.d(TAG, "ROTATION_CACHE_BUILD_BEFORE index=$index pathB=${op.pathB} startUs=${op.bHeadStartMs * 1000L} windowUs=${op.durationMs * 1000L}")
                        cache.build()
                        Log.d(TAG, "ROTATION_CACHE_BUILD_AFTER index=$index empty=${cache.isEmpty()}")
                        if (cache.isEmpty()) {
                            throw IllegalStateException("Rotation cache empty for op index=$index pathB=${op.pathB}")
                        }
                        caches.add(cache)

                        val mode = rotationModeFor(op.mode)
                        Log.d(TAG, "ROTATION_EFFECT_CREATE_BEFORE index=$index mode=$mode window=[$rotationStartUs..$rotationEndUs]")
                        val rotationEffect = RotationGlEffect(
                            startTimeUs = rotationStartUs,
                            endTimeUs = rotationEndUs,
                            mode = mode
                        )
                        val overlay = RotationBitmapOverlay(cache, rotationStartUs, rotationEndUs, mode)
                        Log.d(TAG, "ROTATION_ITEM_CREATE_BEFORE index=$index pathA=${op.pathA} clip=[${op.aTailStartMs}..${op.aEndMs}]")
                        items.add(
                            EditedMediaItem.Builder(clip(op.pathA, op.aTailStartMs, op.aEndMs))
                                .setEffects(Effects(emptyList(), listOf(rotationEffect, OverlayEffect(listOf(overlay)))))
                                .build()
                        )
                        Log.d(TAG, "ROTATION_ITEM_CREATE_AFTER index=$index itemCount=${items.size}")
                        Log.d(TAG, "ROTATION mode=${op.mode} ${op.durationMs}ms A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}] B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}] @t=$runningTimeMs rotation=[$rotationStartUs..$rotationEndUs] fallback=NO")
                        runningTimeMs += op.durationMs
                    }
                    is CrossfadeRenderPlan.Op.Cube -> {
                        onStage("Preparing cube transition...")
                        val cubeStartUs = runningTimeMs * 1000L
                        val cubeEndUs = (runningTimeMs + op.durationMs) * 1000L
                        val cubeFps = slideCacheFps(op.durationMs)
                        Log.d(
                            TAG,
                            "CUBE_CACHE_PROFILE index=$index durationMs=${op.durationMs} fps=$cubeFps " +
                                "maxDimension=$SLIDE_CACHE_MAX_DIMENSION minCoverage=$SLIDE_MIN_COVERAGE_PERCENT " +
                                "fallbackCoverage=$SLIDE_FAST_SAFE_COVERAGE_PERCENT maxFrames=$MAX_SLIDE_CACHE_FRAMES " +
                                "maxBytes=$SLIDE_MAX_CACHE_BYTES"
                        )
                        val cache = CrossfadeFrameCache(
                            clipPath = op.pathB,
                            startUs = op.bHeadStartMs * 1000L,
                            windowUs = op.durationMs * 1000L,
                            fps = cubeFps,
                            maxDimension = SLIDE_CACHE_MAX_DIMENSION,
                            minCoveragePercent = SLIDE_MIN_COVERAGE_PERCENT,
                            fallbackCoveragePercent = SLIDE_FAST_SAFE_COVERAGE_PERCENT,
                            maxEstimatedBytes = SLIDE_MAX_CACHE_BYTES
                        )
                        Log.d(TAG, "CUBE_CACHE_BUILD_BEFORE index=$index pathB=${op.pathB} startUs=${op.bHeadStartMs * 1000L} windowUs=${op.durationMs * 1000L}")
                        cache.build()
                        Log.d(TAG, "CUBE_CACHE_BUILD_AFTER index=$index empty=${cache.isEmpty()}")
                        if (cache.isEmpty()) {
                            throw IllegalStateException("Cube cache empty for op index=$index pathB=${op.pathB}")
                        }
                        caches.add(cache)

                        val direction = cubeDirectionFor(op.direction)
                        Log.d(TAG, "CUBE_EFFECT_CREATE_BEFORE index=$index direction=$direction window=[$cubeStartUs..$cubeEndUs]")
                        val cubeEffect = CubeGlEffect(
                            startTimeUs = cubeStartUs,
                            endTimeUs = cubeEndUs,
                            direction = direction
                        )
                        val overlay = CubeBitmapOverlay(cache, cubeStartUs, cubeEndUs, direction)
                        Log.d(TAG, "CUBE_ITEM_CREATE_BEFORE index=$index pathA=${op.pathA} clip=[${op.aTailStartMs}..${op.aEndMs}]")
                        items.add(
                            EditedMediaItem.Builder(clip(op.pathA, op.aTailStartMs, op.aEndMs))
                                .setEffects(Effects(emptyList(), listOf(cubeEffect, OverlayEffect(listOf(overlay)))))
                                .build()
                        )
                        Log.d(TAG, "CUBE_ITEM_CREATE_AFTER index=$index itemCount=${items.size}")
                        Log.d(TAG, "CUBE dir=${op.direction} ${op.durationMs}ms A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}] B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}] @t=$runningTimeMs cube=[$cubeStartUs..$cubeEndUs] fallback=NO")
                        runningTimeMs += op.durationMs
                    }
                    is CrossfadeRenderPlan.Op.Flip -> {
                        onStage("Preparing flip transition...")
                        val flipStartUs = runningTimeMs * 1000L
                        val flipEndUs = (runningTimeMs + op.durationMs) * 1000L
                        val flipFps = slideCacheFps(op.durationMs)
                        Log.d(
                            TAG,
                            "FLIP_CACHE_PROFILE index=$index durationMs=${op.durationMs} fps=$flipFps " +
                                "maxDimension=$SLIDE_CACHE_MAX_DIMENSION minCoverage=$SLIDE_MIN_COVERAGE_PERCENT " +
                                "fallbackCoverage=$SLIDE_FAST_SAFE_COVERAGE_PERCENT maxFrames=$MAX_SLIDE_CACHE_FRAMES " +
                                "maxBytes=$SLIDE_MAX_CACHE_BYTES"
                        )
                        val cache = CrossfadeFrameCache(
                            clipPath = op.pathB,
                            startUs = op.bHeadStartMs * 1000L,
                            windowUs = op.durationMs * 1000L,
                            fps = flipFps,
                            maxDimension = SLIDE_CACHE_MAX_DIMENSION,
                            minCoveragePercent = SLIDE_MIN_COVERAGE_PERCENT,
                            fallbackCoveragePercent = SLIDE_FAST_SAFE_COVERAGE_PERCENT,
                            maxEstimatedBytes = SLIDE_MAX_CACHE_BYTES
                        )
                        Log.d(TAG, "FLIP_CACHE_BUILD_BEFORE index=$index pathB=${op.pathB} startUs=${op.bHeadStartMs * 1000L} windowUs=${op.durationMs * 1000L}")
                        cache.build()
                        Log.d(TAG, "FLIP_CACHE_BUILD_AFTER index=$index empty=${cache.isEmpty()}")
                        if (cache.isEmpty()) {
                            throw IllegalStateException("Flip cache empty for op index=$index pathB=${op.pathB}")
                        }
                        caches.add(cache)

                        val direction = flipDirectionFor(op.direction)
                        Log.d(TAG, "FLIP_EFFECT_CREATE_BEFORE index=$index direction=$direction window=[$flipStartUs..$flipEndUs]")
                        val flipEffect = FlipGlEffect(
                            startTimeUs = flipStartUs,
                            endTimeUs = flipEndUs,
                            direction = direction
                        )
                        val overlay = FlipBitmapOverlay(cache, flipStartUs, flipEndUs, direction)
                        Log.d(TAG, "FLIP_ITEM_CREATE_BEFORE index=$index pathA=${op.pathA} clip=[${op.aTailStartMs}..${op.aEndMs}]")
                        items.add(
                            EditedMediaItem.Builder(clip(op.pathA, op.aTailStartMs, op.aEndMs))
                                .setEffects(Effects(emptyList(), listOf(flipEffect, OverlayEffect(listOf(overlay)))))
                                .build()
                        )
                        Log.d(TAG, "FLIP_ITEM_CREATE_AFTER index=$index itemCount=${items.size}")
                        Log.d(TAG, "FLIP dir=${op.direction} ${op.durationMs}ms A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}] B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}] @t=$runningTimeMs flip=[$flipStartUs..$flipEndUs] fallback=NO")
                        runningTimeMs += op.durationMs
                    }
                    is CrossfadeRenderPlan.Op.PageTurn -> {
                        onStage("Preparing page turn transition...")
                        val pageTurnStartUs = runningTimeMs * 1000L
                        val pageTurnEndUs = (runningTimeMs + op.durationMs) * 1000L
                        val pageTurnFps = slideCacheFps(op.durationMs)
                        Log.d(
                            TAG,
                            "PAGE_TURN_CACHE_PROFILE index=$index durationMs=${op.durationMs} fps=$pageTurnFps " +
                                "maxDimension=$SLIDE_CACHE_MAX_DIMENSION minCoverage=$SLIDE_MIN_COVERAGE_PERCENT " +
                                "fallbackCoverage=$SLIDE_FAST_SAFE_COVERAGE_PERCENT maxFrames=$MAX_SLIDE_CACHE_FRAMES " +
                                "maxBytes=$SLIDE_MAX_CACHE_BYTES"
                        )
                        val cache = CrossfadeFrameCache(
                            clipPath = op.pathB,
                            startUs = op.bHeadStartMs * 1000L,
                            windowUs = op.durationMs * 1000L,
                            fps = pageTurnFps,
                            maxDimension = SLIDE_CACHE_MAX_DIMENSION,
                            minCoveragePercent = SLIDE_MIN_COVERAGE_PERCENT,
                            fallbackCoveragePercent = SLIDE_FAST_SAFE_COVERAGE_PERCENT,
                            maxEstimatedBytes = SLIDE_MAX_CACHE_BYTES
                        )
                        Log.d(TAG, "PAGE_TURN_CACHE_BUILD_BEFORE index=$index pathB=${op.pathB} startUs=${op.bHeadStartMs * 1000L} windowUs=${op.durationMs * 1000L}")
                        cache.build()
                        Log.d(TAG, "PAGE_TURN_CACHE_BUILD_AFTER index=$index empty=${cache.isEmpty()}")
                        if (cache.isEmpty()) {
                            throw IllegalStateException("Page turn cache empty for op index=$index pathB=${op.pathB}")
                        }
                        caches.add(cache)

                        val direction = pageTurnDirectionFor(op.direction)
                        Log.d(TAG, "PAGE_TURN_EFFECT_CREATE_BEFORE index=$index direction=$direction window=[$pageTurnStartUs..$pageTurnEndUs]")
                        val pageTurnEffect = PageTurnGlEffect(
                            startTimeUs = pageTurnStartUs,
                            endTimeUs = pageTurnEndUs,
                            bFrameCache = cache,
                            direction = direction
                        )
                        Log.d(TAG, "PAGE_TURN_ITEM_CREATE_BEFORE index=$index pathA=${op.pathA} clip=[${op.aTailStartMs}..${op.aEndMs}]")
                        items.add(
                            EditedMediaItem.Builder(clip(op.pathA, op.aTailStartMs, op.aEndMs))
                                .setEffects(Effects(emptyList(), listOf(pageTurnEffect)))
                                .build()
                        )
                        Log.d(TAG, "PAGE_TURN_ITEM_CREATE_AFTER index=$index itemCount=${items.size}")
                        Log.d(TAG, "PAGE_TURN dir=${op.direction} ${op.durationMs}ms A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}] B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}] @t=$runningTimeMs pageTurn=[$pageTurnStartUs..$pageTurnEndUs] fallback=NO")
                        runningTimeMs += op.durationMs
                    }
                    is CrossfadeRenderPlan.Op.WhipPan -> {
                        onStage("Preparing whip pan transition...")
                        val whipStartUs = runningTimeMs * 1000L
                        val whipEndUs = (runningTimeMs + op.durationMs) * 1000L
                        val whipFps = slideCacheFps(op.durationMs)
                        Log.d(
                            TAG,
                            "WHIP_PAN_CACHE_PROFILE index=$index durationMs=${op.durationMs} fps=$whipFps " +
                                "maxDimension=$SLIDE_CACHE_MAX_DIMENSION minCoverage=$SLIDE_MIN_COVERAGE_PERCENT " +
                                "fallbackCoverage=$SLIDE_FAST_SAFE_COVERAGE_PERCENT maxFrames=$MAX_SLIDE_CACHE_FRAMES " +
                                "maxBytes=$SLIDE_MAX_CACHE_BYTES"
                        )
                        val cache = CrossfadeFrameCache(
                            clipPath = op.pathB,
                            startUs = op.bHeadStartMs * 1000L,
                            windowUs = op.durationMs * 1000L,
                            fps = whipFps,
                            maxDimension = SLIDE_CACHE_MAX_DIMENSION,
                            minCoveragePercent = SLIDE_MIN_COVERAGE_PERCENT,
                            fallbackCoveragePercent = SLIDE_FAST_SAFE_COVERAGE_PERCENT,
                            maxEstimatedBytes = SLIDE_MAX_CACHE_BYTES
                        )
                        Log.d(TAG, "WHIP_PAN_CACHE_BUILD_BEFORE index=$index pathB=${op.pathB} startUs=${op.bHeadStartMs * 1000L} windowUs=${op.durationMs * 1000L}")
                        cache.build()
                        Log.d(TAG, "WHIP_PAN_CACHE_BUILD_AFTER index=$index empty=${cache.isEmpty()}")
                        if (cache.isEmpty()) {
                            throw IllegalStateException("Whip pan cache empty for op index=$index pathB=${op.pathB}")
                        }
                        caches.add(cache)

                        val directionName = op.direction.removePrefix("WHIP_PAN_")
                        val dir = SlideOverlay.Direction.valueOf(directionName)
                        val blurDirectionX = when (dir) {
                            SlideOverlay.Direction.LEFT -> 1f
                            SlideOverlay.Direction.RIGHT -> -1f
                            SlideOverlay.Direction.UP,
                            SlideOverlay.Direction.DOWN -> 0f
                        }
                        val blurDirectionY = when (dir) {
                            SlideOverlay.Direction.UP -> 1f
                            SlideOverlay.Direction.DOWN -> -1f
                            SlideOverlay.Direction.LEFT,
                            SlideOverlay.Direction.RIGHT -> 0f
                        }
                        Log.d(TAG, "WHIP_PAN_EFFECT_CREATE_BEFORE index=$index dir=$dir blurDir=($blurDirectionX,$blurDirectionY) window=[$whipStartUs..$whipEndUs]")
                        val blurEffect = DirectionalBlurGlEffect(
                            startTimeUs = whipStartUs,
                            endTimeUs = whipEndUs,
                            directionX = blurDirectionX,
                            directionY = blurDirectionY
                        )
                        val overlay = SlideOverlay(cache, whipStartUs, whipEndUs, dir)
                        Log.d(TAG, "WHIP_PAN_ITEM_CREATE_BEFORE index=$index pathA=${op.pathA} clip=[${op.aTailStartMs}..${op.aEndMs}]")
                        items.add(
                            EditedMediaItem.Builder(clip(op.pathA, op.aTailStartMs, op.aEndMs))
                                .setEffects(Effects(emptyList(), listOf(blurEffect, OverlayEffect(listOf(overlay)))))
                                .build()
                        )
                        Log.d(TAG, "WHIP_PAN_ITEM_CREATE_AFTER index=$index itemCount=${items.size}")
                        Log.d(TAG, "WHIP_PAN dir=${op.direction} ${op.durationMs}ms A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}] B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}] @t=$runningTimeMs whip=[$whipStartUs..$whipEndUs] fallback=NO")
                        runningTimeMs += op.durationMs
                    }
                    is CrossfadeRenderPlan.Op.MotionBlur -> {
                        onStage("Preparing motion blur transition...")
                        val motionBlurStartUs = runningTimeMs * 1000L
                        val motionBlurEndUs = (runningTimeMs + op.durationMs) * 1000L
                        val cache = CrossfadeFrameCache(
                            clipPath = op.pathB,
                            startUs = op.bHeadStartMs * 1000L,
                            windowUs = op.durationMs * 1000L
                        )
                        Log.d(TAG, "MOTION_BLUR_CACHE_BUILD_BEFORE index=$index pathB=${op.pathB} startUs=${op.bHeadStartMs * 1000L} windowUs=${op.durationMs * 1000L}")
                        cache.build()
                        Log.d(TAG, "MOTION_BLUR_CACHE_BUILD_AFTER index=$index empty=${cache.isEmpty()}")
                        if (cache.isEmpty()) {
                            throw IllegalStateException("Motion blur cache empty for op index=$index pathB=${op.pathB}")
                        }
                        caches.add(cache)

                        val (blurDirectionX, blurDirectionY) = blurVectorForDirection(op.direction, "MOTION_BLUR_")
                        Log.d(TAG, "MOTION_BLUR_EFFECT_CREATE_BEFORE index=$index direction=${op.direction} blurDir=($blurDirectionX,$blurDirectionY) window=[$motionBlurStartUs..$motionBlurEndUs]")
                        val blurEffect = DirectionalBlurGlEffect(
                            startTimeUs = motionBlurStartUs,
                            endTimeUs = motionBlurEndUs,
                            directionX = blurDirectionX,
                            directionY = blurDirectionY
                        )
                        val overlay = CrossfadeBitmapOverlay(cache, motionBlurStartUs, motionBlurEndUs)
                        Log.d(TAG, "MOTION_BLUR_ITEM_CREATE_BEFORE index=$index pathA=${op.pathA} clip=[${op.aTailStartMs}..${op.aEndMs}]")
                        items.add(
                            EditedMediaItem.Builder(clip(op.pathA, op.aTailStartMs, op.aEndMs))
                                .setEffects(Effects(emptyList(), listOf(blurEffect, OverlayEffect(listOf(overlay)))))
                                .build()
                        )
                        Log.d(TAG, "MOTION_BLUR_ITEM_CREATE_AFTER index=$index itemCount=${items.size}")
                        Log.d(TAG, "MOTION_BLUR dir=${op.direction} ${op.durationMs}ms A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}] B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}] @t=$runningTimeMs blur=[$motionBlurStartUs..$motionBlurEndUs] fallback=NO")
                        runningTimeMs += op.durationMs
                    }
                    }
                    Log.d(TAG, "BUILD_ITEM_AFTER index=$index runningTimeMs=$runningTimeMs itemCount=${items.size} cacheCount=${caches.size}")
                } catch (t: Throwable) {
                    Log.e(TAG, "BUILD_ITEM_THROW index=$index ${describeOp(index, op)}", t)
                    throw t
                }
            }
        }

        if (items.isEmpty()) {
            caches.forEach { it.release() }
            dispatchCleanups.forEach { runCatching { it() } }
            onResult(Result.Error("no items built from plan"))
            return
        }
        onStage("Starting export...")
        Log.d(TAG, "built ${items.size} items, ${caches.size} crossfade caches, totalTime=${runningTimeMs}ms - starting transformer")

        Log.d(TAG, "SEQUENCE_CREATE_BEFORE itemCount=${items.size}")
        val sequence = EditedMediaItemSequence.Builder(items).build()
        Log.d(TAG, "SEQUENCE_CREATE_AFTER")
        Log.d(TAG, "OUTPUT_EFFECTS_CREATE_BEFORE out=${outW}x${outH}")
        val outputEffects = Effects(emptyList(), listOf(Presentation.createForWidthAndHeight(outW, outH, Presentation.LAYOUT_SCALE_TO_FIT)))
        Log.d(TAG, "OUTPUT_EFFECTS_CREATE_AFTER")
        Log.d(TAG, "COMPOSITION_CREATE_BEFORE sequenceCount=1 itemCount=${items.size}")
        val composition = Composition.Builder(listOf(sequence)).setEffects(outputEffects).build()
        Log.d(TAG, "COMPOSITION_CREATE_AFTER composition=$composition")

        val mainHandler = Handler(Looper.getMainLooper())

        fun releaseCaches() {
            caches.forEach { try { it.release() } catch (_: Exception) {} }
            dispatchCleanups.forEach { runCatching { it() } }
        }

        Log.d(TAG, "TRANSFORMER_CREATE_BEFORE")
        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    Log.d(TAG, "TRANSFORMER_CALLBACK_ON_COMPLETED result=$result outputExists=${outputFile.exists()} bytes=${outputFile.length()}")
                    onProgress(100)
                    Log.d(TAG, "DONE bytes=${outputFile.length()} durationMs=${result.durationMs}")
                    releaseCaches()
                    onResult(Result.Done(outputFile.absolutePath, outputFile.length(), result.durationMs))
                }
                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    Log.e(TAG, "TRANSFORMER_CALLBACK_ON_ERROR result=$result code=${exception.errorCode} msg=${exception.message}", exception)
                    releaseCaches()
                    onResult(Result.Error("code=${exception.errorCode} ${exception.message}"))
                }
            })
            .build()
        Log.d(TAG, "TRANSFORMER_CREATE_AFTER transformer=$transformer")

        try {
            Log.d(TAG, "TRANSFORMER_START_BEFORE output=${outputFile.absolutePath} thread=${Thread.currentThread().name}")
            transformer.start(composition, outputFile.absolutePath)
            Log.d(TAG, "TRANSFORMER_START_AFTER")
            onStage("Rendering...")
        } catch (t: Throwable) {
            Log.e(TAG, "TRANSFORMER_START_THROW ${t.message}", t)
            releaseCaches()
            onResult(Result.Error("THROW ${t.message}"))
            return
        }

        val progressHolder = ProgressHolder()
        mainHandler.post(object : Runnable {
            override fun run() {
                val state = transformer.getProgress(progressHolder)
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    onProgress(progressHolder.progress.coerceIn(0, 99))
                    mainHandler.postDelayed(this, 400L)
                }
            }
        })
    }

    // ---- Kept from step 1/2: single-pair test entry (still used by some flows) ----

    suspend fun renderProjectDissolvePair(
        context: Context,
        projectId: String,
        onProgress: (Int) -> Unit,
        onResult: (Result) -> Unit
    ) {
        data class Clip(val path: String, val transitionType: String?, val durMs: Long)

        val clips = withContext(Dispatchers.IO) {
            val db = ClipForgeDatabase.getInstance(context)
            val items = db.timelineDao().getTimelineForProjectOnce(projectId)
            val out = mutableListOf<Clip>()
            for (item in items) {
                val asset = db.timelineDao().getMediaAssetForItem(item.mediaAssetId) ?: continue
                if (asset.mediaType != "VIDEO") continue
                out.add(Clip(asset.localUri, item.transitionType, readDurationMs(asset.localUri)))
            }
            out
        }

        var pairIndex = -1
        for (i in 0 until clips.size - 1) {
            val t = clips[i].transitionType?.uppercase()
            if (t in REAL_CROSSFADE_TYPES) { pairIndex = i; break }
        }
        if (pairIndex < 0) {
            onResult(Result.Error("no dissolve boundary in project"))
            return
        }

        val c1 = clips[pairIndex]
        val c2 = clips[pairIndex + 1]
        val db = ClipForgeDatabase.getInstance(context)
        val crossfadeMs = withContext(Dispatchers.IO) {
            db.timelineDao().getTimelineForProjectOnce(projectId)
                .firstOrNull { it.transitionType?.uppercase() in REAL_CROSSFADE_TYPES }
                ?.transitionDurationMs ?: 1000L
        }
        if (c1.durMs <= 0L || c2.durMs <= 0L) {
            onResult(Result.Error("bad durations c1=${c1.durMs} c2=${c2.durMs}"))
            return
        }
        renderTwoClipCrossfade(context, c1.path, c1.durMs, c2.path, c2.durMs, crossfadeMs, onProgress, onResult)
    }

    suspend fun renderTwoClipCrossfade(
        context: Context,
        clip1Path: String,
        dur1Ms: Long,
        clip2Path: String,
        dur2Ms: Long,
        crossfadeMs: Long,
        onProgress: (Int) -> Unit,
        onResult: (Result) -> Unit
    ) {
        val outputFile = File(context.getExternalFilesDir(null), "xfade_exec_${System.currentTimeMillis()}.mp4")
        if (outputFile.exists()) outputFile.delete()

        val crossfadeUs = crossfadeMs * 1000L
        val clip1BodyEndMs = (dur1Ms - crossfadeMs).coerceAtLeast(0L)
        val fadeStartUs = clip1BodyEndMs * 1000L
        val fadeEndUs = (clip1BodyEndMs + crossfadeMs) * 1000L

        val cache = CrossfadeFrameCache(clipPath = clip2Path, startUs = 0L, windowUs = crossfadeUs)
        withContext(Dispatchers.IO) { cache.build() }
        if (cache.isEmpty()) { onResult(Result.Error("frame cache empty")); return }

        val item0 = EditedMediaItem.Builder(clip(clip1Path, 0L, clip1BodyEndMs)).build()
        val overlay = CrossfadeBitmapOverlay(cache, fadeStartUs, fadeEndUs)
        val item1 = EditedMediaItem.Builder(clip(clip1Path, clip1BodyEndMs, dur1Ms))
            .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(overlay)))))
            .build()
        val item2 = EditedMediaItem.Builder(clip(clip2Path, crossfadeMs, dur2Ms)).build()

        val sequence = EditedMediaItemSequence.Builder(listOf(item0, item1, item2)).build()
        val composition = Composition.Builder(listOf(sequence)).build()
        val mainHandler = Handler(Looper.getMainLooper())

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    onProgress(100)
                    Log.d(TAG, "DONE bytes=${outputFile.length()} durationMs=${result.durationMs}")
                    try { cache.release() } catch (_: Exception) {}
                    onResult(Result.Done(outputFile.absolutePath, outputFile.length(), result.durationMs))
                }
                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    Log.e(TAG, "ERROR code=${exception.errorCode} msg=${exception.message}", exception)
                    try { cache.release() } catch (_: Exception) {}
                    onResult(Result.Error("code=${exception.errorCode} ${exception.message}"))
                }
            })
            .build()

        try {
            transformer.start(composition, outputFile.absolutePath)
        } catch (t: Throwable) {
            try { cache.release() } catch (_: Exception) {}
            onResult(Result.Error("THROW ${t.message}"))
            return
        }

        val progressHolder = ProgressHolder()
        mainHandler.post(object : Runnable {
            override fun run() {
                val state = transformer.getProgress(progressHolder)
                if (state != Transformer.PROGRESS_STATE_NOT_STARTED) {
                    onProgress(progressHolder.progress.coerceIn(0, 99))
                    mainHandler.postDelayed(this, 400L)
                }
            }
        })
    }
}

private const val MIN_SLIDE_CACHE_FPS = 15
private const val MAX_SLIDE_CACHE_FPS = 30
private const val MAX_SLIDE_CACHE_FRAMES = 72
private const val SLIDE_CACHE_MAX_DIMENSION = 720
private const val SLIDE_MIN_COVERAGE_PERCENT = 95f
private const val SLIDE_FAST_SAFE_COVERAGE_PERCENT = 80f
private const val SLIDE_MAX_CACHE_BYTES = 45L * 1024L * 1024L
