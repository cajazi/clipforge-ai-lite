package com.clipforge.ai.core.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimationEffectIdTest {
    @Test
    fun `of creates deterministic ids`() {
        assertEquals("anim-clip-1-in", AnimationEffectId.of("clip-1", AnimationRole.IN))
        assertEquals("anim-clip-1-out", AnimationEffectId.of("clip-1", AnimationRole.OUT))
        assertEquals("anim-clip-1-combo", AnimationEffectId.of("clip-1", AnimationRole.COMBO))
    }

    @Test
    fun `parse returns clip id and role`() {
        assertEquals(
            AnimationEffectId.Parsed("clip-1", AnimationRole.IN),
            AnimationEffectId.parse("anim-clip-1-in")
        )
        assertEquals(
            AnimationEffectId.Parsed("clip-1", AnimationRole.OUT),
            AnimationEffectId.parse("anim-clip-1-out")
        )
        assertEquals(
            AnimationEffectId.Parsed("clip-1", AnimationRole.COMBO),
            AnimationEffectId.parse("anim-clip-1-combo")
        )
    }

    @Test
    fun `parse rejects unknown format`() {
        assertNull(AnimationEffectId.parse("clip-1-in"))
        assertNull(AnimationEffectId.parse("anim--in"))
        assertNull(AnimationEffectId.parse("anim-clip-1-bounce"))
    }
}
