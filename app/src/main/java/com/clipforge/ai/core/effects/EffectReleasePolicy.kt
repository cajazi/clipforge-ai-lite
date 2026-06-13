package com.clipforge.ai.core.effects

/**
 * Release governance for effects.
 *
 * Registration, export readiness, and user release are intentionally separate:
 * preview can render registered effects while export only attaches export-ready effects.
 */
data class EffectReleasePolicy(
    val exportReadyIds: Set<String> = emptySet(),
    val releasedIds: Set<String> = emptySet()
) {
    init {
        require(exportReadyIds.containsAll(releasedIds)) {
            "releasedIds must be a subset of exportReadyIds"
        }
    }

    fun isExportReady(effectId: String): Boolean = effectId in exportReadyIds

    fun isReleased(effectId: String): Boolean = effectId in releasedIds
}
