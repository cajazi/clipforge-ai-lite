package com.clipforge.ai.core.gl

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.OverlayEffect
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.EditedMediaItemSequence
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import com.clipforge.ai.data.local.database.ClipForgeDatabase
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

    /**
     * GENERALIZED entry: walk the whole render plan, build all crossfade segments with
     * correct cumulative composition offsets, concatenate, render. Handles any number of
     * clips and dissolve boundaries. Must be called from a coroutine on Main.
     */
    suspend fun renderProjectTimeline(
        context: Context,
        projectId: String,
        onProgress: (Int) -> Unit,
        onResult: (Result) -> Unit
    ) {
        val ops = CrossfadeRenderPlan.build(context, projectId)
        if (ops.isEmpty()) {
            onResult(Result.Error("empty render plan"))
            return
        }

        val outputFile = File(context.getExternalFilesDir(null), "xfade_timeline_${System.currentTimeMillis()}.mp4")
        if (outputFile.exists()) outputFile.delete()

        // Pre-build all crossfade caches off the render thread, computing each item's
        // composition offset from the running timeline position.
        data class BuiltItem(val item: EditedMediaItem)
        val items = ArrayList<EditedMediaItem>()
        val caches = ArrayList<CrossfadeFrameCache>()
        var runningTimeMs = 0L

        withContext(Dispatchers.IO) {
            for (op in ops) {
                when (op) {
                    is CrossfadeRenderPlan.Op.PlainClip -> {
                        val durMs = (op.endMs - op.startMs).coerceAtLeast(0L)
                        items.add(EditedMediaItem.Builder(clip(op.path, op.startMs, op.endMs)).build())
                        Log.d(TAG, "PLAIN ${op.path.substringAfterLast('/')} [${op.startMs}..${op.endMs}] @t=$runningTimeMs dur=$durMs")
                        runningTimeMs += durMs
                    }
                    is CrossfadeRenderPlan.Op.Crossfade -> {
                        val fadeStartUs = runningTimeMs * 1000L
                        val fadeEndUs = (runningTimeMs + op.crossfadeMs) * 1000L
                        val cache = CrossfadeFrameCache(
                            clipPath = op.pathB,
                            startUs = op.bHeadStartMs * 1000L,
                            windowUs = op.crossfadeMs * 1000L
                        )
                        cache.build()
                        caches.add(cache)
                        val overlay = CrossfadeBitmapOverlay(cache, fadeStartUs, fadeEndUs)
                        items.add(
                            EditedMediaItem.Builder(clip(op.pathA, op.aTailStartMs, op.aEndMs))
                                .setEffects(Effects(emptyList(), listOf(OverlayEffect(listOf(overlay)))))
                                .build()
                        )
                        Log.d(TAG, "XFADE ${op.crossfadeMs}ms A=${op.pathA.substringAfterLast('/')}[${op.aTailStartMs}..${op.aEndMs}] B=${op.pathB.substringAfterLast('/')}[head ${op.bHeadStartMs}] @t=$runningTimeMs fade=[$fadeStartUs..$fadeEndUs] cacheFrames=${if (cache.isEmpty()) 0 else 1}")
                        runningTimeMs += op.crossfadeMs
                    }
                }
            }
        }

        if (items.isEmpty()) {
            caches.forEach { it.release() }
            onResult(Result.Error("no items built from plan"))
            return
        }
        Log.d(TAG, "built ${items.size} items, ${caches.size} crossfade caches, totalTime=${runningTimeMs}ms - starting transformer")

        val sequence = EditedMediaItemSequence.Builder(items).build()
        val composition = Composition.Builder(listOf(sequence)).build()

        val mainHandler = Handler(Looper.getMainLooper())

        fun releaseCaches() { caches.forEach { try { it.release() } catch (_: Exception) {} } }

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    onProgress(100)
                    Log.d(TAG, "DONE bytes=${outputFile.length()} durationMs=${result.durationMs}")
                    releaseCaches()
                    onResult(Result.Done(outputFile.absolutePath, outputFile.length(), result.durationMs))
                }
                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    Log.e(TAG, "ERROR code=${exception.errorCode} msg=${exception.message}", exception)
                    releaseCaches()
                    onResult(Result.Error("code=${exception.errorCode} ${exception.message}"))
                }
            })
            .build()

        try {
            transformer.start(composition, outputFile.absolutePath)
        } catch (t: Throwable) {
            Log.e(TAG, "THROW ${t.message}", t)
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
