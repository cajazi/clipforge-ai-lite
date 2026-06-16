package com.clipforge.ai.core.animation

import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.domain.model.EffectItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipAnimationRewriterTest {
    @Test
    fun `clip delete removes matching animation rows`() {
        val remaining = ClipAnimationRewriter.deleteClipAnimations(
            effects = listOf(animation("clip-1", AnimationRole.IN), animation("clip-2", AnimationRole.OUT)),
            clipId = "clip-1"
        )

        assertEquals(listOf(AnimationEffectId.of("clip-2", AnimationRole.OUT)), remaining.map { it.id })
    }

    @Test
    fun `duplicate re-ids animations and resolves copied windows`() {
        val rewritten = ClipAnimationRewriter.duplicateClipAnimations(
            effects = listOf(animation("clip-1", AnimationRole.IN, 0L, 700L)),
            sourceClipId = "clip-1",
            newClipId = "clip-2",
            windows = listOf(
                ClipAnimationRewriter.ClipWindow("clip-1", 0L, 1_000L),
                ClipAnimationRewriter.ClipWindow("clip-2", 1_000L, 2_000L)
            )
        )

        val copied = rewritten.single { it.id == AnimationEffectId.of("clip-2", AnimationRole.IN) }
        assertEquals(1_000L, copied.startMs)
        assertEquals(1_700L, copied.endMs)
    }

    @Test
    fun `split drops original clip animations for v1`() {
        val deleted = ClipAnimationRewriter.deleteClipAnimations(
            effects = listOf(animation("clip-1", AnimationRole.IN), animation("clip-1", AnimationRole.OUT)),
            clipId = "clip-1"
        )
        val rewritten = ClipAnimationRewriter.resolveWindows(
            effects = deleted,
            windows = listOf(
                ClipAnimationRewriter.ClipWindow("clip-1", 0L, 500L),
                ClipAnimationRewriter.ClipWindow("clip-2", 500L, 1_000L)
            )
        )

        assertFalse(rewritten.any { AnimationEffectId.parse(it.id)?.clipId == "clip-1" })
    }

    @Test
    fun `missing clip window removes orphan animation rows`() {
        val rewritten = ClipAnimationRewriter.resolveWindows(
            effects = listOf(animation("clip-1", AnimationRole.COMBO), animation("orphan", AnimationRole.IN)),
            windows = listOf(ClipAnimationRewriter.ClipWindow("clip-1", 0L, 1_000L))
        )

        assertTrue(rewritten.any { it.id == AnimationEffectId.of("clip-1", AnimationRole.COMBO) })
        assertFalse(rewritten.any { it.id == AnimationEffectId.of("orphan", AnimationRole.IN) })
    }

    private fun animation(
        clipId: String,
        role: AnimationRole,
        startMs: Long = 0L,
        endMs: Long = 500L
    ) = EffectItem(
        id = AnimationEffectId.of(clipId, role),
        projectId = "project",
        effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
        scope = EffectScope.CLIP,
        startMs = startMs,
        endMs = endMs,
        zOrder = 0,
        params = emptyMap()
    )
}
