package com.clipforge.ai.data.repository

import com.clipforge.ai.core.billing.PlanType
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.core.supabase.CreateProjectBody
import com.clipforge.ai.core.supabase.SupabaseProjectApi
import com.clipforge.ai.data.local.dao.ProjectDao
import com.clipforge.ai.data.local.entity.ProjectEntity
import com.clipforge.ai.domain.model.*
import com.clipforge.ai.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class SupabaseProjectRepository(
    private val projectDao: ProjectDao,
    private val supabaseApi: SupabaseProjectApi
) : ProjectRepository {

    override fun getProjects(): Flow<List<Project>> =
        projectDao.getAllProjects().map { it.map { e -> e.toDomain() } }

    override suspend fun getProjectById(id: String): Project? =
        projectDao.getProjectById(id)?.toDomain()

    override suspend fun createProject(project: Project): NetworkResult<Project> {
        projectDao.upsertProject(project.toEntity())
        return try {
            val body = CreateProjectBody(
                id            = project.id,
                title         = project.title,
                aspectRatio   = project.aspectRatio.name,
                exportQuality = project.exportQuality.name,
                planType      = project.planType.name
            )
            supabaseApi.createProject(body)
            NetworkResult.Success(project)
        } catch (e: Exception) {
            NetworkResult.Success(project) // local save succeeded
        }
    }

    override suspend fun saveProject(project: Project): NetworkResult<Project> {
        projectDao.upsertProject(project.toEntity())
        return try {
            val body = CreateProjectBody(
                id            = project.id,
                title         = project.title,
                aspectRatio   = project.aspectRatio.name,
                exportQuality = project.exportQuality.name
            )
            supabaseApi.updateProject("eq.${project.id}", body)
            NetworkResult.Success(project)
        } catch (e: Exception) {
            NetworkResult.Success(project)
        }
    }

    override suspend fun deleteProject(id: String): NetworkResult<Unit> {
        projectDao.deleteProjectById(id)
        return try { supabaseApi.deleteProject("eq.$id"); NetworkResult.Success(Unit) }
        catch (e: Exception) { NetworkResult.Success(Unit) }
    }

    // ── Mappers ───────────────────────────────────────────────────────────

    private fun ProjectEntity.toDomain(): Project {
        val autoSettings = if (projectType == ProjectType.AUTO_EDIT.name &&
            autoFinalDuration != null && autoSecondsPerClip != null) {
            AutoEditSettings(
                finalDurationSeconds = autoFinalDuration,
                secondsPerClip       = autoSecondsPerClip,
                transitionType       = autoTransitionType?.let {
                    runCatching { TransitionType.valueOf(it) }.getOrDefault(TransitionType.FADE)
                } ?: TransitionType.FADE,
                musicEnabled  = autoMusicEnabled,
                musicAssetId  = autoMusicAssetId
            )
        } else null

        return Project(
            id               = id,
            title            = title,
            aspectRatio      = AspectRatio.valueOf(aspectRatio),
            exportQuality    = ExportQuality.valueOf(exportQuality),
            planType         = PlanType.valueOf(planType),
            thumbnailUri     = thumbnailUri,
            createdAt        = createdAt,
            updatedAt        = updatedAt,
            projectType      = runCatching { ProjectType.valueOf(projectType) }.getOrDefault(ProjectType.MANUAL),
            autoEditSettings = autoSettings
        )
    }

    private fun Project.toEntity() = ProjectEntity(
        id            = id,
        title         = title,
        aspectRatio   = aspectRatio.name,
        exportQuality = exportQuality.name,
        planType      = planType.name,
        thumbnailUri  = thumbnailUri,
        createdAt     = createdAt,
        updatedAt     = updatedAt,
        projectType   = projectType.name,
        autoFinalDuration    = autoEditSettings?.finalDurationSeconds,
        autoSecondsPerClip   = autoEditSettings?.secondsPerClip,
        autoTransitionType   = autoEditSettings?.transitionType?.name,
        autoMusicEnabled     = autoEditSettings?.musicEnabled ?: false,
        autoMusicAssetId     = autoEditSettings?.musicAssetId
    )
}
