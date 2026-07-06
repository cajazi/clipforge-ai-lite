package com.clipforge.ai.domain.repository

import android.net.Uri
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.model.AuthUser

sealed class RegisterResult {
    data class Success(val user: AuthUser)  : RegisterResult()
    object NeedsConfirmation                : RegisterResult()
    data class Failure(val message: String) : RegisterResult()
}

interface AuthRepository {
    suspend fun registerWithEmail(name: String, email: String, password: String): RegisterResult
    suspend fun loginWithEmail(email: String, password: String): NetworkResult<AuthUser>
    suspend fun loginWithGoogleIdToken(idToken: String, nonce: String?): NetworkResult<AuthUser>
    suspend fun handleDeepLink(uri: Uri): NetworkResult<AuthUser>
    suspend fun sendPasswordReset(email: String): NetworkResult<Unit>
    suspend fun logout(): NetworkResult<Unit>
    suspend fun getCurrentSession(): AuthUser?
    suspend fun restoreSession(): NetworkResult<AuthUser>
}
