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
 * Executor: assembles [plain][crossfade-overlay][plain] in one sequence.
 *
 * CRITICAL: the crossfade frame cache is built BEFORE transformer.start(), off the
 * render thread. Building it lazily during the first getBitmap() blocks the
 * video-processing thread for the whole extraction (~30s), producing no output
 * samples, which trips Media3's 10s muxer watchdog ("no output sample written").
 * So we pre-extract on IO, then start the transformer with a ready cache.
 *
 * Overlay uses CrossfadeFrameCache (interim fast path) - later replaced by a proper
 * MediaCodec streaming decoder.
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

    /** Self-contained test entry: first real dissolve boundary -> render the pair. */
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
            Log.d(TAG, "no DISSOLVE boundary found in project")
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
        Log.d(TAG, "dissolve pair idx=$pairIndex c1=${c1.path.substringAfterLast('/')}(${c1.durMs}ms) c2=${c2.path.substringAfterLast('/')}(${c2.durMs}ms) crossfade=${crossfadeMs}ms")

        if (c1.durMs <= 0L || c2.durMs <= 0L) {
            onResult(Result.Error("bad durations c1=${c1.durMs} c2=${c2.durMs}"))
            return
        }

        renderTwoClipCrossfade(context, c1.path, c1.durMs, c2.path, c2.durMs, crossfadeMs, onProgress, onResult)
    }

    /**
     * Renders [clip1 body][crossfade][clip2 body] as one sequence.
     * suspend: builds the frame cache on IO BEFORE starting the transformer on Main.
     */
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
        val clip1TailStartMs = clip1BodyEndMs
        val fadeStartUs = clip1BodyEndMs * 1000L
        val fadeEndUs = (clip1BodyEndMs + crossfadeMs) * 1000L
        Log.d(TAG, "ASSEMBLY clip1Body=[0..$clip1BodyEndMs] xfade(${crossfadeMs}ms) clip1Tail=[$clip1TailStartMs..$dur1Ms]+clip2Head clip2Body=[$crossfadeMs..$dur2Ms]")

        // Pre-build the frame cache OFF the render thread (avoids the muxer watchdog).
        val cache = CrossfadeFrameCache(
            clipPath = clip2Path,
            startUs = 0L,
            windowUs = crossfadeUs
        )
        withContext(Dispatchers.IO) {
            Log.d(TAG, "pre-building frame cache...")
            cache.build()
        }
        if (cache.isEmpty()) {
            onResult(Result.Error("frame cache empty - extraction failed"))
            return
        }
        Log.d(TAG, "cache ready, starting transformer")

        val item0 = EditedMediaItem.Builder(clip(clip1Path, 0L, clip1BodyEndMs)).build()

        val overlay = CrossfadeBitmapOverlay(
            cache = cache,
            fadeStartUs = fadeStartUs,
            fadeEndUs = fadeEndUs
        )
        val item1 = EditedMediaItem.Builder(clip(clip1Path, clip1TailStartMs, dur1Ms))
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
                    onResult(Result.Done(outputFile.absolutePath, outputFile.length(), result.durationMs))
                }
                override fun onError(composition: Composition, result: ExportResult, exception: ExportException) {
                    Log.e(TAG, "ERROR code=${exception.errorCode} msg=${exception.message}", exception)
                    onResult(Result.Error("code=${exception.errorCode} ${exception.message}"))
                }
            })
            .build()

        try {
            transformer.start(composition, outputFile.absolutePath)
        } catch (t: Throwable) {
            Log.e(TAG, "THROW ${t.message}", t)
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
