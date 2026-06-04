package com.clipforge.ai.core.auth

import android.util.Log
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.core.supabase.SupabaseConfig
import com.clipforge.ai.domain.model.AuthUser
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

private const val TAG = "SupabaseAuthClient"
const val OAUTH_REDIRECT_URL = "clipforgeai://auth-callback"

data class SupabaseUser(
    @SerializedName("id")    val id: String  = "",
    @SerializedName("email") val email: String? = null
)

/** Wraps a Supabase /auth/v1 call result — may or may not have a session */
data class AuthCallResult(
    val user: SupabaseUser?   = null,
    val accessToken: String?  = null,
    val refreshToken: String? = null,
    val requiresConfirmation: Boolean = false,
    val error: String?        = null
)

class SupabaseAuthClient {

    private val gson   = Gson()
    private val JSON   = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private val authUrl = "${SupabaseConfig.SUPABASE_URL}/auth/v1"

    // ── Sign Up ───────────────────────────────────────────────────────────
    suspend fun signUp(email: String, password: String, name: String): AuthCallResult =
        withContext(Dispatchers.IO) {
            try {
                val body = """{"email":"$email","password":"$password","data":{"display_name":"$name"}}"""
                val resp = rawPost("$authUrl/signup", body)
                val json = resp.body?.string() ?: ""
                Log.d(TAG, "signUp response [${resp.code}]: $json")
                parseSignUpResponse(json, resp.code)
            } catch (e: IOException) {
                Log.e(TAG, "signUp network error: ${e.message}")
                AuthCallResult(error = "Network error. Check your connection.")
            }
        }

    // ── Sign In ───────────────────────────────────────────────────────────
    suspend fun signIn(email: String, password: String): AuthCallResult =
        withContext(Dispatchers.IO) {
            try {
                val body = """{"email":"$email","password":"$password"}"""
                val resp = rawPost("$authUrl/token?grant_type=password", body)
                val json = resp.body?.string() ?: ""
                Log.d(TAG, "signIn response [${resp.code}]: $json")
                parseAuthResponse(json, resp.code)
            } catch (e: IOException) {
                Log.e(TAG, "signIn network error: ${e.message}")
                AuthCallResult(error = "Network error. Check your connection.")
            }
        }

    // ── Refresh ───────────────────────────────────────────────────────────
    suspend fun refreshSession(refreshToken: String): AuthCallResult =
        withContext(Dispatchers.IO) {
            try {
                val body = """{"refresh_token":"$refreshToken"}"""
                val resp = rawPost("$authUrl/token?grant_type=refresh_token", body)
                val json = resp.body?.string() ?: ""
                parseAuthResponse(json, resp.code)
            } catch (e: IOException) {
                AuthCallResult(error = "Network error.")
            }
        }

    // ── Get user from token ───────────────────────────────────────────────
    suspend fun getUser(accessToken: String): AuthCallResult =
        withContext(Dispatchers.IO) {
            try {
                val req = Request.Builder()
                    .url("$authUrl/user")
                    .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                    .addHeader("Authorization", "Bearer $accessToken")
                    .get().build()
                val resp = client.newCall(req).execute()
                val json = resp.body?.string() ?: ""
                Log.d(TAG, "getUser response [${resp.code}]: ${json.take(200)}")
                if (resp.isSuccessful) {
                    val obj  = gson.fromJson(json, JsonObject::class.java)
                    val id   = obj.get("id")?.asString ?: ""
                    val mail = obj.get("email")?.asString ?: ""
                    if (id.isNotBlank())
                        AuthCallResult(user = SupabaseUser(id, mail), accessToken = accessToken)
                    else
                        AuthCallResult(error = "Session expired")
                } else {
                    AuthCallResult(error = "Session expired. Please log in again.")
                }
            } catch (e: IOException) {
                AuthCallResult(error = "Network error.")
            }
        }

    // ── Password reset ────────────────────────────────────────────────────
    suspend fun sendPasswordReset(email: String): NetworkResult<Unit> =
        withContext(Dispatchers.IO) {
            try {
                val body = """{"email":"$email","redirect_to":"$OAUTH_REDIRECT_URL"}"""
                val resp = rawPost("$authUrl/recover", body)
                if (resp.isSuccessful) NetworkResult.Success(Unit)
                else NetworkResult.Error(resp.code, "Could not send reset email.")
            } catch (e: IOException) {
                NetworkResult.Error(message = "Network error.")
            }
        }

