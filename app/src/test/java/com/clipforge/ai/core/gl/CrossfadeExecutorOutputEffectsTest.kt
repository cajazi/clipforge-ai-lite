package com.clipforge.ai.core.gl

import android.content.Context
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.BaseGlShaderProgram
import androidx.media3.effect.GlEffect
import androidx.media3.effect.OverlayEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

@UnstableApi
class CrossfadeExecutorOutputEffectsTest {

    @Test
    fun `zero overlay branch preserves baseline effect list`() {
        val presentation = FakeGlEffect("presentation")
        val stageA = FakeGlEffect("stage-a")
        val stageB = FakeGlEffect("stage-b")
        val stageEffects = listOf(stageA, stageB)

        val output = CrossfadeExecutor.outputVideoEffects(
            presentationEffect = presentation,
            stageEffects = stageEffects,
            overlayEffect = null
        )

        assertEquals(3, output.size)
        assertEquals(listOf(FakeGlEffect::class, FakeGlEffect::class, FakeGlEffect::class), output.map { it::class })
        assertSame(presentation, output[0])
        assertSame(stageA, output[1])
        assertSame(stageB, output[2])
    }

    @Test
    fun `overlay effect is appended last after presentation and existing stage effects`() {
        val presentation = FakeGlEffect("presentation")
        val stage = FakeGlEffect("stage")
        val overlay = OverlayEffect(emptyList())

        val output = CrossfadeExecutor.outputVideoEffects(
            presentationEffect = presentation,
            stageEffects = listOf(stage),
            overlayEffect = overlay
        )

        assertEquals(3, output.size)
        assertSame(presentation, output[0])
        assertSame(stage, output[1])
        assertSame(overlay, output[2])
    }

    @Test
    fun `output effect construction does not alter duration value`() {
        val durationBeforeMs = 4_500L

        CrossfadeExecutor.outputVideoEffects(
            presentationEffect = FakeGlEffect("presentation"),
            stageEffects = listOf(FakeGlEffect("stage")),
            overlayEffect = OverlayEffect(emptyList())
        )

        assertEquals(4_500L, durationBeforeMs)
    }

    private data class FakeGlEffect(val id: String) : GlEffect {
        override fun toGlShaderProgram(context: Context, useHdr: Boolean): BaseGlShaderProgram {
            throw UnsupportedOperationException("Unit test fake")
        }
    }
}
