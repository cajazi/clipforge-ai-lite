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
 * STEP 2 (Option A) - crossfade exporter, TEST HARNESS stage (Gate 4a).
 *
 * Proves a true two-clip crossfade baked into an exported MP4, in isolation,
 * before integrating with real timeline data.
 *
 * Mechanism (all APIs confirmed against resolved media3 1.9.0 jars):
 *   - Two EditedMediaItemSequences in one Composition.
 *   - Sequence 0 (background) = clip A alone.
 *   - Sequence 1 (overlay)    = a GAP of (clipADurationUs - crossfadeUs) followed
 *                               by clip B, via EditedMediaItemSequence.Builder.addGap().
 *                               The gap delays B so its content overlaps A's tail.
 *   - Composition.setVideoCompositorSettings(CrossfadeCompositorSettings) drives
 *     per-frame alpha on the overlay during the overlap window.
 *
 * IMPORTANT - this harness needs clip A's duration in microseconds to size the gap.
 * For the test we pass it in explicitly (clipADurationUs). In 4b integration we'll
 * read real durations from the timeline.
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
     * Crossfade two clips. Must be called on the main thread (Transformer needs a Looper).
     *
     * @param clipADurationUs full duration of clip A in microseconds (needed to size the gap).
     * @param crossfadeUs     length of the crossfade overlap in microseconds.
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

        // Overlap window on the composition timeline:
        //   B's content starts at (clipADurationUs - crossfadeUs) and the fade runs for crossfadeUs.
        val trimMs = 3000L  // TEST: trim both clips short to shrink the gap
        val effectiveClipADurationUs = trimMs * 1000L
        val gapUs = (effectiveClipADurationUs - crossfadeUs).coerceAtLeast(0L)
        val fadeStartUs = gapUs
        val fadeEndUs = effectiveClipADurationUs
        Log.d(TAG, "START gapUs=$gapUs fadeStartUs=$fadeStartUs fadeEndUs=$fadeEndUs crossfadeUs=$crossfadeUs")

        val clipping = MediaItem.ClippingConfiguration.Builder().setStartPositionMs(0L).setEndPositionMs(trimMs).build()
        val itemA = EditedMediaItem.Builder(MediaItem.Builder().setUri(pathToUri(pathA)).setClippingConfiguration(clipping).build()).build()
        val itemB = EditedMediaItem.Builder(MediaItem.Builder().setUri(pathToUri(pathB)).setClippingConfiguration(clipping).build()).build()

        // Background sequence: clip A alone.
        val backgroundSequence = EditedMediaItemSequence.Builder()
            .addItem(itemA)
            .build()

        // Overlay sequence: gap (to delay B) + clip B.
        val overlaySequence = EditedMediaItemSequence.Builder()
            .addGap(gapUs)
            .addItem(itemB)
            .experimentalSetForceVideoTrack(true)
            .experimentalSetForceAudioTrack(true)
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
