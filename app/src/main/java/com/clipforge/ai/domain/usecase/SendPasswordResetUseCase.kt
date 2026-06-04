package com.clipforge.ai.domain.usecase

import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.repository.AuthRepository

class SendPasswordResetUseCase(private val repo: AuthRepository) {
    suspend operator fun invoke(email: String): NetworkResult<Unit> = repo.sendPasswordReset(email)
}
