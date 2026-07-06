package com.clipforge.ai.core.supabase

import android.util.Log
import com.clipforge.ai.BuildConfig

object SupabaseConfig {

    private val validation: SupabaseConfigValidation by lazy {
        SupabaseConfigValidator.validate(
            rawUrl = BuildConfig.SUPABASE_URL,
            rawAnonKey = BuildConfig.SUPABASE_ANON_KEY
        ).also {
            if (BuildConfig.DEBUG) {
                Log.d(
                    "AUTH_CHECK",
                    "urlBlank=${it.urlBlank} urlMalformed=${it.urlMalformed} " +
                        "keyBlank=${it.keyBlank} keyMalformed=${it.keyMalformed} " +
                        "host=${it.host ?: "none"}"
                )
            }
            if (it.error != null) {
                Log.e("AUTH_CHECK", it.error)
            }
        }
    }

    val SUPABASE_URL: String by lazy {
        validation.normalizedUrl
    }

    val SUPABASE_ANON_KEY: String by lazy {
        validation.anonKey
    }

    val AUTH_BASE_URL: String get() = validation.authBaseUrl
    val REST_BASE_URL: String get() = validation.restBaseUrl

    fun isConfigured(): Boolean =
        validation.isValid

    fun validationError(): String? = validation.error

    fun authHost(): String? = validation.host

    internal fun currentValidation(): SupabaseConfigValidation = validation

    const val ADMIN_EMAIL       = "cossybest24@gmail.com"
    const val BUCKET_MEDIA      = "project-media"
    const val BUCKET_AUDIO      = "project-audio"
    const val BUCKET_EXPORTS    = "project-exports"
    const val BUCKET_THUMBNAILS = "project-thumbnails"
    const val TABLE_PROJECTS    = "projects"
    const val TABLE_RENDER_JOBS = "render_jobs"
    const val TABLE_MEDIA_ASSETS = "media_assets"
    const val TABLE_PROFILES    = "profiles"
}
