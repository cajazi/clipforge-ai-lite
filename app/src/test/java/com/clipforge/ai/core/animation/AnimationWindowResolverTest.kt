package com.clipforge.ai.core.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AnimationWindowResolverTest {
    @Test
    fun `no-transition windows use clip bounds`() {
        assertEquals(
            AnimationWindowResolver.Window(1_000L, 1_500L),
            AnimationWindowResolver.resolve(1_000L, 3_000L, 500L, AnimationRole.IN)
        )
        assertEquals(
            AnimationWindowResolver.Window(2_500L, 3_000L),
            AnimationWindowResolver.resolve(1_000L, 3_000L, 500L, AnimationRole.OUT)
        )
        assertEquals(
            AnimationWindowResolver.Window(1_000L, 3_000L),
            AnimationWindowResolver.resolve(1_000L, 3_000L, 500L, AnimationRole.COMBO)
        )
    }

    @Test
    fun `incoming transition clamps in start`() {
        assertEquals(
            AnimationWindowResolver.Window(1_300L, 1_800L),
            AnimationWindowResolver.resolve(
                clipStartMs = 1_000L,
                clipEndMs = 3_000L,
                requestedDurationMs = 500L,
                role = AnimationRole.IN,
                incomingTransitionDurationMs = 300L
            )
        )
    }

    @Test
    fun `outgoing transition clamps out end`() {
        assertEquals(
            AnimationWindowResolver.Window(2_200L, 2_700L),
            AnimationWindowResolver.resolve(
                clipStartMs = 1_000L,
                clipEndMs = 3_000L,
                requestedDurationMs = 500L,
                role = AnimationRole.OUT,
                outgoingTransitionDurationMs = 300L
            )
        )
    }

    @Test
    fun `both-side transition clamp limits combo to clean window`() {
        assertEquals(
            AnimationWindowResolver.Window(1_250L, 2_600L),
            AnimationWindowResolver.resolve(
                clipStartMs = 1_000L,
                clipEndMs = 3_000L,
                requestedDurationMs = 500L,
                role = AnimationRole.COMBO,
                incomingTransitionDurationMs = 250L,
                outgoingTransitionDurationMs = 400L
            )
        )
    }

    @Test
    fun `short clip shrinks in and out proportionally without overlap`() {
        val windows = AnimationWindowResolver.resolveInOut(
            clipStartMs = 0L,
            clipEndMs = 1_000L,
            requestedInDurationMs = 800L,
            requestedOutDurationMs = 800L
        ).associate { it.role to it.window }

        assertEquals(AnimationWindowResolver.Window(0L, 500L), windows[AnimationRole.IN])
        assertEquals(AnimationWindowResolver.Window(500L, 1_000L), windows[AnimationRole.OUT])
    }

    @Test
    fun `below minimum usable duration drops window`() {
        assertNull(
            AnimationWindowResolver.resolve(
                clipStartMs = 0L,
                clipEndMs = 10L,
                requestedDurationMs = 10L,
                role = AnimationRole.COMBO,
                minimumUsableDurationMs = 11L
            )
        )
    }
}
