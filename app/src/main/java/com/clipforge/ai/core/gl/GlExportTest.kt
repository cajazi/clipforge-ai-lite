package com.clipforge.ai.core.gl

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.transformer.Composition
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.Transformer
import java.io.File

/**
 * STEP 1a - prove the device can render ONE real MP4 from ONE clip via Media3 Transformer.
 * No custom shader yet. No UI. Standalone and isolated from the (fake) export flow.
 *
 * Call runTransformerTest(context, Uri.parse(asset.localUri)) from any button.
 * Output: <app files dir>/transformer_test.mp4
 * Watch logcat tag "GL_EXPORT_TEST".
 */
@UnstableApi
object GlExportTest {

    private const val TAG = "GL_EXPORT_TEST"

    fun runTransformerTest(
        context: Context,
        inputUri: Uri,
        onResult: (success: Boolean, message: String) -> Unit = { _, _ -> }
    ) {
        val outputFile = File(context.getExternalFilesDir(null), "transformer_test.mp4")
        if (outputFile.exists()) outputFile.delete()

        Log.d(TAG, "START input=$inputUri output=${outputFile.absolutePath}")

        val mediaItem = MediaItem.Builder().setUri(inputUri).build()
        val editedMediaItem = EditedMediaItem.Builder(mediaItem).build()

        val transformer = Transformer.Builder(context)
            .addListener(object : Transformer.Listener {
                override fun onCompleted(composition: Composition, result: ExportResult) {
                    val msg = "DONE ok file=${outputFile.absolutePath} " +
                        "exists=${outputFile.exists()} bytes=${outputFile.length()} " +
                        "durationMs=${result.durationMs} width=${result.width} height=${result.height}"
                    Log.d(TAG, msg)
                    onResult(true, msg)
                }

                override fun onError(
                    composition: Composition,
                    result: ExportResult,
                    exception: ExportException
                ) {
                    val msg = "ERROR code=${exception.errorCode} message=${exception.message}"
                    Log.e(TAG, msg, exception)
                    onResult(false, msg)
                }
            })
            .build()

        try {
            transformer.start(editedMediaItem, outputFile.absolutePath)
            Log.d(TAG, "transformer.start() called - rendering in background")
        } catch (t: Throwable) {
            Log.e(TAG, "THROW before start: ${t.message}", t)
            onResult(false, "THROW ${t.message}")
        }
    }
}
