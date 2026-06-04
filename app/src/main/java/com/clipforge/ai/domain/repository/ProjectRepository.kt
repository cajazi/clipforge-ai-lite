package com.clipforge.ai.domain.repository
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.model.Project
import kotlinx.coroutines.flow.Flow
interface ProjectRepository {
    fun getProjects(): Flow<List<Project>>
    suspend fun getProjectById(id: String): Project?
    suspend fun createProject(project: Project): NetworkResult<Project>
    suspend fun saveProject(project: Project): NetworkResult<Project>
    suspend fun deleteProject(id: String): NetworkResult<Unit>
}
