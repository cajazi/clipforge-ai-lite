package com.clipforge.ai.domain.usecase

import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.repository.AuthRepository

class LogoutUseCase(private val repo: AuthRepository) {
    suspend operator fun invoke(): NetworkResult<Unit> = repo.logout()
}
