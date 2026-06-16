package com.clipforge.ai.presentation.animation

import com.clipforge.ai.core.animation.AnimationEffectId
import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.Keyframe
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipAnimationModelsTest {
    @Test
    fun `selected clip drives animation UI state`() {
        val state = buildClipAnimationUiState(
            selectedClipId = "clip-2",
            selectedRole = AnimationRole.IN,
            effects = listOf(animation("clip-1", AnimationRole.IN), animation("clip-2", AnimationRole.OUT)),
            clipWindows = listOf(window("clip-1"), window("clip-2")),
            sessionSelectedPresetId = null,
            inFlightDurationMs = null
        )

        assertEquals("clip-2", state.selectedClipId)
        assertNull(state.inAnimation)
        assertEquals(500L, state.outAnimation?.effectiveDurationMs)
    }

    @Test
    fun `repo effects derive in out combo summaries`() {
        val state = buildClipAnimationUiState(
            selectedClipId = "clip-1",
            selectedRole = AnimationRole.COMBO,
            effects = listOf(
                animation("clip-1", AnimationRole.IN, 0L, 400L, requestedMs = 700L),
                animation("clip-1", AnimationRole.OUT, 600L, 1_000L, requestedMs = 800L),
                animation("clip-1", AnimationRole.COMBO, 0L, 1_000L, requestedMs = 1_500L)
            ),
            clipWindows = listOf(window("clip-1", endMs = 1_000L)),
            sessionSelectedPresetId = "slow_zoom",
            inFlightDurationMs = null
        )

        assertEquals(700L, state.inAnimation?.requestedDurationMs)
        assertEquals(800L, state.outAnimation?.requestedDurationMs)
        assertEquals(1_500L, state.comboAnimation?.requestedDurationMs)
        assertEquals(1_000L, state.comboAnimation?.effectiveDurationMs)
    }

    @Test
    fun `requested and effective duration display when clamped`() {
        val state = buildClipAnimationUiState(
            selectedClipId = "clip-1",
            selectedRole = AnimationRole.IN,
            effects = listOf(animation("clip-1", AnimationRole.IN, 0L, 500L, requestedMs = 1_000L)),
            clipWindows = listOf(window("clip-1", endMs = 500L)),
            sessionSelectedPresetId = null,
            inFlightDurationMs = null
        )

        assertEquals(1_000L, state.requestedDurationMs)
        assertEquals(500L, state.effectiveDurationMs)
    }

    @Test
    fun `timeline marker summary map derives from repo flow rows`() {
        val markers = buildClipAnimationMarkerMap(
            listOf(
                animation("clip-1", AnimationRole.IN),
                animation("clip-1", AnimationRole.OUT, 600L, 1_000L),
                animation("clip-2", AnimationRole.COMBO, 1_000L, 2_000L)
            )
        )

        assertEquals(500L, markers["clip-1"]?.inMarker?.effectiveDurationMs)
        assertEquals(400L, markers["clip-1"]?.outMarker?.effectiveDurationMs)
        assertEquals(1_000L, markers["clip-2"]?.comboMarker?.effectiveDurationMs)
    }

    @Test
    fun `marker state updates after apply remove and duration change inputs`() {
        val before = buildClipAnimationMarkerMap(listOf(animation("clip-1", AnimationRole.IN)))
        val afterDuration = buildClipAnimationMarkerMap(listOf(animation("clip-1", AnimationRole.IN, endMs = 800L)))
        val afterRemove = buildClipAnimationMarkerMap(emptyList())

        assertEquals(500L, before["clip-1"]?.inMarker?.effectiveDurationMs)
        assertEquals(800L, afterDuration["clip-1"]?.inMarker?.effectiveDurationMs)
        assertTrue(afterRemove.isEmpty())
    }

    private fun window(
        clipId: String,
        startMs: Long = 0L,
        endMs: Long = 2_000L
    ) = ClipAnimationWindowInput(clipId = clipId, startMs = startMs, endMs = endMs)

    private fun animation(
        clipId: String,
        role: AnimationRole,
        startMs: Long = 0L,
        endMs: Long = 500L,
        requestedMs: Long = endMs - startMs
    ) = EffectItem(
        id = AnimationEffectId.of(clipId, role),
        projectId = PROJECT_ID,
        effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
        scope = EffectScope.CLIP,
        startMs = startMs,
        endMs = endMs,
        zOrder = 0,
        params = mapOf(
            "opacity" to EffectParamValue.Keyframed(
                listOf(
                    Keyframe(0L, 0f),
                    Keyframe(requestedMs * 1_000L, 1f)
                )
            )
        )
    )

    private companion object {
        const val PROJECT_ID = "project"
    }
}
