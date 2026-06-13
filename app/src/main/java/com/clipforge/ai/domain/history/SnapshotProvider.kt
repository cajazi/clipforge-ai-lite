package com.clipforge.ai.domain.history

interface SnapshotProvider<T> {
    val providerId: String

    suspend fun capture(projectId: String): T

    suspend fun restore(projectId: String, snapshot: T)
}
