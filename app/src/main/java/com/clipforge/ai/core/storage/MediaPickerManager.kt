package com.clipforge.ai.core.storage
import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import java.io.File
class MediaPickerManager {
    fun buildSingleVisualPickerRequest(
        mediaType: PickVisualMediaRequest = PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
    ): PickVisualMediaRequest = mediaType
    fun getFileSizeBytes(context: Context, uri: Uri): Long =
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    fun getMimeType(context: Context, uri: Uri): String? = context.contentResolver.getType(uri)

    /**
     * Copies the bytes behind a picked content:// URI into the app's own private
     * storage and returns an absolute file path that never loses permission.
     *
     * Picked Photo Picker URIs lose their read grant across sessions, which makes
     * them unusable for export/render later. Copying the file in at import time
     * gives a stable, app-owned path. Returns null on failure (caller can fall back).
     */
    fun copyToAppStorage(context: Context, uri: Uri, assetId: String, mimeType: String?): String? {
        return try {
            val ext = when {
                mimeType?.contains("mp4") == true -> "mp4"
                mimeType?.startsWith("video") == true -> "mp4"
                mimeType?.contains("png") == true -> "png"
                mimeType?.contains("jpeg") == true || mimeType?.contains("jpg") == true -> "jpg"
                mimeType?.startsWith("image") == true -> "jpg"
                mimeType?.startsWith("audio") == true -> "m4a"
                else -> "bin"
            }
            val mediaDir = File(context.filesDir, "media").apply { mkdirs() }
            val outFile = File(mediaDir, "media_${assetId}.$ext")
            context.contentResolver.openInputStream(uri)?.use { input ->
                outFile.outputStream().use { output ->
                    input.copyTo(output, bufferSize = 64 * 1024)
                }
            } ?: run {
                Log.e("MediaPickerManager", "openInputStream returned null for $uri")
                return null
            }
            if (outFile.exists() && outFile.length() > 0L) {
                Log.d("MediaPickerManager", "Copied to ${outFile.absolutePath} (${outFile.length()} bytes)")
                outFile.absolutePath
            } else {
                Log.e("MediaPickerManager", "Copy produced empty file for $uri")
                null
            }
        } catch (e: Exception) {
            Log.e("MediaPickerManager", "copyToAppStorage failed for $uri: ${e.message}", e)
            null
        }
    }
}
