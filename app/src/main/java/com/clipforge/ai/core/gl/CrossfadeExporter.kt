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
import java.io.File

/**
 * STEP 2 (overlay route) - crossfade SEGMENT via OverlayEffect, TEST HARNESS.
 *
 * VideoCompositorSettings (two sequences) was proven NOT to blend overlay video.
 * This route instead uses ONE clip (clip A's tail) as the base frame, and overlays
 * clip B's frames on top via OverlayEffect + a CrossfadeBitmapOverlay whose alpha
 * ramps 0 -> 1. An overlay is a true alpha-over-base composite, which is exactly a
 * crossfade.
 *
 * Output is just the ~1s crossfade segment. If it dissolves correctly, the full
 * feature concatenates [A minus tail] + [segment] + [B minus head] via the proven
 * multi-clip export.
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
        // The segment runs [0, crossfadeUs] on the composition timeline; the whole
        // thing is the fade.
        val fadeStartUs = 0L
        val fadeEndUs = crossfadeUs
        Log.d(TAG, "START OVERLAY aTailStartMs=$aTailStartMs clipADurationMs=$clipADurationMs crossfadeMs=$crossfadeMs")

        // Base = clip A's LAST crossfade seconds.
        val clipAClip = MediaItem.ClippingConfiguration.Builder()
            .setStartPositionMs(aTailStartMs)
            .setEndPositionMs(clipADurationMs)
            .build()
        val mediaItemA = MediaItem.Builder().setUri(pathToUri(pathA)).setClippingConfiguration(clipAClip).build()

        // Overlay = clip B's frames (starting at clip B t=0), alpha ramping 0->1.
        val overlay = CrossfadeBitmapOverlay(
            clipBPath = pathB,
            clipBStartUs = 0L,
            fadeStartUs = fadeStartUs,
            fadeEndUs = fadeEndUs
        )
        val effects = Effects(emptyList(), listOf(OverlayEffect(listOf(overlay))))

        val itemA = EditedMediaItem.Builder(mediaItemA)
            .setEffects(effects)
            .build()

        val sequence = EditedMediaItemSequence.Builder(listOf(itemA)).build()
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
