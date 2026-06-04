package com.clipforge.ai.domain.usecase
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.model.*
import com.clipforge.ai.domain.repository.ProjectRepository
import java.util.UUID
class CreateProjectUseCase(private val repository: ProjectRepository) {
    suspend operator fun invoke(title: String, aspectRatio: AspectRatio): NetworkResult<Project> {
        val project = Project(id = UUID.randomUUID().toString(), title = title,
            aspectRatio = aspectRatio, exportQuality = ExportQuality.QUALITY_720P,
            createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis())
        return repository.createProject(project)
    }
}
