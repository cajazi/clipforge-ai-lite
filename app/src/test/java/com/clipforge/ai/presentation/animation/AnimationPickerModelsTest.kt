package com.clipforge.ai.presentation.animation

import com.clipforge.ai.core.animation.AnimationPresetIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationPickerModelsTest {

    @Test
    fun `picker title is Animation tabs and categories match approved order`() {
        val state = buildAnimationPickerState(hasAnimation = false)

        assertEquals("Animation", state.title)
        assertEquals(
            listOf("In", "Out", "Combo"),
            state.tabs.map { it.tab.title }
        )
        assertEquals(
            listOf("Trending", "Basic", "Light", "Glitch", "Mask", "3D", "Vibration"),
            state.categories.map { it.category.title }
        )
    }

    @Test
    fun `disabled categories are marked coming soon and expose no presets`() {
        val state = buildAnimationPickerState(hasAnimation = true)

        val disabledCategories = state.categories.filterNot { it.enabled }
        assertEquals(
            listOf(
                AnimationPickerCategory.TRENDING,
                AnimationPickerCategory.LIGHT,
                AnimationPickerCategory.GLITCH,
                AnimationPickerCategory.MASK,
                AnimationPickerCategory.THREE_D
            ),
            disabledCategories.map { it.category }
        )
        disabledCategories.forEach { category ->
            assertEquals("Coming soon", category.helperLabel)
            assertTrue(animationPickerPresetsFor(AnimationPickerTab.IN, category.category).isEmpty())
        }
    }

    @Test
    fun `basic exposes current transform presets with none first`() {
        val state = buildAnimationPickerState(hasAnimation = true)

        assertTrue(state.hasAnimation)
        assertEquals(
            listOf(
                null,
                AnimationPresetIds.FADE_IN,
                AnimationPresetIds.ZOOM_IN,
                AnimationPresetIds.SLIDE_IN_LEFT,
                AnimationPresetIds.SLIDE_IN_RIGHT,
                AnimationPresetIds.SLIDE_IN_UP,
                AnimationPresetIds.SLIDE_IN_DOWN,
                AnimationPresetIds.ROTATE_IN,
                AnimationPresetIds.POP_IN,
                AnimationPresetIds.BOUNCE_IN
            ),
            animationPickerPresetsFor(AnimationPickerTab.IN, AnimationPickerCategory.BASIC).map { it.presetId }
        )
        assertEquals(
            listOf(
                null,
                AnimationPresetIds.FADE_OUT,
                AnimationPresetIds.ZOOM_OUT,
                AnimationPresetIds.SLIDE_OUT_RIGHT,
                AnimationPresetIds.SLIDE_OUT_LEFT,
                AnimationPresetIds.SLIDE_OUT_UP,
                AnimationPresetIds.SLIDE_OUT_DOWN,
                AnimationPresetIds.ROTATE_OUT,
                AnimationPresetIds.POP_OUT,
                AnimationPresetIds.BOUNCE_OUT
            ),
            animationPickerPresetsFor(AnimationPickerTab.OUT, AnimationPickerCategory.BASIC).map { it.presetId }
        )
        assertEquals(
            listOf(null, AnimationPresetIds.SLOW_ZOOM, AnimationPresetIds.DRIFT_ZOOM),
            animationPickerPresetsFor(AnimationPickerTab.COMBO, AnimationPickerCategory.BASIC).map { it.presetId }
        )
    }

    @Test
    fun `vibration exposes transform expressible loop presets only under combo`() {
        assertEquals(
            listOf(null),
            animationPickerPresetsFor(AnimationPickerTab.IN, AnimationPickerCategory.VIBRATION).map { it.presetId }
        )
        assertEquals(
            listOf(
                null,
                AnimationPresetIds.PULSE,
                AnimationPresetIds.SWAY,
                AnimationPresetIds.FLOAT,
                AnimationPresetIds.BOB,
                AnimationPresetIds.WOBBLE,
                AnimationPresetIds.HEARTBEAT,
                AnimationPresetIds.BREATHE,
                AnimationPresetIds.SHAKE_LIGHT
            ),
            animationPickerPresetsFor(AnimationPickerTab.COMBO, AnimationPickerCategory.VIBRATION).map { it.presetId }
        )
    }

    @Test
    fun `remove is hidden when project has no animation`() {
        val state = buildAnimationPickerState(hasAnimation = false)

        assertFalse(state.hasAnimation)
    }
}
