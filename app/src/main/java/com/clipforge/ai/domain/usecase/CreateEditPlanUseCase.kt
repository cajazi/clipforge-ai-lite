package com.clipforge.ai.domain.usecase
import com.clipforge.ai.domain.model.Project
import com.clipforge.ai.domain.model.TimelineItem
class CreateEditPlanUseCase {
    operator fun invoke(project: Project, timeline: List<TimelineItem>): Map<String, Any> = mapOf(
        "projectId" to project.id, "aspectRatio" to project.aspectRatio.label,
        "quality" to project.exportQuality.label, "clips" to timeline.map { it.id }
    )
}
