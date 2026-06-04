package com.clipforge.ai.core.gl

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
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
 * Real export for a project.
 *
 * - Single-clip path (resolveFirstVideoPath / exportFirstClip) is kept for reference
 *   and was the original proof-of-life. Not used by the live export flow anymore.
 * - Multi-clip path (resolveAllVideoPaths / exportProject) concatenates ALL video
 *   clips in timeline order into one MP4 via an EditedMediaItemSequence + Composition.
 *
 * Trims and baked transitions are NOT applied yet - this renders each full source
 * clip back to back. Those come in later steps.
 */
@UnstableApi
object ProjectExporter {

    private const val TAG = "PROJECT_EXPORT"

    sealed class Result {
        data class Done(val outputPath: String, val bytes: Long) : Result()
        data class Error(val message: String) : Result()
    }

    // ---------------------------------------------------------------------
    // Single-clip (legacy, kept for reference)
    // ---------------------------------------------------------------------

    suspend fun resolveFirstVideoPath(context: Context, projectId: String): String? =
        withContext(Dispatchers.IO) {
            val db = ClipForgeDatabase.getInstance(context)
            val items = db.timelineDao().getTimelineForProjectOnce(projectId)
            for (item in items) {
                val asset = db.timelineDao().getMediaAssetForItem(item.mediaAssetId) ?: continue
                if (asset.mediaType == "VIDEO") {
                    Log.d(TAG, "first video asset=${asset.id} localUri=${asset.localUri}")
                    return@withContext asset.localUri
                }
            }
            Log.e(TAG, "no VIDEO asset found for project $projectId")
            null
        }

    // ---------------------------------------------------------------------
    // Multi-clip (live path)
    // ---------------------------------------------------------------------

    /**
     * Resolves ALL renderable video clip paths for the project, in timeline order
     * (trackIndex ASC, orderIndex ASC - guaranteed by the DAO query).
     * Returns an empty list if the project has no usable video assets.
     */
    suspend fun resolveAllVideoPaths(context: Context, projectId: String): List<String> =
        withContext(Dispatchers.IO) {
            val db = ClipForgeDatabase.getInstance(context)
            val items = db.timelineDao().getTimelineForProjectOnce(projectId)
            val paths = mutableListOf<String>()
            for (item in items) {
                val asset = db.timelineDao().getMediaAssetForItem(item.mediaAssetId) ?: continue
                if (asset.mediaType == "VIDEO") {
                    paths.add(asset.localUri)
                }
            }
            Log.d(TAG, "resolved ${paths.size} video clip(s) for project $projectId")
            paths
        }

    private fun pathToUri(path: String): Uri =
        if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)

    /**
     * Starts a real export concatenating all given clip paths in order. Must be
     * called on the main thread (Transformer requires a Looper). Calls
     * onProgress(0..100) periodically and onResult exactly once.
     */
    fun exportProject(
        context: Context,
        paths: List<String>,
        onProgress: (Int) -> Unit,
        onResult: (Result) -> Unit
    ) {
        if (paths.isEmpty()) {
            onResult(Result.Error("No video clips to export"))
            return
        }

        val outputFile = File(context.getExternalFilesDir(null), "export_${System.currentTimeMillis()}.mp4")
        if (outputFile.exists()) outputFile.delete()

        val editedItems = paths.map { path ->
            val mediaItem = MediaItem.Builder().setUri(pathToUri(path)).build()
            EditedMediaItem.Builder(mediaItem).build()
        }
        Log.d(TAG, "START clips=${editedItems.size} output=${outputFile.absolutePath}")

        val sequence = EditedMediaItemSequence.Builder(editedItems).build()
        val composition = Composition.Builder(listOf(sequence)).build()

        val mainHandler = Handler(Looper.getMainLooper())

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    onProgress(100)
                    Log.d(TAG, "DONE bytes=${outputFile.length()} durationMs=${result.durationMs}")
                    onResult(Result.Done(outputFile.absolutePath, outputFile.length()))
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

        // Poll progress every 400ms. Completion is signalled by the listener, not here.
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

    /**
     * Legacy single-clip export. Retained for reference; not used by the live flow.
     */
    fun exportFirstClip(
        context: Context,
        localUri: String,
        onProgress: (Int) -> Unit,
        onResult: (Result) -> Unit
    ) {
        exportProject(context, listOf(localUri), onProgress, onResult)
    }
}
