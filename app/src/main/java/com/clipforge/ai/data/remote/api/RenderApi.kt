package com.clipforge.ai.data.remote.api
import com.clipforge.ai.data.remote.dto.RenderJobDto
import retrofit2.Response
import retrofit2.http.*
interface RenderApi {
    @POST("render/{projectId}/start") suspend fun startRender(@Path("projectId") projectId: String, @Query("quality") quality: String, @Query("watermark") addWatermark: Boolean): Response<RenderJobDto>
    @GET("render/job/{jobId}") suspend fun getJobStatus(@Path("jobId") jobId: String): Response<RenderJobDto>
    @POST("render/job/{jobId}/cancel") suspend fun cancelJob(@Path("jobId") jobId: String): Response<Unit>
    @GET("render/history/{projectId}") suspend fun getRenderHistory(@Path("projectId") projectId: String): Response<List<RenderJobDto>>
}
