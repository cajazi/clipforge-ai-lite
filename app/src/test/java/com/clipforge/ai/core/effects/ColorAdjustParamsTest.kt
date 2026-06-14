package com.clipforge.ai.core.effects

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ColorAdjustParamsTest {

    @Test
    fun keysAreUnique() {
        val specs = colorAdjustParamSpecs()

        assertEquals(specs.size, specs.map { it.key }.toSet().size)
    }

    @Test
    fun labelsAreCorrect() {
        val specs = colorAdjustParamSpecs().associateBy { it.key }

        assertEquals("Brightness", specs.getValue(BRIGHTNESS).label)
        assertEquals("Contrast", specs.getValue(CONTRAST).label)
    }

    @Test
    fun minMaxAndDefaultValuesAreCorrect() {
        val specs = colorAdjustParamSpecs().associateBy { it.key }

        assertSpec(specs.getValue(BRIGHTNESS), min = 0f, max = 2f, default = 1f)
        assertSpec(specs.getValue(CONTRAST), min = 0f, max = 2f, default = 1f)
    }

    @Test
    fun defaultsAreIdentity() {
        val specs = colorAdjustParamSpecs()

        assertTrue(specs.all { it.default == 1f })
    }

    private fun assertSpec(spec: ParamSpec, min: Float, max: Float, default: Float) {
        assertEquals(min, spec.min, 0f)
        assertEquals(max, spec.max, 0f)
        assertEquals(default, spec.default, 0f)
    }
}
