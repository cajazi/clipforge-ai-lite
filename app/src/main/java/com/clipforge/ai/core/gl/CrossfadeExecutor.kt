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

    private fun describeOp(index: Int, op: CrossfadeRenderPlan.Op): String =
        when (op) {
            is CrossfadeRenderPlan.Op.PlainClip ->
                "op[$index]=PLAIN path=${op.path} startMs=${op.startMs} endMs=${op.endMs} durationMs=${op.endMs - op.startMs}"
            is CrossfadeRenderPlan.Op.Crossfade ->
                "op[$index]=XFADE pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} durationMs=${op.crossfadeMs}"
            is CrossfadeRenderPlan.Op.DipToColor ->
                "op[$index]=DIP pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} bHeadEndMs=${op.bHeadEndMs} halfMs=${op.halfDurationMs} color=${op.colorInt}"
            is CrossfadeRenderPlan.Op.Slide ->
                "op[$index]=SLIDE pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} durationMs=${op.durationMs} direction=${op.direction}"
            is CrossfadeRenderPlan.Op.Zoom ->
                "op[$index]=ZOOM pathA=${op.pathA} aTailStartMs=${op.aTailStartMs} aEndMs=${op.aEndMs} pathB=${op.pathB} bHeadStartMs=${op.bHeadStartMs} durationMs=${op.durationMs} mode=${op.mode}"
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
        var runningTimeMs = 0L

        onStage("Preparing transitions...")
        withContext(Dispatchers.IO) {
            for ((index, op) in ops.withIndex()) {
                Log.d(TAG, "BUILD_ITEM_BEFORE ${describeOp(index, op)} runningTimeMs=$runningTimeMs thread=${Thread.currentThread().name}")
                try {
                    when (op) {
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

        fun releaseCaches() { caches.forEach { try { it.release() } catch (_: Exception) {} } }

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
