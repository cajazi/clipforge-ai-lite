package com.clipforge.ai.core.auth

import android.content.Context
import android.net.Uri
import android.util.Log
import com.clipforge.ai.core.billing.PlanType
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.core.supabase.SupabaseConfig
import com.clipforge.ai.data.repository.AuthRepositoryImpl
import com.clipforge.ai.data.repository.ProfileRepositoryImpl
import com.clipforge.ai.domain.model.AuthUser
import com.clipforge.ai.domain.model.UserProfile
import com.clipforge.ai.domain.model.UserRole
import com.clipforge.ai.domain.repository.AuthRepository
import com.clipforge.ai.domain.repository.RegisterResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

private const val TAG = "AuthManager"

sealed class AuthState {
    object Loading   : AuthState()
    object LoggedOut : AuthState()
    data class LoggedIn(val user: AuthUser, val profile: UserProfile) : AuthState()
}

sealed class RegisterState {
    data class Success(val user: AuthUser) : RegisterState()
    object NeedsEmailConfirmation          : RegisterState()
    data class Error(val message: String)  : RegisterState()
}

class AuthManager(context: Context) {

    val sessionManager              = AuthSessionManager(context)
    val authRepo: AuthRepository    = AuthRepositoryImpl(sessionManager)
    val profileRepo                 = ProfileRepositoryImpl(sessionManager)

    private val _authState = MutableStateFlow<AuthState>(AuthState.Loading)
    val authState: StateFlow<AuthState> = _authState.asStateFlow()

    suspend fun initialize() = withContext(Dispatchers.IO) {
        Log.d(TAG, "Initializing...")
        when (val r = authRepo.restoreSession()) {
            is NetworkResult.Success -> { Log.d(TAG, "Session ok: ${r.data.email}"); syncAndLoad(r.data) }
            else -> { Log.d(TAG, "No session"); _authState.value = AuthState.LoggedOut }
        }
    }

    suspend fun loginWithEmail(email: String, password: String): NetworkResult<AuthUser> =
        withContext(Dispatchers.IO) {
            when (val r = authRepo.loginWithEmail(email, password)) {
                is NetworkResult.Success -> { syncAndLoad(r.data); r }
                else -> r
            }
        }

    suspend fun registerWithEmail(name: String, email: String, password: String): RegisterState =
        withContext(Dispatchers.IO) {
            when (val r = authRepo.registerWithEmail(name, email, password)) {
                is RegisterResult.Success       -> { syncAndLoad(r.user); RegisterState.Success(r.user) }
                is RegisterResult.NeedsConfirmation -> RegisterState.NeedsEmailConfirmation
                is RegisterResult.Failure       -> RegisterState.Error(r.message)
            }
        }

    suspend fun handleDeepLink(uri: Uri): NetworkResult<AuthUser> =
        withContext(Dispatchers.IO) {
            when (val r = authRepo.handleDeepLink(uri)) {
                is NetworkResult.Success -> { syncAndLoad(r.data); r }
                else -> r
            }
        }

    suspend fun logout() { authRepo.logout(); _authState.value = AuthState.LoggedOut }

    fun getGoogleOAuthUrl(): String = kotlinx.coroutines.runBlocking { authRepo.loginWithGoogle() }

    fun getAuthConfigError(): String? = SupabaseConfig.validationError()

    private suspend fun syncAndLoad(user: AuthUser) {
        // Upsert profile — DB trigger may have already created it
        val existing = profileRepo.getProfile(user.id)
        if (existing !is NetworkResult.Success) {
            val isAdmin = user.email.trim().lowercase() == SupabaseConfig.ADMIN_EMAIL.lowercase()
            profileRepo.createOrUpdateProfile(UserProfile(
                id          = user.id,
                email       = user.email,
                displayName = user.displayName ?: user.email.substringBefore("@"),
                plan        = if (isAdmin) PlanType.PRO  else PlanType.FREE,
                role        = if (isAdmin) UserRole.ADMIN else UserRole.USER
            ))
            Log.d(TAG, "Profile synced: ${user.email}")
        }
        val profile = (profileRepo.getProfile(user.id) as? NetworkResult.Success)?.data
            ?: UserProfile(id = user.id, email = user.email,
                displayName = user.displayName ?: user.email.substringBefore("@"))
        _authState.value = AuthState.LoggedIn(user, profile)
        Log.d(TAG, "LoggedIn: ${user.email} | ${profile.plan} | ${profile.role}")
    }

    fun currentUser()    = (_authState.value as? AuthState.LoggedIn)?.user
    fun currentProfile() = (_authState.value as? AuthState.LoggedIn)?.profile
    fun isLoggedIn()     = _authState.value is AuthState.LoggedIn
}
