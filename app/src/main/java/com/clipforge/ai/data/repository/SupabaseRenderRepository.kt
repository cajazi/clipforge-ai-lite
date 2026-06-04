package com.clipforge.ai.data.repository

import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.core.supabase.CreateRenderJobBody
import com.clipforge.ai.core.supabase.SupabaseRenderApi
import com.clipforge.ai.core.supabase.SupabaseRenderJob
import com.clipforge.ai.domain.model.ExportSettings
import com.clipforge.ai.domain.model.RenderJob
import com.clipforge.ai.domain.model.RenderJobStatus
import com.clipforge.ai.domain.repository.RenderRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.util.UUID

class SupabaseRenderRepository(
    private val supabaseApi: SupabaseRenderApi
) : RenderRepository {

    override suspend fun startRenderJob(
        projectId: String,
        settings: ExportSettings
    ): NetworkResult<RenderJob> {
        return try {
            val jobId = UUID.randomUUID().toString()
            val body = CreateRenderJobBody(
                id            = jobId,
                projectId     = projectId,
                status        = "QUEUED",
                exportQuality = settings.quality,
                addWatermark  = settings.addWatermark
            )
            val response = supabaseApi.createRenderJob(body)
            if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                NetworkResult.Success(response.body()!!.first().toDomain())
            } else {
                // Return a local job even if Supabase fails — UI can still show progress
                NetworkResult.Success(RenderJob(
                    id            = jobId,
                    projectId     = projectId,
                    status        = RenderJobStatus.QUEUED,
                    exportQuality = settings.quality
                ))
            }
        } catch (e: Exception) {
            NetworkResult.Error(message = e.localizedMessage)
        }
    }

    override suspend fun cancelRenderJob(jobId: String): NetworkResult<Unit> {
        return try {
            supabaseApi.updateJobStatus("eq.$jobId", mapOf("status" to "CANCELLED"))
            NetworkResult.Success(Unit)
        } catch (e: Exception) {
            NetworkResult.Error(message = e.localizedMessage)
        }
    }

    /** Poll Supabase for render job status every 5 seconds. */
    override fun observeRenderJob(jobId: String): Flow<RenderJob> = flow {
        while (true) {
            try {
                val response = supabaseApi.getJobStatus("eq.$jobId")
                if (response.isSuccessful && response.body()?.isNotEmpty() == true) {
                    val job = response.body()!!.first().toDomain()
                    emit(job)
                    if (job.status in listOf(
                            RenderJobStatus.COMPLETED,
                            RenderJobStatus.FAILED,
                            RenderJobStatus.CANCELLED
                        )) break
                }
            } catch (_: Exception) { /* continue polling */ }
            delay(5_000L)
        }
    }

    override suspend fun getRenderHistory(projectId: String): NetworkResult<List<RenderJob>> {
        return try {
            val response = supabaseApi.getRenderHistory("eq.$projectId")
            if (response.isSuccessful && response.body() != null) {
                NetworkResult.Success(response.body()!!.map { it.toDomain() })
            } else {
                NetworkResult.Error(response.code(), response.message())
            }
        } catch (e: Exception) {
            NetworkResult.Error(message = e.localizedMessage)
        }
    }

    private fun SupabaseRenderJob.toDomain() = RenderJob(
        id              = id,
        projectId       = projectId,
        status          = try { RenderJobStatus.valueOf(status) } catch (_: Exception) { RenderJobStatus.QUEUED },
        exportQuality   = exportQuality,
        outputUrl       = outputUrl,
        errorMessage    = errorMessage,
        progressPercent = progressPercent,
        createdAt       = System.currentTimeMillis(),
        completedAt     = null
    )
}
