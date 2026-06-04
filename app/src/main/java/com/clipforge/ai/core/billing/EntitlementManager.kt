package com.clipforge.ai.core.billing
class EntitlementManager {
    private var currentPlan: PlanType = PlanType.FREE
    fun isPro(): Boolean = currentPlan == PlanType.PRO
    fun isFree(): Boolean = currentPlan == PlanType.FREE
    fun setPlan(plan: PlanType) { currentPlan = plan }
    fun getCurrentPlan(): PlanType = currentPlan
}
