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
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import java.io.File

/**
 * STEP 2 (Option B) - crossfade SEGMENT renderer, TEST HARNESS stage.
 *
 * The gap-based overlap (Option A) was proven NOT to carry the overlay clip's video
 * into the compositor on 1.9.0. Option B sidesteps any time-offset:
 *
 *   - Render ONLY the short crossfade SEGMENT, where both clips start at t=0:
 *       * background sequence = clip A's LAST crossfade seconds
 *       * overlay sequence    = clip B's FIRST crossfade seconds
 *     Both carry real video from frame 0, so the compositor (proven to drive a
 *     correct alpha ramp) can actually blend them.
 *   - The whole segment IS the fade window [0, crossfadeUs].
 *
 * Full feature (next step, after this segment is confirmed):
 *     [clip A minus tail] + [crossfade segment] + [clip B minus head]
 * concatenated via the already-proven multi-clip export.
 */
@UnstableApi
object CrossfadeExporter {

    private const val TAG = "CROSSFADE_EXPORT"

    sealed class Result {
        data class Done(val outputPath: String, val bytes: Long, val durationMs: Long) : Result()
        data class Error(val message: String) : Result()
    }

    private fun pathToUri(path: String): Uri =
        if (path.startsWith("/")) Uri.fromFile(File(path)) else Uri.parse(path)

    /**
     * Renders just the crossfade segment between two clips. Main thread only.
     *
     * @param clipADurationUs full duration of clip A in microseconds (to locate its tail).
     * @param crossfadeUs     length of the crossfade in microseconds.
     */
    fun crossfadeTwoClips(
        context: Context,
        pathA: String,
        pathB: String,
        clipADurationUs: Long,
        crossfadeUs: Long,
        onProgress: (Int) -> Unit,
        onResult: (Result) -> Unit
    ) {
        val outputFile = File(context.getExternalFilesDir(null), "crossfade_${System.currentTimeMillis()}.mp4")
        if (outputFile.exists()) outputFile.delete()

        val crossfadeMs = crossfadeUs / 1000L
        val clipADurationMs = clipADurationUs / 1000L
        val aTailStartMs = (clipADurationMs - crossfadeMs).coerceAtLeast(0L)
        val fadeStartUs = 0L
        val fadeEndUs = crossfadeUs
        Log.d(TAG, "START B-SEGMENT aTailStartMs=$aTailStartMs clipADurationMs=$clipADurationMs crossfadeMs=$crossfadeMs")

        val clipAClip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(aTailStartMs)
            .setEndPositionMs(clipADurationMs)
            .build()
        val clipBClip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(0L)
            .setEndPositionMs(crossfadeMs)
            .build()

        val itemA = EditedMediaItem.Builder(
            MediaItem.Builder().setUri(pathToUri(pathA)).setClippingConfiguration(clipAClip).build()
        ).build()
        val itemB = EditedMediaItem.Builder(
            MediaItem.Builder().setUri(pathToUri(pathB)).setClippingConfiguration(clipBClip).build()
        ).build()

        val backgroundSequence = EditedMediaItemSequence.Builder()
            .addItem(itemA)
            .build()

        val overlaySequence = EditedMediaItemSequence.Builder()
            .addItem(itemB)
            .build()

        val compositor = CrossfadeCompositorSettings(fadeStartUs, fadeEndUs)

        val composition = Composition.Builder(
            listOf(backgroundSequence, overlaySequence)
        )
            .setVideoCompositorSettings(compositor)
            .build()

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
