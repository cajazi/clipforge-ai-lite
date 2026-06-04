package com.clipforge.ai.core.supabase

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.clipforge.ai.core.storage.userDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.util.UUID

/**
 * Manages the current user session.
 * For MVP: generates a stable anonymous user ID stored in DataStore.
 * TODO: Replace with real Supabase Auth (email/Google sign-in) post-MVP.
 */
class SupabaseAuthManager(private val context: Context) {

    private val USER_ID_KEY = stringPreferencesKey("anon_user_id")

    val userId: Flow<String> = context.userDataStore.data.map { prefs ->
        prefs[USER_ID_KEY] ?: ""
    }

    /** Returns the current user ID, generating one if it doesn't exist yet. */
    suspend fun getOrCreateUserId(): String {
        val prefs = context.userDataStore.data.first()
        val existing = prefs[USER_ID_KEY]
        if (!existing.isNullOrBlank()) return existing

        val newId = UUID.randomUUID().toString()
        context.userDataStore.edit { it[USER_ID_KEY] = newId }
        return newId
    }

    /** Returns the storage path for a file: {userId}/{filename} */
    suspend fun storagePath(fileName: String): String {
        val uid = getOrCreateUserId()
        return "$uid/$fileName"
    }

    suspend fun isLoggedIn(): Boolean = getOrCreateUserId().isNotBlank()
}
