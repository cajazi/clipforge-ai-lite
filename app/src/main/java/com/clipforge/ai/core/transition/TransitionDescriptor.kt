package com.clipforge.ai.core.transition

/**
 * Pure metadata describing a transition — the single source of truth for both the export
 * pipeline and the preview, plus the panel UX.
 *
 * Adding a transition becomes: construct a [TransitionDescriptor] + a [TransitionRenderer]
 * (and optionally a [PreviewRenderer]) and register them. No executor / render-plan / panel
 * code changes.
 *
 * @param id            registry key (decoupled from the persisted enum)
 * @param displayName   user-facing label (panel + logs)
 * @param category      panel grouping
 * @param timingModel   generic consumption rule (drives render-plan clamp math)
 * @param defaultDurationMs default boundary duration when the user hasn't overridden it
 * @param easing        feel curve, shared by export + preview
 * @param isExportable  true if the export pipeline can bake it; false => preview must show
 *                      an honest plain cut (parity rule) until a real renderer exists
 * @param isPremium     entitlement gating (mirrors TransitionType.isPremium)
 *
 * Phase A: data only. No instances are created yet (registrations land in Phase B).
 */
data class TransitionDescriptor(
    val id: TransitionId,
    val displayName: String,
    val category: TransitionCategory,
    val timingModel: TimingModel,
    val defaultDurationMs: Long = 500L,
    val easing: Easing = Easing.Smoothstep,
    val isExportable: Boolean = true,
    val isPremium: Boolean = false
) {
    init {
        require(defaultDurationMs > 0L) { "defaultDurationMs must be > 0 for $id" }
    }

    /** Convenience: timeline consumed at a boundary for a given duration. */
    fun consumptionMs(durationMs: Long): Long = timingModel.consumptionMs(durationMs)
}
