package com.clipforge.ai.presentation.animation

import com.clipforge.ai.core.animation.AnimationPresetIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationPickerModelsTest {

    @Test
    fun `picker title is Animation and categories match approved order`() {
        val state = buildAnimationPickerState(hasAnimation = false)

        assertEquals("Animation", state.title)
        assertEquals(
            listOf("In", "Out", "Combo", "Loop"),
            state.categories.map { it.category.title }
        )
    }

    @Test
    fun `picker exposes C8_5 presets by category`() {
        val state = buildAnimationPickerState(hasAnimation = true)

        assertTrue(state.hasAnimation)
        assertEquals(
            listOf(
                AnimationPresetIds.FADE_IN,
                AnimationPresetIds.ZOOM_IN,
                AnimationPresetIds.SLIDE_IN_LEFT
            ),
            state.categories.single { it.category == AnimationPickerCategory.IN }.presets.map { it.presetId }
        )
        assertEquals(
            listOf(
                AnimationPresetIds.FADE_OUT,
                AnimationPresetIds.ZOOM_OUT,
                AnimationPresetIds.SLIDE_OUT_RIGHT
            ),
            state.categories.single { it.category == AnimationPickerCategory.OUT }.presets.map { it.presetId }
        )
        assertEquals(
            listOf(AnimationPresetIds.SLOW_ZOOM),
            state.categories.single { it.category == AnimationPickerCategory.COMBO }.presets.map { it.presetId }
        )
        assertEquals(
            listOf(AnimationPresetIds.PULSE, AnimationPresetIds.SWAY),
            state.categories.single { it.category == AnimationPickerCategory.LOOP }.presets.map { it.presetId }
        )
    }

    @Test
    fun `remove is hidden when project has no animation`() {
        val state = buildAnimationPickerState(hasAnimation = false)

        assertFalse(state.hasAnimation)
    }
}
