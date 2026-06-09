package com.clipforge.ai.core.transition

import androidx.media3.common.util.UnstableApi

/**
 * One transition's full wiring: metadata + export strategy + (optional) preview strategy.
 * A non-exportable descriptor may omit [renderer] and rely on [PreviewRenderer.PlainCut].
 */
@UnstableApi
data class TransitionRegistration(
    val descriptor: TransitionDescriptor,
    val renderer: TransitionRenderer?,
    val previewRenderer: PreviewRenderer
)

/**
 * Single registration + lookup point for the transition engine — the runtime replacement
 * for the scattered `when (op)` switches in CrossfadeRenderPlan / CrossfadeExecutor.
 *
 * Adding a transition is a single `register(...)` call; the executor (after the Phase D
 * flip) dispatches via [get]. Insertion order is preserved so the panel can list families
 * deterministically.
 *
 * Thread-safety: registration is expected at app start (single-threaded), but the map is
 * synchronized so lookups from render/preview threads are safe.
 *
 * Phase A: the registry is intentionally EMPTY. No transitions are registered yet and
 * nothing reads from it — existing behavior is fully unchanged. Population happens in
 * Phase B behind a dual-run parity gate.
 */
@UnstableApi
object TransitionRegistry {

    private val registrations = LinkedHashMap<String, TransitionRegistration>()
    private val lock = Any()

    /** Register (or replace) a transition. Returns the registration for chaining/tests. */
    fun register(registration: TransitionRegistration): TransitionRegistration {
        synchronized(lock) {
            registrations[registration.descriptor.id.value] = registration
        }
        return registration
    }

    /** Lookup by id, or null if not registered. */
    fun get(id: TransitionId): TransitionRegistration? =
        synchronized(lock) { registrations[id.value] }

    fun contains(id: TransitionId): Boolean =
        synchronized(lock) { registrations.containsKey(id.value) }

    /** All registrations in insertion order. */
    fun all(): List<TransitionRegistration> =
        synchronized(lock) { registrations.values.toList() }

    /** Registrations for one panel category, in insertion order. */
    fun byCategory(category: TransitionCategory): List<TransitionRegistration> =
        synchronized(lock) {
            registrations.values.filter { it.descriptor.category == category }
        }

    /** Test/relaunch hook — clears all registrations. */
    fun clear() {
        synchronized(lock) { registrations.clear() }
    }

    val size: Int get() = synchronized(lock) { registrations.size }
}
