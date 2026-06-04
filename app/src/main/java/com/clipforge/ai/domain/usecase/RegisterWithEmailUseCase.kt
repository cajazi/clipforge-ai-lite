package com.clipforge.ai.domain.usecase

import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.model.AuthUser
import com.clipforge.ai.domain.repository.AuthRepository
import com.clipforge.ai.domain.repository.RegisterResult

class RegisterWithEmailUseCase(private val repo: AuthRepository) {
    suspend operator fun invoke(email: String, password: String, name: String): NetworkResult<AuthUser> =
        when (val result = repo.registerWithEmail(name, email, password)) {
            is RegisterResult.Success -> NetworkResult.Success(result.user)
            is RegisterResult.NeedsConfirmation -> NetworkResult.Error(message = "Please verify your email before signing in.")
            is RegisterResult.Failure -> NetworkResult.Error(message = result.message)
        }
}