    // ── Logout ────────────────────────────────────────────────────────────
    suspend fun logout(accessToken: String) = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$authUrl/logout")
                .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
                .addHeader("Authorization", "Bearer $accessToken")
                .post("{}".toRequestBody(JSON)).build()
            client.newCall(req).execute()
        } catch (_: IOException) {}
    }

    // ── Google OAuth URL — clipforgeai deep link, NO localhost ────────────
    fun googleOAuthUrl(): String {
        val encoded = java.net.URLEncoder.encode(OAUTH_REDIRECT_URL, "UTF-8")
        val url = "$authUrl/authorize?provider=google&redirect_to=$encoded"
        Log.d(TAG, "OAuth start — redirectUrl=$OAUTH_REDIRECT_URL")
        Log.d(TAG, "Full URL: $url")
        return url
    }

    // ── Helpers ───────────────────────────────────────────────────────────
    private fun rawPost(url: String, bodyJson: String): Response {
        val req = Request.Builder().url(url)
            .addHeader("apikey", SupabaseConfig.SUPABASE_ANON_KEY)
            .addHeader("Content-Type", "application/json")
            .post(bodyJson.toRequestBody(JSON)).build()
        return client.newCall(req).execute()
    }

    private fun parseSignUpResponse(json: String, code: Int): AuthCallResult {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java)

            // Error cases
            val errMsg  = obj.get("error")?.asString
            val errDesc = obj.get("error_description")?.asString
            val msg     = obj.get("msg")?.asString
            if (errMsg != null || errDesc != null) {
                val raw = errDesc ?: errMsg ?: msg ?: "Registration failed"
                Log.e(TAG, "signUp error: $raw")
                return AuthCallResult(error = friendlyError(raw))
            }

            val userId    = obj.getAsJsonObject("user")?.get("id")?.asString ?: ""
            val userEmail = obj.getAsJsonObject("user")?.get("email")?.asString ?: ""
            val at        = obj.get("access_token")?.asString
            val rt        = obj.get("refresh_token")?.asString

            Log.d(TAG, "signUp: userId=$userId at=${at?.take(20)} requiresConfirmation=${at == null}")

            if (userId.isBlank()) {
                return AuthCallResult(error = friendlyError(msg ?: "Registration failed (code $code)"))
            }

            if (at.isNullOrBlank()) {
                // Email confirmation required — not an error
                return AuthCallResult(
                    user                 = SupabaseUser(userId, userEmail),
                    requiresConfirmation = true
                )
            }

            AuthCallResult(
                user         = SupabaseUser(userId, userEmail),
                accessToken  = at,
                refreshToken = rt
            )
        } catch (e: Exception) {
            Log.e(TAG, "parseSignUpResponse exception: ${e.message} | json=$json")
            AuthCallResult(error = "Registration error. Please try again.")
        }
    }

    private fun parseAuthResponse(json: String, code: Int): AuthCallResult {
        return try {
            val obj     = gson.fromJson(json, JsonObject::class.java)
            val errDesc = obj.get("error_description")?.asString
            val errMsg  = obj.get("error")?.asString
            val msg     = obj.get("msg")?.asString
            if (errMsg != null || errDesc != null) {
                val raw = errDesc ?: errMsg ?: "Login failed"
                Log.e(TAG, "auth error: $raw")
                return AuthCallResult(error = friendlyError(raw))
            }
            val at   = obj.get("access_token")?.asString ?: ""
            val rt   = obj.get("refresh_token")?.asString ?: ""
            val user = obj.getAsJsonObject("user")
            val id   = user?.get("id")?.asString ?: ""
            val mail = user?.get("email")?.asString ?: ""
            if (at.isBlank() || id.isBlank()) {
                Log.e(TAG, "Missing access_token or user id in response")
                return AuthCallResult(error = friendlyError(msg ?: "Login failed"))
            }
            AuthCallResult(user = SupabaseUser(id, mail), accessToken = at, refreshToken = rt)
        } catch (e: Exception) {
            Log.e(TAG, "parseAuthResponse exception: ${e.message}")
            AuthCallResult(error = "Unexpected error. Please try again.")
        }
    }

    private fun friendlyError(raw: String) = when {
        raw.contains("already registered",  true) ||
        raw.contains("already exists",      true) ||
        raw.contains("User already",        true) -> "An account with this email already exists."
        raw.contains("invalid login",       true) ||
        raw.contains("Invalid login",       true) -> "Incorrect email or password."
        raw.contains("invalid",             true) -> "Invalid email or password."
        raw.contains("weak",                true) -> "Password must be at least 8 characters."
        raw.contains("not found",           true) -> "No account found with this email."
        raw.contains("rate limit",          true) -> "Too many attempts. Please wait."
        raw.contains("email not confirmed", true) ||
        raw.contains("Email not confirmed", true) -> "Please verify your email before signing in."
        raw.contains("signup",              true) -> "Registration failed. Please try again."
        else                                      -> raw
    }
}
