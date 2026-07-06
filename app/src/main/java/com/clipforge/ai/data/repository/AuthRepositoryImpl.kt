package com.clipforge.ai.data.repository

import android.net.Uri
import android.util.Log
import com.clipforge.ai.core.auth.AuthApiClient
import com.clipforge.ai.core.auth.AuthSessionManager
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.model.AuthUser
import com.clipforge.ai.domain.repository.AuthRepository
import com.clipforge.ai.domain.repository.RegisterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val TAG = "AuthRepositoryImpl"

class AuthRepositoryImpl(
    private val session: AuthSessionManager
) : AuthRepository {

    private val api = AuthApiClient()

    override suspend fun registerWithEmail(
        name: String, email: String, password: String
    ): RegisterResult = withContext(Dispatchers.IO) {
        Log.d(TAG, "registerWithEmail requested")
        val r = api.signUp(email, password, name)
        when {
            r.error != null          -> RegisterResult.Failure(r.error)
            r.needsConfirmation      -> RegisterResult.NeedsConfirmation
            r.accessToken != null    -> {
                val user = r.toUser(); session.saveSession(user)
                Log.d(TAG, "Register success: ${user.email}")
                RegisterResult.Success(user)
            }
            else -> RegisterResult.Failure("Registration failed. Please try again.")
        }
    }

    override suspend fun loginWithEmail(
        email: String, password: String
    ): NetworkResult<AuthUser> = withContext(Dispatchers.IO) {
        Log.d(TAG, "loginWithEmail requested")
        val r = api.signIn(email, password)
        when {
            r.error != null       -> NetworkResult.Error(message = r.error)
            r.accessToken != null -> {
                val user = r.toUser(); session.saveSession(user)
                Log.d(TAG, "Login success: ${user.email}")
                NetworkResult.Success(user)
            }
            else -> NetworkResult.Error(message = "Login failed. Please try again.")
        }
    }

    override suspend fun loginWithGoogleIdToken(
        idToken: String,
        nonce: String?
    ): NetworkResult<AuthUser> = withContext(Dispatchers.IO) {
        Log.d(TAG, "loginWithGoogleIdToken requested")
        val r = api.signInWithGoogleIdToken(idToken, nonce)
        when {
            r.error != null -> NetworkResult.Error(message = r.error)
            r.accessToken != null && r.userId != null -> {
                val user = r.toUser()
                session.saveSession(user)
                Log.d(TAG, "Google login success: ${user.email}")
                NetworkResult.Success(user)
            }
            else -> NetworkResult.Error(message = "Google sign-in failed. Please try again.")
        }
    }

    override suspend fun sendPasswordReset(email: String): NetworkResult<Unit> =
        withContext(Dispatchers.IO) {
            if (api.resetPassword(email)) {
                NetworkResult.Success(Unit)
            } else {
                NetworkResult.Error(message = "Could not send reset email.")
            }
        }

    override suspend fun handleDeepLink(uri: Uri): NetworkResult<AuthUser> =
        withContext(Dispatchers.IO) {
            Log.d(TAG, "handleDeepLink: ${uri.safeAuthSummary()}")
            val raw = uri.fragment?.takeIf { it.isNotBlank() }
                ?: uri.query?.takeIf { it.isNotBlank() }
                ?: return@withContext NetworkResult.Error(message = "No token in callback")

            val params = raw.split("&").associate { p ->
                val i = p.indexOf("=")
                if (i > 0) p.substring(0, i) to
                    runCatching { java.net.URLDecoder.decode(p.substring(i+1), "UTF-8") }
                        .getOrDefault(p.substring(i+1))
                else p to ""
            }

            val at = params["access_token"]  ?: ""
            val rt = params["refresh_token"] ?: ""
            val code = params["code"] ?: ""
            val error = params["error_description"] ?: params["error"]
            Log.d(
                TAG,
                "callback fields accessToken=${at.isNotBlank()} refreshToken=${rt.isNotBlank()} " +
                    "code=${code.isNotBlank()} error=${!error.isNullOrBlank()}"
            )

            if (!error.isNullOrBlank()) {
                session.clearOAuthCodeVerifier()
                return@withContext NetworkResult.Error(message = error)
            }

            if (at.isBlank() && code.isNotBlank()) {
                val verifier = session.getOAuthCodeVerifier()
                    ?: return@withContext NetworkResult.Error(message = "OAuth session expired. Please try again.")
                val exchanged = api.exchangeCodeForSession(code, verifier)
                session.clearOAuthCodeVerifier()
                return@withContext when {
                    exchanged.error != null -> NetworkResult.Error(message = exchanged.error)
                    exchanged.accessToken != null && exchanged.userId != null -> {
                        val user = exchanged.toUser()
                        session.saveSession(user)
                        Log.d(TAG, "PKCE session restored: ${user.email}")
                        NetworkResult.Success(user)
                    }
                    else -> NetworkResult.Error(message = "Google sign-in failed")
                }
            }

            session.clearOAuthCodeVerifier()
            if (at.isBlank()) return@withContext NetworkResult.Error(message = "No token received")

            val r = api.getUser(at)
            if (r.userId != null) {
                val user = AuthUser(id = r.userId, email = r.email ?: "",
                    accessToken = at, refreshToken = rt)
                session.saveSession(user)
                Log.d(TAG, "Session restored: ${user.email}")
                NetworkResult.Success(user)
            } else {
                NetworkResult.Error(message = r.error ?: "Google sign-in failed")
            }
        }

    override suspend fun logout(): NetworkResult<Unit> {
        session.getAccessToken()?.let { api.signOut(it) }
        session.clearSession()
        return NetworkResult.Success(Unit)
    }

    override suspend fun getCurrentSession(): AuthUser? = session.getSession()

    override suspend fun restoreSession(): NetworkResult<AuthUser> =
        withContext(Dispatchers.IO) {
            val stored = session.getSession()
                ?: return@withContext NetworkResult.Error(message = "No session")
            val r = api.getUser(stored.accessToken)
            when {
                r.userId != null -> {
                    val user = stored.copy(email = r.email ?: stored.email)
                    Log.d(TAG, "Session valid: ${user.email}")
                    NetworkResult.Success(user)
                }
                stored.refreshToken.isNotBlank() -> {
                    val ref = api.refresh(stored.refreshToken)
                    if (ref.accessToken != null && ref.userId != null) {
                        val user = ref.toUser(); session.saveSession(user)
                        Log.d(TAG, "Session refreshed: ${user.email}")
                        NetworkResult.Success(user)
                    } else {
                        session.clearSession()
                        NetworkResult.Error(message = "Session expired")
                    }
                }
                else -> { session.clearSession(); NetworkResult.Error(message = "Session invalid") }
            }
        }

    private fun com.clipforge.ai.core.auth.AuthResult.toUser() = AuthUser(
        id           = userId ?: "",
        email        = email  ?: "",
        accessToken  = accessToken  ?: "",
        refreshToken = refreshToken ?: ""
    )
}

private fun Uri.safeAuthSummary(): String {
    val raw = fragment?.takeIf { it.isNotBlank() } ?: query.orEmpty()
    return "scheme=$scheme host=$host accessToken=${raw.contains("access_token=")} " +
        "refreshToken=${raw.contains("refresh_token=")} code=${raw.contains("code=")} " +
        "error=${raw.contains("error=") || raw.contains("error_description=")}"
}
