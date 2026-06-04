package com.clipforge.ai.core.auth

import android.util.Log
import com.clipforge.ai.BuildConfig
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import java.security.MessageDigest
import java.security.SecureRandom
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

private const val TAG = "AuthApiClient"
const val OAUTH_REDIRECT = "clipforgeai://auth-callback"
private const val MISSING_CONFIG_MESSAGE =
    "Supabase is not configured. Add SUPABASE_URL and SUPABASE_ANON_KEY to local.properties."

private fun isConfigured(): Boolean {
    return BuildConfig.SUPABASE_URL.isNotBlank() &&
        BuildConfig.SUPABASE_ANON_KEY.isNotBlank()
}

data class AuthResult(
    val userId: String?       = null,
    val email: String?        = null,
    val accessToken: String?  = null,
    val refreshToken: String? = null,
    val needsConfirmation: Boolean = false,
    val error: String?        = null
)

data class OAuthStart(
    val url: String,
    val codeVerifier: String
)

class AuthApiClient {

    private val gson = Gson()
    private val JSON = "application/json".toMediaType()

    private val http = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private val base = "${BuildConfig.SUPABASE_URL.trim().trimEnd('/')}/auth/v1"

    // ── Headers — these are what Supabase requires ────────────────────────
    private fun headers(token: String? = null): Headers {
        val anonKey = BuildConfig.SUPABASE_ANON_KEY.trim()
        val b = Headers.Builder()
            .add("apikey", anonKey)
            .add("Content-Type", "application/json")
        if (token != null) b.add("Authorization", "Bearer $token")
        else b.add("Authorization", "Bearer $anonKey")
        return b.build()
    }

    // ── Sign Up ───────────────────────────────────────────────────────────
    suspend fun signUp(email: String, password: String, name: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext AuthResult(error = MISSING_CONFIG_MESSAGE)
            Log.d(TAG, "signUp: $email")
            val body = gson.toJson(mapOf(
                "email" to email.trim(),
                "password" to password,
                "data" to mapOf("display_name" to name.trim())
            ))
            call("$base/signup", body)
        }

    // ── Sign In ───────────────────────────────────────────────────────────
    suspend fun signIn(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext AuthResult(error = MISSING_CONFIG_MESSAGE)
            Log.d(TAG, "signIn: $email")
            val body = gson.toJson(mapOf(
                "email" to email.trim(),
                "password" to password
            ))
            call("$base/token?grant_type=password", body)
        }

    // ── Refresh ───────────────────────────────────────────────────────────
    suspend fun refresh(refreshToken: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext AuthResult(error = MISSING_CONFIG_MESSAGE)
            val body = gson.toJson(mapOf("refresh_token" to refreshToken))
            call("$base/token?grant_type=refresh_token", body)
        }

    // ── Exchange PKCE auth code ───────────────────────────────────────────
    suspend fun exchangeCodeForSession(code: String, codeVerifier: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext AuthResult(error = MISSING_CONFIG_MESSAGE)
            val body = gson.toJson(mapOf(
                "auth_code" to code,
                "code_verifier" to codeVerifier
            ))
            call("$base/token?grant_type=pkce", body)
        }

    // ── Get user ──────────────────────────────────────────────────────────
    suspend fun getUser(accessToken: String): AuthResult =
        withContext(Dispatchers.IO) {
            if (!isConfigured()) return@withContext AuthResult(error = MISSING_CONFIG_MESSAGE)
            try {
                val req = Request.Builder().url("$base/user")
                    .headers(headers(accessToken)).get().build()
                val resp = http.newCall(req).execute()
                val json = resp.body?.string() ?: ""
                Log.d(TAG, "getUser [${resp.code}]: ${json.take(120)}")
                if (resp.isSuccessful) {
                    val obj = gson.fromJson(json, JsonObject::class.java)
                    AuthResult(
                        userId      = obj.get("id")?.asString,
                        email       = obj.get("email")?.asString,
                        accessToken = accessToken
                    )
                } else AuthResult(error = "Session expired")
            } catch (e: IOException) {
                Log.e(TAG, "getUser error: ${e.message}")
                AuthResult(error = "Network error")
            }
        }

    // ── Logout ────────────────────────────────────────────────────────────
    suspend fun signOut(accessToken: String) = withContext(Dispatchers.IO) {
        try {
            val req = Request.Builder().url("$base/logout")
                .headers(headers(accessToken))
                .post("{}".toRequestBody(JSON)).build()
            http.newCall(req).execute()
        } catch (_: IOException) {}
    }

