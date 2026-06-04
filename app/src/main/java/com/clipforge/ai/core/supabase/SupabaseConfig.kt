package com.clipforge.ai.core.supabase

import android.util.Log
import com.clipforge.ai.BuildConfig

object SupabaseConfig {

    val SUPABASE_URL: String by lazy {
        BuildConfig.SUPABASE_URL.also {
            Log.d("AUTH_CHECK", "URL empty = ${it.isBlank()}")
            if (it.isBlank()) Log.e("AUTH_CHECK", "SUPABASE_URL not configured in local.properties!")
        }
    }

    val SUPABASE_ANON_KEY: String by lazy {
        BuildConfig.SUPABASE_ANON_KEY.also {
            Log.d("AUTH_CHECK", "KEY empty = ${it.isBlank()}")
            if (it.isBlank()) Log.e("AUTH_CHECK", "SUPABASE_ANON_KEY not configured in local.properties!")
        }
    }

    fun isConfigured(): Boolean =
        SUPABASE_URL.isNotBlank() && SUPABASE_ANON_KEY.isNotBlank()

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
