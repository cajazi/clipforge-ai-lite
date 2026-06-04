package com.clipforge.ai.core.storage
import android.content.Context
import android.net.Uri
import java.io.File
class LocalFileManager(private val context: Context) {
    private val cacheDir: File get() = context.cacheDir
    fun copyUriToCache(uri: Uri, fileName: String): File? = null // TODO
    fun deleteCachedFile(file: File): Boolean = file.delete()
    fun clearMediaCache() { /* TODO */ }
    fun availableCacheBytes(): Long = cacheDir.freeSpace
}
