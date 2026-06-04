package com.clipforge.ai.domain.model

import com.clipforge.ai.core.billing.PlanType

enum class UserRole { USER, ADMIN }

data class UserProfile(
    val id: String,
    val email: String,
    val displayName: String       = "",
    val avatarUrl: String?        = null,
    val plan: PlanType            = PlanType.FREE,
    val role: UserRole            = UserRole.USER,
    val createdAt: String         = "",
    val updatedAt: String         = ""
) {
    val isPro   get() = plan == PlanType.PRO  || role == UserRole.ADMIN
    val isAdmin get() = role == UserRole.ADMIN
}
