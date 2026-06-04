package com.clipforge.ai.domain.repository
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.model.ExportSettings
import com.clipforge.ai.domain.model.RenderJob
import kotlinx.coroutines.flow.Flow
interface RenderRepository {
    suspend fun startRenderJob(projectId: String, settings: ExportSettings): NetworkResult<RenderJob>
    suspend fun cancelRenderJob(jobId: String): NetworkResult<Unit>
    fun observeRenderJob(jobId: String): Flow<RenderJob>
    suspend fun getRenderHistory(projectId: String): NetworkResult<List<RenderJob>>
}
