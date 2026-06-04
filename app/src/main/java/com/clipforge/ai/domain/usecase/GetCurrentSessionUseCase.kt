package com.clipforge.ai.domain.usecase

import com.clipforge.ai.domain.model.AuthUser
import com.clipforge.ai.domain.repository.AuthRepository

class GetCurrentSessionUseCase(private val repo: AuthRepository) {
    suspend operator fun invoke(): AuthUser? = repo.getCurrentSession()
}
