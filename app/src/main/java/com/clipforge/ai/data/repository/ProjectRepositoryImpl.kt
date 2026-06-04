package com.clipforge.ai.data.repository
import com.clipforge.ai.core.billing.PlanType
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.data.local.dao.ProjectDao
import com.clipforge.ai.data.local.entity.ProjectEntity
import com.clipforge.ai.data.remote.api.ProjectApi
import com.clipforge.ai.domain.model.*
import com.clipforge.ai.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
class ProjectRepositoryImpl(private val projectDao: ProjectDao, private val projectApi: ProjectApi) : ProjectRepository {
    override fun getProjects(): Flow<List<Project>> = projectDao.getAllProjects().map { it.map { e -> e.toDomain() } }
    override suspend fun getProjectById(id: String): Project? = projectDao.getProjectById(id)?.toDomain()
    override suspend fun createProject(project: Project): NetworkResult<Project> { projectDao.upsertProject(project.toEntity()); return NetworkResult.Success(project) }
    override suspend fun saveProject(project: Project): NetworkResult<Project> { projectDao.upsertProject(project.toEntity()); return NetworkResult.Success(project) }
    override suspend fun deleteProject(id: String): NetworkResult<Unit> { projectDao.deleteProjectById(id); return NetworkResult.Success(Unit) }
    private fun ProjectEntity.toDomain() = Project(id, title, AspectRatio.valueOf(aspectRatio), ExportQuality.valueOf(exportQuality), createdAt, updatedAt, PlanType.valueOf(planType), thumbnailUri)
    private fun Project.toEntity() = ProjectEntity(id, title, aspectRatio.name, exportQuality.name, planType.name, thumbnailUri, createdAt, updatedAt)
}
