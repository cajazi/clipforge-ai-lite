package com.clipforge.ai.core.animation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AnimationPresetsTest {

    @Test
    fun `preset ids are unique and stable`() {
        val ids = AnimationPresets.all.map { it.id }

        assertEquals(ids.toSet().size, ids.size)
        assertEquals(
            listOf(
                AnimationPresetIds.FADE_IN,
                AnimationPresetIds.ZOOM_IN,
                AnimationPresetIds.SLIDE_IN_LEFT,
                AnimationPresetIds.FADE_OUT,
                AnimationPresetIds.ZOOM_OUT,
                AnimationPresetIds.SLIDE_OUT_RIGHT,
                AnimationPresetIds.SLOW_ZOOM,
                AnimationPresetIds.PULSE,
                AnimationPresetIds.SWAY
            ),
            ids
        )
    }

    @Test
    fun `all presets produce valid tracks for supported targets`() {
        AnimationPresets.all.forEach { preset ->
            assertTrue(preset.supportedTargets.isNotEmpty())
            preset.supportedTargets.forEach { target ->
                val track = preset.toTrack(target)
                assertEquals(target, track.targetType)
                assertEquals(preset.presetType, track.presetType)
                assertTrue(AnimationValidation.validateTrack(track).isValid)
            }
        }
    }

    @Test
    fun `lookup by id returns preset and unknown id returns null`() {
        assertNotNull(AnimationPresets.get(AnimationPresetIds.ZOOM_IN))
        assertNull(AnimationPresets.get("missing"))
    }

    @Test
    fun `type filtering returns only requested preset type`() {
        val inPresets = AnimationPresets.byType(AnimationPresetType.IN)

        assertTrue(inPresets.isNotEmpty())
        assertTrue(inPresets.all { it.presetType == AnimationPresetType.IN })
        assertTrue(inPresets.any { it.id == AnimationPresetIds.FADE_IN })
    }

    @Test
    fun `approved preset list is grouped by type`() {
        assertEquals(
            listOf(
                AnimationPresetIds.FADE_IN,
                AnimationPresetIds.ZOOM_IN,
                AnimationPresetIds.SLIDE_IN_LEFT
            ),
            AnimationPresets.byType(AnimationPresetType.IN).map { it.id }
        )
        assertEquals(
            listOf(
                AnimationPresetIds.FADE_OUT,
                AnimationPresetIds.ZOOM_OUT,
                AnimationPresetIds.SLIDE_OUT_RIGHT
            ),
            AnimationPresets.byType(AnimationPresetType.OUT).map { it.id }
        )
        assertEquals(
            listOf(AnimationPresetIds.SLOW_ZOOM),
            AnimationPresets.byType(AnimationPresetType.COMBO).map { it.id }
        )
        assertEquals(
            listOf(AnimationPresetIds.PULSE, AnimationPresetIds.SWAY),
            AnimationPresets.byType(AnimationPresetType.LOOP).map { it.id }
        )
    }

    @Test
    fun `approved preset keyframes match contract`() {
        assertTrack(AnimationPresetIds.FADE_IN, AnimationPropertyKeys.OPACITY, listOf(0f, identity(AnimationPropertyKeys.OPACITY)))
        assertTrack(AnimationPresetIds.ZOOM_IN, AnimationPropertyKeys.SCALE_X, listOf(0.8f, identity(AnimationPropertyKeys.SCALE_X)))
        assertTrack(AnimationPresetIds.ZOOM_IN, AnimationPropertyKeys.SCALE_Y, listOf(0.8f, identity(AnimationPropertyKeys.SCALE_Y)))
        assertTrack(AnimationPresetIds.ZOOM_IN, AnimationPropertyKeys.OPACITY, listOf(0f, identity(AnimationPropertyKeys.OPACITY)))
        assertTrack(AnimationPresetIds.SLIDE_IN_LEFT, AnimationPropertyKeys.POSITION_X, listOf(-2f, identity(AnimationPropertyKeys.POSITION_X)))
        assertTrack(AnimationPresetIds.FADE_OUT, AnimationPropertyKeys.OPACITY, listOf(identity(AnimationPropertyKeys.OPACITY), 0f))
        assertTrack(AnimationPresetIds.ZOOM_OUT, AnimationPropertyKeys.SCALE_X, listOf(identity(AnimationPropertyKeys.SCALE_X), 0.8f))
        assertTrack(AnimationPresetIds.ZOOM_OUT, AnimationPropertyKeys.SCALE_Y, listOf(identity(AnimationPropertyKeys.SCALE_Y), 0.8f))
        assertTrack(AnimationPresetIds.ZOOM_OUT, AnimationPropertyKeys.OPACITY, listOf(identity(AnimationPropertyKeys.OPACITY), 0f))
        assertTrack(AnimationPresetIds.SLIDE_OUT_RIGHT, AnimationPropertyKeys.POSITION_X, listOf(identity(AnimationPropertyKeys.POSITION_X), 2f))
        assertTrack(AnimationPresetIds.SLOW_ZOOM, AnimationPropertyKeys.SCALE_X, listOf(identity(AnimationPropertyKeys.SCALE_X), 1.12f))
        assertTrack(AnimationPresetIds.SLOW_ZOOM, AnimationPropertyKeys.SCALE_Y, listOf(identity(AnimationPropertyKeys.SCALE_Y), 1.12f))
        assertTrack(AnimationPresetIds.PULSE, AnimationPropertyKeys.SCALE_X, listOf(identity(AnimationPropertyKeys.SCALE_X), 1.06f, identity(AnimationPropertyKeys.SCALE_X)))
        assertTrack(AnimationPresetIds.PULSE, AnimationPropertyKeys.SCALE_Y, listOf(identity(AnimationPropertyKeys.SCALE_Y), 1.06f, identity(AnimationPropertyKeys.SCALE_Y)))
        assertTrack(AnimationPresetIds.SWAY, AnimationPropertyKeys.ROTATION, listOf(-3f, 3f, -3f))
    }

    @Test
    fun `target filtering returns supported presets`() {
        val textPresets = AnimationPresets.forTarget(AnimationTargetType.TEXT)

        assertEquals(AnimationPresets.all, textPresets)
        assertFalse(AnimationPresets.forTarget(AnimationTargetType.CLIP).isEmpty())
    }

    @Test
    fun `toTrack rejects unsupported target`() {
        val preset = AnimationPreset(
            id = "clip_only",
            displayName = "Clip Only",
            presetType = AnimationPresetType.IN,
            defaultDurationUs = 100_000L,
            supportedTargets = setOf(AnimationTargetType.CLIP),
            properties = listOf(
                AnimationProperty(
                    AnimationPropertyKeys.OPACITY,
                    listOf(
                        com.clipforge.ai.core.effects.Keyframe(0L, 0f),
                        com.clipforge.ai.core.effects.Keyframe(100_000L, 1f)
                    )
                )
            )
        )

        val thrown = runCatching { preset.toTrack(AnimationTargetType.TEXT) }.exceptionOrNull()
        assertTrue(thrown is IllegalArgumentException)
    }

    @Test
    fun `preset model rejects invalid property tracks`() {
        val thrown = runCatching {
            AnimationPreset(
                id = "bad",
                displayName = "Bad",
                presetType = AnimationPresetType.IN,
                defaultDurationUs = 100_000L,
                supportedTargets = setOf(AnimationTargetType.CLIP),
                properties = listOf(
                    AnimationProperty(
                        "unknown",
                        listOf(
                            com.clipforge.ai.core.effects.Keyframe(0L, 0f),
                            com.clipforge.ai.core.effects.Keyframe(100_000L, 1f)
                        )
                    )
                )
            )
        }.exceptionOrNull()

        assertTrue(thrown is IllegalArgumentException)
    }

    private fun assertTrack(presetId: String, key: String, values: List<Float>) {
        val property = AnimationPresets.get(presetId)!!.properties.single { it.key == key }
        assertEquals(values, property.keyframes.map { it.value })
    }

    private fun identity(key: String): Float =
        AnimationPropertyKeys.defaultValue(key)!!
}
