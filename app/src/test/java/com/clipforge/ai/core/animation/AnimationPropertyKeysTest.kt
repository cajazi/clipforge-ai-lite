package com.clipforge.ai.core.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationPropertyKeysTest {

    @Test
    fun all_nine_keys_are_present() {
        val expected = setOf(
            "positionX", "positionY", "scale", "scaleX", "scaleY",
            "rotation", "opacity", "anchorX", "anchorY"
        )
        assertEquals(expected, AnimationPropertyKeys.keys().toSet())
    }

    @Test
    fun no_duplicate_keys() {
        val keys = AnimationPropertyKeys.keys()
        assertEquals("keys must be unique", keys.size, keys.toSet().size)
    }

    @Test
    fun defaults_are_identity_values() {
        assertEquals(0f, AnimationPropertyKeys.defaultValue("positionX"))
        assertEquals(0f, AnimationPropertyKeys.defaultValue("positionY"))
        assertEquals(1f, AnimationPropertyKeys.defaultValue("scale"))
        assertEquals(1f, AnimationPropertyKeys.defaultValue("scaleX"))
        assertEquals(1f, AnimationPropertyKeys.defaultValue("scaleY"))
        assertEquals(0f, AnimationPropertyKeys.defaultValue("rotation"))
        assertEquals(1f, AnimationPropertyKeys.defaultValue("opacity"))
        assertEquals(0.5f, AnimationPropertyKeys.defaultValue("anchorX"))
        assertEquals(0.5f, AnimationPropertyKeys.defaultValue("anchorY"))
    }

    @Test
    fun isKnown_accepts_valid_rejects_invalid() {
        assertTrue(AnimationPropertyKeys.isKnown("scale"))
        assertFalse(AnimationPropertyKeys.isKnown("brightness"))
        assertFalse(AnimationPropertyKeys.isKnown(""))
    }

    @Test
    fun defaultValue_null_for_unknown() {
        assertNull(AnimationPropertyKeys.defaultValue("nope"))
    }
}
