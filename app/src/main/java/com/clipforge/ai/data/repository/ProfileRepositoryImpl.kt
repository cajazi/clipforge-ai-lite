package com.clipforge.ai.data.repository

import android.util.Log
import com.clipforge.ai.core.auth.AuthSessionManager
import com.clipforge.ai.core.billing.PlanType
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.core.supabase.SupabaseConfig
import com.clipforge.ai.domain.model.UserProfile
import com.clipforge.ai.domain.model.UserRole
import com.clipforge.ai.domain.repository.ProfileRepository
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

private const val TAG = "ProfileRepo"
private const val ADMIN_EMAIL = "cossybest24@gmail.com"

data class ProfileDto(
    @SerializedName("id")           val id: String,
    @SerializedName("email")        val email: String,
    @SerializedName("display_name") val displayName: String,
    @SerializedName("avatar_url")   val avatarUrl: String? = null,
    @SerializedName("plan")         val plan: String       = "free",
    @SerializedName("role")         val role: String       = "user",
    @SerializedName("created_at")   val createdAt: String? = null,
    @SerializedName("updated_at")   val updatedAt: String? = null
)

class ProfileRepositoryImpl(
    private val session: AuthSessionManager
) : ProfileRepository {

    private val gson   = Gson()
    private val JSON   = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder().build()
    private val base   = "${SupabaseConfig.SUPABASE_URL}/rest/v1/profiles"

    override suspend fun getProfile(userId: String): NetworkResult<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                val token = session.getAccessToken() ?: return@withContext NetworkResult.Error(message = "Not authenticated")
                val req = Request.Builder()
                    .url("$base?id=eq.$userId&select=*")
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .get().build()
                val resp = client.newCall(req).execute()
                val json = resp.body?.string() ?: "[]"
                val dtos  = gson.fromJson(json, Array<ProfileDto>::class.java)
                if (dtos.isNotEmpty()) {
                    NetworkResult.Success(dtos.first().toProfile())
                } else {
                    NetworkResult.Error(404, "Profile not found")
                }
            } catch (e: Exception) {
                Log.e(TAG, "getProfile error: ${e.message}")
                NetworkResult.Error(message = e.localizedMessage)
            }
        }

    override suspend fun createOrUpdateProfile(profile: UserProfile): NetworkResult<UserProfile> =
        withContext(Dispatchers.IO) {
            try {
                val token = session.getAccessToken() ?: return@withContext NetworkResult.Error(message = "Not authenticated")
                // Admin check — only applies when email is developer email
                val resolvedRole = if (profile.email == ADMIN_EMAIL) UserRole.ADMIN else profile.role
                val resolvedPlan = if (profile.email == ADMIN_EMAIL) PlanType.PRO else profile.plan
                val dto  = ProfileDto(
                    id          = profile.id,
                    email       = profile.email,
                    displayName = profile.displayName,
                    avatarUrl   = profile.avatarUrl,
                    plan        = resolvedPlan.name.lowercase(),
                    role        = resolvedRole.name.lowercase()
                )
                val body = gson.toJson(dto).toRequestBody(JSON)
                val req  = Request.Builder()
                    .url("$base?on_conflict=id")
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $token")
                    .addHeader("Prefer", "resolution=merge-duplicates,return=representation")
                    .post(body).build()
                val resp = client.newCall(req).execute()
                val json = resp.body?.string() ?: "[]"
                val dtos  = gson.fromJson(json, Array<ProfileDto>::class.java)
                if (dtos.isNotEmpty()) {
                    NetworkResult.Success(dtos.first().toProfile())
                } else {
                    // upsert succeeded but returned nothing — return input
                    NetworkResult.Success(profile.copy(role = resolvedRole, plan = resolvedPlan))
                }
            } catch (e: Exception) {
                Log.e(TAG, "createOrUpdateProfile error: ${e.message}")
                NetworkResult.Error(message = e.localizedMessage)
            }
        }

    private fun ProfileDto.toProfile() = UserProfile(
        id          = id,
        email       = email,
        displayName = displayName,
        avatarUrl   = avatarUrl,
        plan        = try { PlanType.valueOf(plan.uppercase()) } catch (_: Exception) { PlanType.FREE },
        role        = try { UserRole.valueOf(role.uppercase()) } catch (_: Exception) { UserRole.USER },
        createdAt   = createdAt ?: "",
        updatedAt   = updatedAt ?: ""
    )
}
