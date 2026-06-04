package com.clipforge.ai.domain.usecase
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.model.Project
import com.clipforge.ai.domain.repository.ProjectRepository
class SaveProjectUseCase(private val repository: ProjectRepository) {
    suspend operator fun invoke(project: Project): NetworkResult<Project> =
        repository.saveProject(project.copy(updatedAt = System.currentTimeMillis()))
}
