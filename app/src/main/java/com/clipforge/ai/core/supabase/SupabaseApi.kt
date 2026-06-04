package com.clipforge.ai.core.supabase

import retrofit2.Response
import retrofit2.http.*

interface SupabaseProjectApi {

    @GET("projects")
    suspend fun getProjects(
        @Query("order") order: String = "updated_at.desc",
        @Query("select") select: String = "*"
    ): Response<List<SupabaseProject>>

    @GET("projects")
    suspend fun getProjectById(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<SupabaseProject>>

    @POST("projects")
    suspend fun createProject(
        @Body project: CreateProjectBody
    ): Response<List<SupabaseProject>>

    @PATCH("projects")
    suspend fun updateProject(
        @Query("id") idFilter: String,
        @Body project: CreateProjectBody
    ): Response<List<SupabaseProject>>

    @DELETE("projects")
    suspend fun deleteProject(
        @Query("id") idFilter: String
    ): Response<Unit>
}

interface SupabaseRenderApi {

    @GET("render_jobs")
    suspend fun getJobStatus(
        @Query("id") idFilter: String,
        @Query("select") select: String = "*"
    ): Response<List<SupabaseRenderJob>>

    @POST("render_jobs")
    suspend fun createRenderJob(
        @Body job: CreateRenderJobBody
    ): Response<List<SupabaseRenderJob>>

    @PATCH("render_jobs")
    suspend fun updateJobStatus(
        @Query("id") idFilter: String,
        @Body update: Map<String, Any>
    ): Response<List<SupabaseRenderJob>>

    @GET("render_jobs")
    suspend fun getRenderHistory(
        @Query("project_id") projectIdFilter: String,
        @Query("order") order: String = "created_at.desc",
        @Query("select") select: String = "*"
    ): Response<List<SupabaseRenderJob>>
}

interface SupabaseMediaApi {

    @GET("media_assets")
    suspend fun getAssetsForProject(
        @Query("project_id") projectIdFilter: String,
        @Query("order") order: String = "created_at.asc",
        @Query("select") select: String = "*"
    ): Response<List<SupabaseMediaAsset>>

    @POST("media_assets")
    suspend fun insertMediaAsset(
        @Body asset: SupabaseMediaAsset
    ): Response<List<SupabaseMediaAsset>>

    @DELETE("media_assets")
    suspend fun deleteAsset(
        @Query("id") idFilter: String
    ): Response<Unit>
}
