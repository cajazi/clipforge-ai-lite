package com.clipforge.ai.presentation.history

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipforge.ai.data.local.database.ClipForgeDatabase
import com.clipforge.ai.data.remote.api.ProjectApi
import com.clipforge.ai.data.remote.dto.EditPlanDto
import com.clipforge.ai.data.remote.dto.ProjectDto
import com.clipforge.ai.data.repository.ProjectRepositoryImpl
import com.clipforge.ai.domain.model.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import retrofit2.Response

data class HistoryUiState(
    val projects: List<Project> = emptyList(),
    val isLoading: Boolean = true
)

private val noOpApi = object : ProjectApi {
    override suspend fun getProjects(): Response<List<ProjectDto>> = throw UnsupportedOperationException()
    override suspend fun getProject(id: String): Response<ProjectDto> = throw UnsupportedOperationException()
    override suspend fun createProject(project: ProjectDto): Response<ProjectDto> = throw UnsupportedOperationException()
    override suspend fun updateProject(id: String, project: ProjectDto): Response<ProjectDto> = throw UnsupportedOperationException()
    override suspend fun deleteProject(id: String): Response<Unit> = throw UnsupportedOperationException()
    override suspend fun submitEditPlan(id: String, plan: EditPlanDto): Response<Unit> = throw UnsupportedOperationException()
}

class ProjectHistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val db   = ClipForgeDatabase.getInstance(application)
    private val repo = ProjectRepositoryImpl(db.projectDao(), noOpApi)

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            repo.getProjects().collect { projects ->
                _uiState.value = _uiState.value.copy(projects = projects, isLoading = false)
            }
        }
    }
}