    // ── Password reset ────────────────────────────────────────────────────
    suspend fun resetPassword(email: String): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) return@withContext false
        try {
            val body = gson.toJson(mapOf(
                "email" to email.trim(),
                "redirect_to" to OAUTH_REDIRECT
            ))
            val req  = Request.Builder().url("$base/recover")
                .headers(headers()).post(body.toRequestBody(JSON)).build()
            http.newCall(req).execute().isSuccessful
        } catch (_: IOException) { false }
    }

    // ── Google OAuth URL ──────────────────────────────────────────────────
    fun googleOAuthStart(): OAuthStart? {
        if (!isConfigured()) return null
        val codeVerifier = createCodeVerifier()
        val codeChallenge = codeChallengeS256(codeVerifier)
        val encoded = java.net.URLEncoder.encode(OAUTH_REDIRECT, "UTF-8")
        val encodedChallenge = java.net.URLEncoder.encode(codeChallenge, "UTF-8")
        val url = "$base/authorize" +
            "?provider=google" +
            "&redirect_to=$encoded" +
            "&code_challenge=$encodedChallenge" +
            "&code_challenge_method=S256"
        Log.d(TAG, "Google OAuth URL: $url")
        return OAuthStart(url = url, codeVerifier = codeVerifier)
    }

    // ── Internal POST helper ──────────────────────────────────────────────
    private fun call(url: String, bodyJson: String): AuthResult {
        if (!isConfigured()) return AuthResult(error = MISSING_CONFIG_MESSAGE)
        return try {
            val req = Request.Builder().url(url)
                .headers(headers())
                .post(bodyJson.toRequestBody(JSON)).build()
            val resp = http.newCall(req).execute()
            val json = resp.body?.string() ?: ""
            Log.d(TAG, "POST $url [${resp.code}]: ${json.take(300)}")
            parseResponse(json, resp.code)
        } catch (e: IOException) {
            Log.e(TAG, "call $url error: ${e.message}")
            AuthResult(error = "Network error. Check your connection.")
        }
    }

    private fun parseResponse(json: String, code: Int): AuthResult {
        return try {
            val obj = gson.fromJson(json, JsonObject::class.java)

            // Error fields
            val errDesc = obj.get("error_description")?.asString
            val errCode = obj.get("error")?.asString
            val msg     = obj.get("msg")?.asString

            if (errDesc != null || errCode != null) {
                val raw = errDesc ?: errCode ?: msg ?: "Error $code"
                Log.e(TAG, "Auth error: $raw")
                return AuthResult(error = friendly(raw))
            }

            // User object
            val userObj = obj.getAsJsonObject("user")
            val userId  = userObj?.get("id")?.asString ?: ""
            val email   = userObj?.get("email")?.asString ?: ""
            val at      = obj.get("access_token")?.asString
            val rt      = obj.get("refresh_token")?.asString

            Log.d(TAG, "parseResponse: userId=$userId at=${at?.take(10)} rt=${rt?.take(10)}")

            when {
                userId.isBlank() -> AuthResult(error = friendly(msg ?: "Auth failed (code $code)"))
                at.isNullOrBlank() -> AuthResult(          // signup, email confirmation required
                    userId = userId, email = email, needsConfirmation = true)
                else -> AuthResult(
                    userId = userId, email = email,
                    accessToken = at, refreshToken = rt)
            }
        } catch (e: Exception) {
            Log.e(TAG, "parse error: ${e.message} json=$json")
            AuthResult(error = "Unexpected error (code $code)")
        }
    }

    private fun friendly(raw: String) = when {
        raw.contains("already registered", true) ||
        raw.contains("already exists",     true) -> "An account with this email already exists."
        raw.contains("invalid login",      true) ||
        raw.contains("invalid credentials",true) -> "Incorrect email or password."
        raw.contains("weak",               true) -> "Password must be at least 8 characters."
        raw.contains("not found",          true) -> "No account found with this email."
        raw.contains("rate limit",         true) -> "Too many attempts. Please wait."
        raw.contains("confirmed",          true) -> "Please verify your email before signing in."
        raw.contains("401",                true) -> "Auth config error — check Supabase anon key."
        else -> raw
    }

    private fun createCodeVerifier(): String {
        val bytes = ByteArray(32)
        SecureRandom().nextBytes(bytes)
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)
    }

    private fun codeChallengeS256(codeVerifier: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(codeVerifier.toByteArray(Charsets.US_ASCII))
        return Base64.getUrlEncoder().withoutPadding().encodeToString(digest)
    }
}
