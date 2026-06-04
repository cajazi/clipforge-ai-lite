package com.clipforge.ai.domain.model
import com.clipforge.ai.core.billing.PlanType
data class ExportSettings(
    val quality: String, val addWatermark: Boolean, val planType: PlanType,
    val frameRate: Int = 30, val audioBitrate: String = "128k", val videoBitrate: String? = null
) {
    companion object {
        fun forPlan(plan: PlanType) = ExportSettings(
            quality = if (plan == PlanType.PRO) "1080p" else "720p",
            addWatermark = plan == PlanType.FREE, planType = plan
        )
    }
}
