package com.clipforge.ai.domain.repository

import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.model.UserProfile

interface ProfileRepository {
    suspend fun getProfile(userId: String): NetworkResult<UserProfile>
    suspend fun createOrUpdateProfile(profile: UserProfile): NetworkResult<UserProfile>
}
