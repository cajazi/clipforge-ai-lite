package com.clipforge.ai.core.billing
enum class PlanType { FREE, PRO }
data class SubscriptionPlan(
    val planType: PlanType, val productId: String,
    val displayName: String, val priceFormatted: String
) {
    companion object {
        const val PRO_MONTHLY_ID = "clipforge_pro_monthly"
        const val PRO_YEARLY_ID  = "clipforge_pro_yearly"
    }
}
