package com.clipforge.ai.core.supabase

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

const val SUPABASE_MISSING_CONFIG_MESSAGE =
    "Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties."

const val SUPABASE_MALFORMED_URL_MESSAGE =
    "Supabase URL is invalid. Use the HTTPS project URL from Supabase."

const val SUPABASE_MALFORMED_KEY_MESSAGE =
    "Supabase anon key is not configured correctly."

data class SupabaseConfigValidation(
    val normalizedUrl: String,
    val anonKey: String,
    val host: String?,
    val urlBlank: Boolean,
    val keyBlank: Boolean,
    val urlMalformed: Boolean,
    val keyMalformed: Boolean,
    val error: String?
) {
    val isValid: Boolean get() = error == null
    val authBaseUrl: String get() = "$normalizedUrl/auth/v1"
    val restBaseUrl: String get() = "$normalizedUrl/rest/v1/"
}

object SupabaseConfigValidator {
    fun validate(rawUrl: String, rawAnonKey: String): SupabaseConfigValidation {
        val normalizedUrl = rawUrl.trim().trimEnd('/')
        val anonKey = rawAnonKey.trim()
        val urlBlank = normalizedUrl.isBlank()
        val keyBlank = anonKey.isBlank()
        val parsedUrl = normalizedUrl.takeIf { it.isNotBlank() }?.toHttpUrlOrNull()
        val host = parsedUrl?.host
        val path = parsedUrl?.encodedPath.orEmpty()
        val urlMalformed = !urlBlank && (
            parsedUrl == null ||
                parsedUrl.scheme != "https" ||
                parsedUrl.host.isBlank() ||
                (path.isNotBlank() && path != "/")
            )
        val keyMalformed = !keyBlank && anonKey.equals("PASTE_ANON_KEY_HERE", ignoreCase = true)
        val error = when {
            urlBlank || keyBlank -> SUPABASE_MISSING_CONFIG_MESSAGE
            urlMalformed -> SUPABASE_MALFORMED_URL_MESSAGE
            keyMalformed -> SUPABASE_MALFORMED_KEY_MESSAGE
            else -> null
        }

        return SupabaseConfigValidation(
            normalizedUrl = normalizedUrl,
            anonKey = anonKey,
            host = host,
            urlBlank = urlBlank,
            keyBlank = keyBlank,
            urlMalformed = urlMalformed,
            keyMalformed = keyMalformed,
            error = error
        )
    }
}
