package com.clipforge.ai.domain.model

data class AuthUser(
    val id: String,
    val email: String,
    val displayName: String? = null,
    val avatarUrl: String?   = null,
    val accessToken: String  = "",
    val refreshToken: String = ""
)
