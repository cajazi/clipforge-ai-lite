package com.clipforge.ai.core.effects

/** One registered effect: its metadata plus the factory that renders it. */
data class EffectRegistration(
    val descriptor: EffectDescriptor,
    val factory: EffectFactory
)

/**
 * Registry of available effects, mirroring TransitionRegistry.
 *
 * Registration is idempotent for an identical registration (same id and equal
 * descriptor): re-registering is a no-op, so registerBuiltIns-style calls are safe to
 * repeat. Registering a DIFFERENT descriptor or factory under an existing id is a
 * programming error and throws.
 */
class EffectRegistry {

    private val registrations = LinkedHashMap<String, EffectRegistration>()

    @Synchronized
    fun register(registration: EffectRegistration) {
        val id = registration.descriptor.id
        val existing = registrations[id]
        if (existing == null) {
            registrations[id] = registration
            return
        }
        if (existing.descriptor == registration.descriptor && existing.factory === registration.factory) {
            return // idempotent re-registration
        }
        throw IllegalStateException(
            "Effect id '$id' is already registered with a different descriptor or factory"
        )
    }

    @Synchronized
    fun get(id: String): EffectRegistration? = registrations[id]

    @Synchronized
    fun all(): List<EffectRegistration> = registrations.values.toList()

    @Synchronized
    fun clear() = registrations.clear()
}
