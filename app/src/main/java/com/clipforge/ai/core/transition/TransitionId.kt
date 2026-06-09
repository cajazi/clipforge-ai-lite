package com.clipforge.ai.core.transition

/**
 * Stable, framework-internal identifier for a transition.
 *
 * Intentionally decoupled from the persisted [com.clipforge.ai.domain.model.TransitionType]
 * enum: the DB enum is the storage contract, while [TransitionId] is the registry key the
 * new engine dispatches on. A mapping layer (added in a later phase) bridges the two so
 * persisted projects keep working while the engine evolves.
 *
 * Phase A: type only. No behavior, no registrations.
 */
@JvmInline
value class TransitionId(val value: String) {
    init {
        require(value.isNotBlank()) { "TransitionId must not be blank" }
    }

    override fun toString(): String = value
}
