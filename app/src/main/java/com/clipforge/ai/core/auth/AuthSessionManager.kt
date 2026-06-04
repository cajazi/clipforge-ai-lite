package com.clipforge.ai.core.auth

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.clipforge.ai.core.storage.userDataStore
import com.clipforge.ai.domain.model.AuthUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

class AuthSessionManager(private val context: Context) {

    private val KEY_USER_ID       = stringPreferencesKey("auth_user_id")
    private val KEY_EMAIL         = stringPreferencesKey("auth_email")
    private val KEY_DISPLAY_NAME  = stringPreferencesKey("auth_display_name")
    private val KEY_ACCESS_TOKEN  = stringPreferencesKey("auth_access_token")
    private val KEY_REFRESH_TOKEN = stringPreferencesKey("auth_refresh_token")
    private val KEY_OAUTH_CODE_VERIFIER = stringPreferencesKey("auth_oauth_code_verifier")

    val sessionFlow: Flow<AuthUser?> = context.userDataStore.data.map { prefs ->
        val id = prefs[KEY_USER_ID] ?: return@map null
        val at = prefs[KEY_ACCESS_TOKEN] ?: return@map null
        if (id.isBlank() || at.isBlank()) return@map null
        AuthUser(
            id           = id,
            email        = prefs[KEY_EMAIL] ?: "",
            displayName  = prefs[KEY_DISPLAY_NAME],
            accessToken  = at,
            refreshToken = prefs[KEY_REFRESH_TOKEN] ?: ""
        )
    }

    suspend fun saveSession(user: AuthUser) {
        context.userDataStore.edit { prefs ->
            prefs[KEY_USER_ID]       = user.id
            prefs[KEY_EMAIL]         = user.email
            prefs[KEY_DISPLAY_NAME]  = user.displayName ?: ""
            prefs[KEY_ACCESS_TOKEN]  = user.accessToken
            prefs[KEY_REFRESH_TOKEN] = user.refreshToken
        }
    }

    suspend fun clearSession() {
        context.userDataStore.edit { it.clear() }
    }

    suspend fun getSession(): AuthUser? = sessionFlow.first()

    suspend fun getAccessToken(): String? =
        context.userDataStore.data.first()[KEY_ACCESS_TOKEN]

    suspend fun saveOAuthCodeVerifier(codeVerifier: String) {
        context.userDataStore.edit { prefs ->
            prefs[KEY_OAUTH_CODE_VERIFIER] = codeVerifier
        }
    }

    suspend fun getOAuthCodeVerifier(): String? =
        context.userDataStore.data.first()[KEY_OAUTH_CODE_VERIFIER]

    suspend fun clearOAuthCodeVerifier() {
        context.userDataStore.edit { prefs ->
            prefs.remove(KEY_OAUTH_CODE_VERIFIER)
        }
    }
}
