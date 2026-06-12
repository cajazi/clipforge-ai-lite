package com.clipforge.ai.core.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Test

class EffectRegistryTest {

    private fun descriptor(id: String) = EffectDescriptor(
        id = id,
        displayName = id,
        category = EffectCategory.TRENDY,
        paramSpecs = listOf(ParamSpec("intensity", "Intensity", 0f, 1f, 0.5f))
    )

    private val factory = EffectFactory { _, _, _ -> throw NotImplementedError("test factory") }

    @Test
    fun `register and get round-trips`() {
        val registry = EffectRegistry()
        val registration = EffectRegistration(descriptor("vhs"), factory)
        registry.register(registration)
        assertSame(registration.factory, registry.get("vhs")?.factory)
        assertEquals(registration.descriptor, registry.get("vhs")?.descriptor)
    }

    @Test
    fun `identical re-registration is idempotent`() {
        val registry = EffectRegistry()
        val registration = EffectRegistration(descriptor("vhs"), factory)
        registry.register(registration)
        registry.register(registration) // must not throw
        registry.register(EffectRegistration(descriptor("vhs"), factory)) // equal descriptor, same factory
        assertEquals(1, registry.all().size)
    }

    @Test
    fun `conflicting duplicate id throws`() {
        val registry = EffectRegistry()
        registry.register(EffectRegistration(descriptor("vhs"), factory))
        val conflicting = EffectRegistration(
            descriptor("vhs").copy(displayName = "VHS Deluxe"),
            factory
        )
        assertThrows(IllegalStateException::class.java) { registry.register(conflicting) }
    }

    @Test
    fun `different factory under same id throws`() {
        val registry = EffectRegistry()
        registry.register(EffectRegistration(descriptor("vhs"), factory))
        val otherFactory = EffectFactory { _, _, _ -> throw NotImplementedError("other") }
        assertThrows(IllegalStateException::class.java) {
            registry.register(EffectRegistration(descriptor("vhs"), otherFactory))
        }
    }

    @Test
    fun `all returns registrations in registration order`() {
        val registry = EffectRegistry()
        registry.register(EffectRegistration(descriptor("vhs"), factory))
        registry.register(EffectRegistration(descriptor("pixelate"), factory))
        assertEquals(listOf("vhs", "pixelate"), registry.all().map { it.descriptor.id })
    }

    @Test
    fun `get unknown id returns null`() {
        assertNull(EffectRegistry().get("missing"))
    }

    @Test
    fun `descriptor rejects duplicate param keys`() {
        assertThrows(IllegalArgumentException::class.java) {
            EffectDescriptor(
                id = "broken",
                displayName = "Broken",
                category = EffectCategory.BLUR,
                paramSpecs = listOf(
                    ParamSpec("intensity", "A", 0f, 1f, 0.5f),
                    ParamSpec("intensity", "B", 0f, 1f, 0.5f)
                )
            )
        }
    }
}
