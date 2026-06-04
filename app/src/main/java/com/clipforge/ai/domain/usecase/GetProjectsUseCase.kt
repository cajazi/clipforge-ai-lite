package com.clipforge.ai.domain.usecase
import com.clipforge.ai.domain.model.Project
import com.clipforge.ai.domain.repository.ProjectRepository
import kotlinx.coroutines.flow.Flow
class GetProjectsUseCase(private val repository: ProjectRepository) {
    operator fun invoke(): Flow<List<Project>> = repository.getProjects()
}
