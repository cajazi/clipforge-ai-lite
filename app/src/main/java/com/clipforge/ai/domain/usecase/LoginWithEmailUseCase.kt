package com.clipforge.ai.domain.usecase

import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.model.AuthUser
import com.clipforge.ai.domain.repository.AuthRepository

class LoginWithEmailUseCase(private val repo: AuthRepository) {
    suspend operator fun invoke(email: String, password: String): NetworkResult<AuthUser> =
        repo.loginWithEmail(email, password)
}
