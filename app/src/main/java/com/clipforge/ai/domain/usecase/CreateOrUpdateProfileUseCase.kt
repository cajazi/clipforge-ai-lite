package com.clipforge.ai.domain.usecase

import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.model.UserProfile
import com.clipforge.ai.domain.repository.ProfileRepository

class CreateOrUpdateProfileUseCase(private val repo: ProfileRepository) {
    suspend operator fun invoke(profile: UserProfile): NetworkResult<UserProfile> =
        repo.createOrUpdateProfile(profile)
}
