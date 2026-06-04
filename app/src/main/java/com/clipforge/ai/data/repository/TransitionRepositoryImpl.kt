package com.clipforge.ai.data.repository

import android.util.Log
import com.clipforge.ai.core.auth.AuthSessionManager
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.core.supabase.SupabaseConfig
import com.clipforge.ai.data.local.dao.TimelineDao
import com.clipforge.ai.domain.model.Transition
import com.clipforge.ai.domain.repository.TransitionRepository
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "TransitionRepo"

class TransitionRepositoryImpl(
    private val dao: TimelineDao,
    private val sessionManager: AuthSessionManager
) : TransitionRepository {

    private val gson   = Gson()
    private val JSON   = "application/json".toMediaType()
    private val http   = OkHttpClient.Builder().build()
    private val dbBase = "${SupabaseConfig.SUPABASE_URL}/rest/v1/timeline_items"

    override suspend fun saveTransition(
        projectId: String, clipId: String, transition: Transition
    ): NetworkResult<Unit> = withContext(Dispatchers.IO) {
        // 1. Save to Room
        dao.updateTransition(clipId, transition.type.name, transition.durationMs)
        Log.d(TAG, "Saved transition ${transition.type} to Room for clip $clipId")

        // 2. Sync to Supabase
        syncToSupabase(
            filter  = "id=eq.$clipId",
            body    = """{"transition_type":"${transition.type.name}","transition_duration_ms":${transition.durationMs}}"""
        )
    }

    override suspend fun saveAllTransitions(
        projectId: String, transition: Transition
    ): NetworkResult<Unit> = withContext(Dispatchers.IO) {
        // 1. Save to Room
        dao.updateAllTransitions(projectId, transition.type.name, transition.durationMs)
        Log.d(TAG, "Saved transition ${transition.type} to ALL clips in project $projectId")

        // 2. Sync to Supabase
        syncToSupabase(
            filter  = "project_id=eq.$projectId",
            body    = """{"transition_type":"${transition.type.name}","transition_duration_ms":${transition.durationMs}}"""
        )
    }

    private fun syncToSupabase(filter: String, body: String): NetworkResult<Unit> {
        return try {
            val token = kotlinx.coroutines.runBlocking { sessionManager.getAccessToken() }
                ?: return NetworkResult.Success(Unit) // offline — Room already saved

            val req = Request.Builder()
                .url("$dbBase?$filter")
                .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .patch(body.toRequestBody(JSON))
                .build()
            val resp = http.newCall(req).execute()
            Log.d(TAG, "Supabase sync: ${resp.code}")
            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Supabase sync failed (will retry next save): ${e.message}")
            NetworkResult.Success(Unit) // Room already saved — not fatal
        }
    }
}
