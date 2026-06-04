package com.clipforge.ai.data.repository
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.data.remote.api.RenderApi
import com.clipforge.ai.data.remote.dto.RenderJobDto
import com.clipforge.ai.domain.model.*
import com.clipforge.ai.domain.repository.RenderRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
class RenderRepositoryImpl(private val renderApi: RenderApi) : RenderRepository {
    override suspend fun startRenderJob(projectId: String, settings: ExportSettings): NetworkResult<RenderJob> =
        try { val r = renderApi.startRender(projectId, settings.quality, settings.addWatermark)
            if (r.isSuccessful && r.body() != null) NetworkResult.Success(r.body()!!.toDomain())
            else NetworkResult.Error(r.code(), r.message()) }
        catch (e: Exception) { NetworkResult.Error(message = e.localizedMessage) }
    override suspend fun cancelRenderJob(jobId: String): NetworkResult<Unit> =
        try { renderApi.cancelJob(jobId); NetworkResult.Success(Unit) }
        catch (e: Exception) { NetworkResult.Error(message = e.localizedMessage) }
    override fun observeRenderJob(jobId: String): Flow<RenderJob> = flow {
        while (true) {
            try { val r = renderApi.getJobStatus(jobId)
                if (r.isSuccessful && r.body() != null) { val j = r.body()!!.toDomain(); emit(j)
                    if (j.status in listOf(RenderJobStatus.COMPLETED, RenderJobStatus.FAILED, RenderJobStatus.CANCELLED)) break }
            } catch (_: Exception) {}
            delay(5_000L)
        }
    }
    override suspend fun getRenderHistory(projectId: String): NetworkResult<List<RenderJob>> =
        try { val r = renderApi.getRenderHistory(projectId)
            if (r.isSuccessful && r.body() != null) NetworkResult.Success(r.body()!!.map { it.toDomain() })
            else NetworkResult.Error(r.code(), r.message()) }
        catch (e: Exception) { NetworkResult.Error(message = e.localizedMessage) }
    private fun RenderJobDto.toDomain() = RenderJob(id, projectId, RenderJobStatus.valueOf(status), exportQuality, outputUrl, errorMessage, progressPercent, createdAt, completedAt)
}
