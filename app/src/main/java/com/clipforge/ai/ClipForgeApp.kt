package com.clipforge.ai

import android.app.Application
import com.clipforge.ai.core.ads.AdFrequencyController
import com.clipforge.ai.core.ads.GoogleAdManager
import com.clipforge.ai.core.auth.AuthManager
import com.clipforge.ai.core.billing.BillingManager
import com.clipforge.ai.core.billing.EntitlementManager
import com.clipforge.ai.core.export.ExportManager
import com.clipforge.ai.core.network.NetworkMonitor
import com.clipforge.ai.core.storage.UserPreferencesManager
import com.clipforge.ai.core.supabase.SupabaseClient
import com.clipforge.ai.core.supabase.SupabaseProjectApi
import com.clipforge.ai.core.supabase.SupabaseRenderApi
import com.clipforge.ai.core.supabase.SupabaseStorage
import com.clipforge.ai.data.local.database.ClipForgeDatabase
import com.clipforge.ai.data.remote.api.MediaApi
import com.clipforge.ai.core.network.ApiClient
import com.clipforge.ai.data.repository.EffectRepositoryImpl
import com.clipforge.ai.data.repository.MediaRepositoryImpl
import com.clipforge.ai.data.repository.SupabaseProjectRepository
import com.clipforge.ai.data.repository.SupabaseRenderRepository

class ClipForgeApp : Application() {

    val database             by lazy { ClipForgeDatabase.getInstance(this) }
    val supabaseProjectApi   by lazy { SupabaseClient.create<SupabaseProjectApi>() }
    val supabaseRenderApi    by lazy { SupabaseClient.create<SupabaseRenderApi>() }
    val supabaseStorage      by lazy { SupabaseStorage(this) }
    val mediaApi             by lazy { ApiClient.create<MediaApi>() }
    val projectRepository    by lazy { SupabaseProjectRepository(database.projectDao(), supabaseProjectApi) }
    val renderRepository     by lazy { SupabaseRenderRepository(supabaseRenderApi) }
    val mediaRepository      by lazy { MediaRepositoryImpl(database.mediaAssetDao(), mediaApi) }
    val effectRepository     by lazy { EffectRepositoryImpl(database.effectItemDao()) }
    val userPreferencesManager by lazy { UserPreferencesManager(this) }
    val exportManager        by lazy { ExportManager(this, userPreferencesManager) }
    val networkMonitor       by lazy { NetworkMonitor(this) }
    val entitlementManager   by lazy { EntitlementManager() }
    val billingManager       by lazy { BillingManager(entitlementManager) }
    val adFrequencyController by lazy { AdFrequencyController() }
    val adManager            by lazy { GoogleAdManager(adFrequencyController) }

    // Auth — single instance shared across all ViewModels
    val authManager          by lazy { AuthManager(this) }

    override fun onCreate() {
        super.onCreate()
        SupabaseClient.accessTokenProvider = { authManager.sessionManager.getAccessToken() }
        adManager.initialize(this)
        billingManager.initialize(this)
    }
}
