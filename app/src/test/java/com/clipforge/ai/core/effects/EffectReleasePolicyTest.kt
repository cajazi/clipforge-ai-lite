package com.clipforge.ai.core.effects

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectReleasePolicyTest {

    @Test
    fun `accepts empty sets`() {
        val policy = EffectReleasePolicy()

        assertTrue(policy.exportReadyIds.isEmpty())
        assertTrue(policy.releasedIds.isEmpty())
        assertFalse(policy.isExportReady("brightness"))
        assertFalse(policy.isReleased("brightness"))
    }

    @Test
    fun `accepts released ids subset of export ready ids`() {
        val policy = EffectReleasePolicy(
            exportReadyIds = setOf("brightness", "contrast"),
            releasedIds = setOf("brightness")
        )

        assertTrue(policy.isExportReady("brightness"))
        assertTrue(policy.isExportReady("contrast"))
        assertTrue(policy.isReleased("brightness"))
        assertFalse(policy.isReleased("contrast"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun `rejects released ids outside export ready ids`() {
        EffectReleasePolicy(
            exportReadyIds = setOf("brightness"),
            releasedIds = setOf("contrast")
        )
    }
}
