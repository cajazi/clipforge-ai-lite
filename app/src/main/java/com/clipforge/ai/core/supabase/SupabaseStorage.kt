package com.clipforge.ai.core.supabase

import android.content.Context
import android.net.Uri
import android.util.Log
import com.clipforge.ai.core.auth.AuthSessionManager
import com.clipforge.ai.core.network.NetworkResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

private const val TAG = "SupabaseStorage"

class SupabaseStorage(private val context: Context) {

    private val sessionManager = AuthSessionManager(context)

    private val client = OkHttpClient.Builder()
        .callTimeout(120, TimeUnit.SECONDS)
        .connectTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(120, TimeUnit.SECONDS)
        .build()

    private val storageUrl = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object"

    /**
     * Upload a Uri to Supabase Storage under the user's folder.
     * Path format: {bucket}/{userId}/{uuid}.{ext}
     * Returns the public URL.
     */
    suspend fun uploadMedia(uri: Uri): NetworkResult<String> =
        uploadFile(uri, SupabaseConfig.BUCKET_MEDIA)

    suspend fun uploadAudio(uri: Uri): NetworkResult<String> =
        uploadFile(uri, SupabaseConfig.BUCKET_AUDIO)

    suspend fun uploadThumbnail(uri: Uri): NetworkResult<String> =
        uploadFile(uri, SupabaseConfig.BUCKET_THUMBNAILS)

    suspend fun uploadExport(uri: Uri): NetworkResult<String> =
        uploadFile(uri, SupabaseConfig.BUCKET_EXPORTS)

    private suspend fun uploadFile(uri: Uri, bucket: String): NetworkResult<String> =
        withContext(Dispatchers.IO) {
            try {
                val session = sessionManager.getSession()
                    ?: return@withContext NetworkResult.Error(message = "Not authenticated")
                val userId   = session.id
                val token    = session.accessToken
                val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                val ext      = when {
                    mimeType.contains("mp4")  -> "mp4"
                    mimeType.contains("mov")  -> "mov"
                    mimeType.contains("jpeg") -> "jpg"
                    mimeType.contains("jpg")  -> "jpg"
                    mimeType.contains("png")  -> "png"
                    mimeType.contains("mp3")  -> "mp3"
                    mimeType.contains("aac")  -> "aac"
                    mimeType.contains("wav")  -> "wav"
                    else -> mimeType.substringAfterLast("/").substringBefore(";")
                }

                // Enforce: {userId}/{uuid}.{ext}
                val fileName = "$userId/${UUID.randomUUID()}.$ext"

                val inputStream = context.contentResolver.openInputStream(uri)
                    ?: return@withContext NetworkResult.Error(message = "Cannot open file")
                val bytes = inputStream.use { it.readBytes() }

                Log.d(TAG, "Uploading $fileName to $bucket (${bytes.size} bytes)")

                val requestBody = bytes.toRequestBody(mimeType.toMediaType())
                val request = Request.Builder()
                    .url("$storageUrl/$bucket/$fileName")
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Content-Type", mimeType)
                    .addHeader("x-upsert", "true")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()
                return@withContext if (response.isSuccessful) {
                    val publicUrl = "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/$bucket/$fileName"
                    Log.d(TAG, "Upload success: $publicUrl")
                    NetworkResult.Success(publicUrl)
                } else {
                    val error = response.body?.string() ?: response.message
                    Log.e(TAG, "Upload failed: $error")
                    NetworkResult.Error(response.code, error)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Upload exception: ${e.localizedMessage}")
                NetworkResult.Error(message = e.localizedMessage)
            }
        }

    /**
     * Delete a file from Supabase Storage.
     * Path must include the userId prefix: {userId}/{filename}
     */
    suspend fun deleteFile(bucket: String, filePath: String): NetworkResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val request = Request.Builder()
                    .url("$storageUrl/$bucket/$filePath")
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer ${sessionManager.getAccessToken() ?: SupabaseConfig.SUPABASE_ANON_KEY}")
                    .delete()
                    .build()
                val response = client.newCall(request).execute()
                if (response.isSuccessful) NetworkResult.Success(Unit)
                else NetworkResult.Error(response.code, response.message)
            } catch (e: IOException) {
                NetworkResult.Error(message = e.localizedMessage)
            }
        }

    /** Get public URL for a file without uploading. */
    fun getPublicUrl(bucket: String, userId: String, fileName: String): String =
        "${SupabaseConfig.SUPABASE_URL}/storage/v1/object/public/$bucket/$userId/$fileName"
}
