package com.clipforge.ai.core.auth

import android.util.Log
import com.clipforge.ai.BuildConfig
import com.clipforge.ai.core.supabase.SUPABASE_MALFORMED_URL_MESSAGE
import com.clipforge.ai.core.supabase.SupabaseConfigValidator
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Headers
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException

private const val TAG = "AuthApiClient"
const val OAUTH_REDIRECT = "clipforgeai://auth-callback"

data class AuthResult(
    val userId: String? = null,
    val email: String? = null,
    val accessToken: String? = null,
    val refreshToken: String? = null,
    val needsConfirmation: Boolean = false,
    val error: String? = null
)

class AuthApiClient(
    rawSupabaseUrl: String = BuildConfig.SUPABASE_URL,
    rawAnonKey: String = BuildConfig.SUPABASE_ANON_KEY,
    private val expectedGoogleWebClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID,
    private val callFactory: Call.Factory = defaultHttpClient()
) {

    private val gson = Gson()
    private val jsonMediaType = "application/json".toMediaType()
    private val config = SupabaseConfigValidator.validate(rawSupabaseUrl, rawAnonKey)

    suspend fun signUp(email: String, password: String, name: String): AuthResult =
        withContext(Dispatchers.IO) {
            configError()?.let { return@withContext it }
            debug("signUp requested host=${config.host ?: "none"}")
            val body = gson.toJson(
                mapOf(
                    "email" to email.trim(),
                    "password" to password,
                    "data" to mapOf("display_name" to name.trim())
                )
            )
            post("/signup", body)
        }

    suspend fun signIn(email: String, password: String): AuthResult =
        withContext(Dispatchers.IO) {
            configError()?.let { return@withContext it }
            debug("signIn requested host=${config.host ?: "none"}")
            val body = gson.toJson(
                mapOf(
                    "email" to email.trim(),
                    "password" to password
                )
            )
            post("/token?grant_type=password", body)
        }

    suspend fun refresh(refreshToken: String): AuthResult =
        withContext(Dispatchers.IO) {
            configError()?.let { return@withContext it }
            val body = gson.toJson(mapOf("refresh_token" to refreshToken))
            post("/token?grant_type=refresh_token", body)
        }

    suspend fun exchangeCodeForSession(code: String, codeVerifier: String): AuthResult =
        withContext(Dispatchers.IO) {
            configError()?.let { return@withContext it }
            val body = gson.toJson(
                mapOf(
                    "auth_code" to code,
                    "code_verifier" to codeVerifier
                )
            )
            post("/token?grant_type=pkce", body)
        }

    suspend fun signInWithGoogleIdToken(idToken: String, nonce: String?): AuthResult =
        withContext(Dispatchers.IO) {
            configError()?.let { return@withContext it }
            if (idToken.isBlank()) {
                return@withContext AuthResult(error = "Google ID token not returned.")
            }
            val expectedAudience = expectedGoogleWebClientId.trim()
            val audience = GoogleIdTokenDiagnostics.inspect(idToken, expectedAudience)
            if (audience != null) {
                debug(
                    "google id token audience aud=${audience.audForLog} " +
                        "azp=${audience.azp ?: "none"} expected=$expectedAudience " +
                        "audMatchesExpected=${audience.audMatchesExpected}"
                )
                if (!audience.audMatchesExpected) {
                    return@withContext AuthResult(
                        error = "OAuth client ID mismatch. Check Google Web Client ID setup."
                    )
                }
            } else {
                debug("google id token audience unavailable expected=$expectedAudience")
            }
            debug(
                "signInWithGoogleIdToken requested host=${config.host ?: "none"} " +
                    "provider=google noncePresent=${!nonce.isNullOrBlank()}"
            )
            val payload = mutableMapOf(
                "provider" to "google",
                "id_token" to idToken
            )
            nonce?.takeIf { it.isNotBlank() }?.let { payload["nonce"] = it }
            val body = gson.toJson(payload)
            post("/token?grant_type=id_token", body, ::parseGoogleIdTokenResponse)
        }

    suspend fun getUser(accessToken: String): AuthResult =
        withContext(Dispatchers.IO) {
            configError()?.let { return@withContext it }
            try {
                val path = "/user"
                val request = Request.Builder()
                    .url("${config.authBaseUrl}$path")
                    .headers(headers(accessToken))
                    .get()
                    .build()
                val response = callFactory.newCall(request).execute()
                val body = response.body?.string().orEmpty()
                debugHttp(path, response.code, body)
                if (response.isSuccessful) {
                    val obj = parseJsonObject(body)
                    AuthResult(
                        userId = obj?.string("id"),
                        email = obj?.string("email"),
                        accessToken = accessToken
                    )
                } else {
                    AuthResult(error = "Session expired. Please log in again.")
                }
            } catch (e: IOException) {
                logIOException("/user", e)
                AuthResult(error = networkErrorMessage(e))
            } catch (e: IllegalArgumentException) {
                debug("getUser malformed url: ${e.message}")
                AuthResult(error = SUPABASE_MALFORMED_URL_MESSAGE)
            }
        }

    suspend fun signOut(accessToken: String) = withContext(Dispatchers.IO) {
        configError()?.let { return@withContext }
        try {
            val path = "/logout"
            val request = Request.Builder()
                .url("${config.authBaseUrl}$path")
                .headers(headers(accessToken))
                .post("{}".toRequestBody(jsonMediaType))
                .build()
            callFactory.newCall(request).execute().close()
        } catch (e: IOException) {
            logIOException("/logout", e)
        }
    }

    suspend fun resetPassword(email: String): Boolean = withContext(Dispatchers.IO) {
        if (configError() != null) return@withContext false
        try {
            val body = gson.toJson(
                mapOf(
                    "email" to email.trim(),
                    "redirect_to" to OAUTH_REDIRECT
                )
            )
            val path = "/recover"
            val request = Request.Builder()
                .url("${config.authBaseUrl}$path")
                .headers(headers())
                .post(body.toRequestBody(jsonMediaType))
                .build()
            val response = callFactory.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            debugHttp(path, response.code, responseBody)
            response.isSuccessful
        } catch (e: IOException) {
            logIOException("/recover", e)
            false
        } catch (e: IllegalArgumentException) {
            debug("resetPassword malformed url: ${e.message}")
            false
        }
    }

    private fun post(
        path: String,
        bodyJson: String,
        parser: (String, Int) -> AuthResult = ::parseResponse
    ): AuthResult {
        configError()?.let { return it }
        return try {
            val request = Request.Builder()
                .url("${config.authBaseUrl}$path")
                .headers(headers())
                .post(bodyJson.toRequestBody(jsonMediaType))
                .build()
            val response = callFactory.newCall(request).execute()
            val responseBody = response.body?.string().orEmpty()
            debugHttp(path, response.code, responseBody)
            parser(responseBody, response.code)
        } catch (e: IOException) {
            logIOException(path, e)
            AuthResult(error = networkErrorMessage(e))
        } catch (e: IllegalArgumentException) {
            debug("POST endpoint=$path malformed url: ${e.message}")
            AuthResult(error = SUPABASE_MALFORMED_URL_MESSAGE)
        }
    }

    internal fun parseResponse(json: String, code: Int): AuthResult =
        parseResponse(json, code, ::friendly)

    internal fun parseGoogleIdTokenResponse(json: String, code: Int): AuthResult =
        parseResponse(json, code, ::googleIdTokenFriendly)

    private fun parseResponse(
        json: String,
        code: Int,
        errorMapper: (String?, Int) -> String
    ): AuthResult {
        val obj = parseJsonObject(json)
        if (obj == null) {
            return AuthResult(
                error = if (code !in 200..299) errorMapper(null, code) else httpStatusMessage(code)
            )
        }

        val rawError = obj.errorText()
        if (code !in 200..299 || rawError != null) {
            return AuthResult(error = errorMapper(rawError, code))
        }

        val userObj = obj.objectOrNull("user")
        val userId = userObj?.string("id") ?: obj.string("id").orEmpty()
        val email = userObj?.string("email") ?: obj.string("email").orEmpty()
        val accessToken = obj.string("access_token")
        val refreshToken = obj.string("refresh_token")

        debug(
            "auth response parsed userIdPresent=${userId.isNotBlank()} " +
                "accessTokenPresent=${!accessToken.isNullOrBlank()} " +
                "refreshTokenPresent=${!refreshToken.isNullOrBlank()}"
        )

        return when {
            userId.isBlank() -> AuthResult(error = "Auth response was missing the user id.")
            accessToken.isNullOrBlank() -> AuthResult(
                userId = userId,
                email = email,
                needsConfirmation = true
            )
            else -> AuthResult(
                userId = userId,
                email = email,
                accessToken = accessToken,
                refreshToken = refreshToken
            )
        }
    }

    private fun headers(token: String? = null): Headers {
        val bearer = token?.takeIf { it.isNotBlank() } ?: config.anonKey
        return Headers.Builder()
            .add("apikey", config.anonKey)
            .add("Authorization", "Bearer $bearer")
            .add("Content-Type", "application/json")
            .build()
    }

    private fun configError(): AuthResult? {
        val message = config.error ?: return null
        debug(
            "config urlBlank=${config.urlBlank} urlMalformed=${config.urlMalformed} " +
                "keyBlank=${config.keyBlank} keyMalformed=${config.keyMalformed} " +
                "host=${config.host ?: "none"}"
        )
        return AuthResult(error = message)
    }

    private fun parseJsonObject(json: String): JsonObject? =
        runCatching { gson.fromJson(json, JsonObject::class.java) }.getOrNull()

    private fun JsonObject.objectOrNull(name: String): JsonObject? {
        val element = get(name) ?: return null
        return if (element.isJsonObject) element.asJsonObject else null
    }

    private fun JsonObject.string(name: String): String? {
        val element = get(name) ?: return null
        if (!element.isJsonPrimitive) return null
        return runCatching { element.asString }.getOrNull()?.takeIf { it.isNotBlank() }
    }

    private fun JsonObject.errorText(): String? {
        val parts = listOf(
            string("error_description"),
            string("msg"),
            string("message"),
            string("error"),
            string("error_code"),
            string("code")
        ).filterNotNull().filter { it.isNotBlank() }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" ")
    }

    private fun friendly(raw: String?, code: Int): String {
        val text = raw.orEmpty()
        val lower = text.lowercase()
        return when {
            code == 401 ||
                lower.contains("invalid api key") ||
                lower.contains("invalid apikey") ||
                lower.contains("jwt") -> "Auth config error. Check the Supabase anon key."
            lower.contains("invalid login") ||
                lower.contains("invalid credentials") ||
                lower.contains("invalid_credentials") -> "Incorrect email or password."
            lower.contains("email not confirmed") ||
                lower.contains("not confirmed") -> "Please verify your email before signing in."
            lower.contains("already registered") ||
                lower.contains("already exists") ||
                lower.contains("user already") -> "An account with this email already exists."
            lower.contains("weak") -> "Password must be at least 8 characters."
            lower.contains("not found") -> "No account found with this email."
            lower.contains("rate limit") -> "Too many attempts. Please wait."
            code == 404 -> "Supabase auth endpoint not found. Check SUPABASE_URL."
            code >= 500 -> "Supabase auth service error. Please try again."
            text.isNotBlank() -> sanitizeForLog(text).take(180)
            else -> httpStatusMessage(code)
        }
    }

    private fun googleIdTokenFriendly(raw: String?, code: Int): String {
        val text = raw.orEmpty()
        val lower = text.lowercase()
        return when {
            code == 401 ||
                lower.contains("invalid api key") ||
                lower.contains("invalid apikey") -> "Auth config error. Check the Supabase anon key."
            lower.contains("audience") ||
                lower.contains("invalid aud") ||
                lower.contains("client id") -> "OAuth client ID mismatch. Check Google Web Client ID setup."
            lower.contains("nonce") -> "Supabase rejected Google token: nonce verification failed."
            lower.contains("provider") ||
                lower.contains("disabled") ||
                lower.contains("not enabled") -> "Supabase rejected Google token: Google provider is not configured."
            code == 404 -> "Supabase auth endpoint not found. Check SUPABASE_URL."
            code >= 500 -> "Supabase auth service error. Please try again."
            text.isNotBlank() -> "Supabase rejected Google token: ${sanitizeForLog(text).take(140)}"
            code !in 200..299 -> "Supabase rejected Google token. (HTTP $code)"
            else -> "Supabase rejected Google token."
        }
    }

    private fun httpStatusMessage(code: Int): String = when {
        code == 401 -> "Auth config error. Check the Supabase anon key."
        code == 404 -> "Supabase auth endpoint not found. Check SUPABASE_URL."
        code >= 500 -> "Supabase auth service error. Please try again."
        code !in 200..299 -> "Auth request failed. Please try again. (HTTP $code)"
        else -> "Unexpected auth response."
    }

    private fun networkErrorMessage(error: IOException): String = when (error) {
        is UnknownHostException -> "Network DNS error. Check the Supabase URL or connection."
        is SocketTimeoutException -> "Network timeout contacting Supabase. Please try again."
        is SSLException -> "Secure connection failed. Check device date/time or network SSL settings."
        else -> "Network error. Check your connection."
    }

    private fun logIOException(path: String, error: IOException) {
        if (BuildConfig.DEBUG) {
            runCatching {
                Log.w(
                    TAG,
                    "auth endpoint=$path io=${error.javaClass.simpleName}: ${error.message.orEmpty().take(180)}"
                )
            }
        }
    }

    private fun debugHttp(path: String, code: Int, body: String) {
        debug("auth endpoint=$path status=$code body=${sanitizeForLog(body).take(300)}")
    }

    private fun debug(message: String) {
        if (BuildConfig.DEBUG) runCatching { Log.d(TAG, message) }
    }

    private fun sanitizeForLog(value: String): String =
        value.replace(SENSITIVE_JSON_FIELD) { match ->
            "\"${match.groupValues[1]}\":\"<redacted>\""
        }.replace(JWT_LIKE_VALUE, "<redacted-jwt>")

}

private val SENSITIVE_JSON_FIELD =
    Regex(
        "(?i)\"(access_token|refresh_token|id_token|password|apikey|authorization|" +
            "client_secret|clientSecret|anon_key|supabase_anon_key)\"\\s*:\\s*\"[^\"]*\""
    )

private val JWT_LIKE_VALUE =
    Regex("eyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+")

private fun defaultHttpClient(): OkHttpClient =
    OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
