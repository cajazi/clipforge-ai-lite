package com.clipforge.ai.data.remote.api
import com.clipforge.ai.data.remote.dto.EditPlanDto
import com.clipforge.ai.data.remote.dto.ProjectDto
import retrofit2.Response
import retrofit2.http.*
interface ProjectApi {
    @GET("projects") suspend fun getProjects(): Response<List<ProjectDto>>
    @GET("projects/{id}") suspend fun getProject(@Path("id") id: String): Response<ProjectDto>
    @POST("projects") suspend fun createProject(@Body project: ProjectDto): Response<ProjectDto>
    @PUT("projects/{id}") suspend fun updateProject(@Path("id") id: String, @Body project: ProjectDto): Response<ProjectDto>
    @DELETE("projects/{id}") suspend fun deleteProject(@Path("id") id: String): Response<Unit>
    @POST("projects/{id}/edit-plan") suspend fun submitEditPlan(@Path("id") id: String, @Body plan: EditPlanDto): Response<Unit>
}
