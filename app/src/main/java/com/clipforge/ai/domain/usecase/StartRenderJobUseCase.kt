package com.clipforge.ai.domain.usecase
import com.clipforge.ai.core.billing.EntitlementManager
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.domain.model.ExportSettings
import com.clipforge.ai.domain.model.RenderJob
import com.clipforge.ai.domain.repository.RenderRepository
class StartRenderJobUseCase(
    private val renderRepository: RenderRepository,
    private val entitlementManager: EntitlementManager
) {
    suspend operator fun invoke(projectId: String): NetworkResult<RenderJob> =
        renderRepository.startRenderJob(projectId, ExportSettings.forPlan(entitlementManager.getCurrentPlan()))
}
