package com.clipforge.ai.core.animation

import com.clipforge.ai.core.effects.KeyframeEasing
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
                AnimationPresetIds.SLIDE_IN_RIGHT,
                AnimationPresetIds.SLIDE_IN_UP,
                AnimationPresetIds.SLIDE_IN_DOWN,
                AnimationPresetIds.ROTATE_IN,
                AnimationPresetIds.POP_IN,
                AnimationPresetIds.BOUNCE_IN,
                AnimationPresetIds.FADE_OUT,
                AnimationPresetIds.ZOOM_OUT,
                AnimationPresetIds.SLIDE_OUT_RIGHT,
                AnimationPresetIds.SLIDE_OUT_LEFT,
                AnimationPresetIds.SLIDE_OUT_UP,
                AnimationPresetIds.SLIDE_OUT_DOWN,
                AnimationPresetIds.ROTATE_OUT,
                AnimationPresetIds.POP_OUT,
                AnimationPresetIds.BOUNCE_OUT,
                AnimationPresetIds.SLOW_ZOOM,
                AnimationPresetIds.PULSE,
                AnimationPresetIds.SWAY,
                AnimationPresetIds.FLOAT,
                AnimationPresetIds.BOB,
                AnimationPresetIds.WOBBLE,
                AnimationPresetIds.HEARTBEAT,
                AnimationPresetIds.BREATHE,
                AnimationPresetIds.SHAKE_LIGHT,
                AnimationPresetIds.DRIFT_ZOOM
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
                AnimationPresetIds.SLIDE_IN_LEFT,
                AnimationPresetIds.SLIDE_IN_RIGHT,
                AnimationPresetIds.SLIDE_IN_UP,
                AnimationPresetIds.SLIDE_IN_DOWN,
                AnimationPresetIds.ROTATE_IN,
                AnimationPresetIds.POP_IN,
                AnimationPresetIds.BOUNCE_IN
            ),
            AnimationPresets.byType(AnimationPresetType.IN).map { it.id }
        )
        assertEquals(
            listOf(
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
            AnimationPresets.byType(AnimationPresetType.OUT).map { it.id }
        )
        assertEquals(
            listOf(AnimationPresetIds.SLOW_ZOOM, AnimationPresetIds.DRIFT_ZOOM),
            AnimationPresets.byType(AnimationPresetType.COMBO).map { it.id }
        )
        assertEquals(
            listOf(
                AnimationPresetIds.PULSE,
                AnimationPresetIds.SWAY,
                AnimationPresetIds.FLOAT,
                AnimationPresetIds.BOB,
                AnimationPresetIds.WOBBLE,
                AnimationPresetIds.HEARTBEAT,
                AnimationPresetIds.BREATHE,
                AnimationPresetIds.SHAKE_LIGHT
            ),
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

    // --- Phase 1 Basic In/Out preset additions -------------------------------------------------

    private val newInPresetIds = listOf(
        AnimationPresetIds.SLIDE_IN_RIGHT,
        AnimationPresetIds.SLIDE_IN_UP,
        AnimationPresetIds.SLIDE_IN_DOWN,
        AnimationPresetIds.ROTATE_IN,
        AnimationPresetIds.POP_IN,
        AnimationPresetIds.BOUNCE_IN
    )

    private val newOutPresetIds = listOf(
        AnimationPresetIds.SLIDE_OUT_LEFT,
        AnimationPresetIds.SLIDE_OUT_UP,
        AnimationPresetIds.SLIDE_OUT_DOWN,
        AnimationPresetIds.ROTATE_OUT,
        AnimationPresetIds.POP_OUT,
        AnimationPresetIds.BOUNCE_OUT
    )

    @Test
    fun `each new preset exists exactly once and resolves by id`() {
        val ids = AnimationPresets.all.map { it.id }
        (newInPresetIds + newOutPresetIds).forEach { id ->
            assertEquals("preset '$id' must appear exactly once", 1, ids.count { it == id })
            assertNotNull("preset '$id' must resolve via get()", AnimationPresets.get(id))
        }
    }

    @Test
    fun `each new preset has at least one property with valid keyframes`() {
        (newInPresetIds + newOutPresetIds).forEach { id ->
            val preset = AnimationPresets.get(id)!!
            assertTrue("preset '$id' must have >=1 property", preset.properties.isNotEmpty())
            preset.properties.forEach { property ->
                assertTrue("'$id'.${property.key} keyframes must be non-empty", property.keyframes.isNotEmpty())
                assertTrue(
                    "'$id'.${property.key} keyframes must be strictly increasing",
                    AnimationValidation.validateProperty(property).isValid
                )
            }
            // Whole-track validity (mirrors the render-path contract) for the default target.
            assertTrue(AnimationValidation.validateTrack(preset.toTrack(AnimationTargetType.CLIP)).isValid)
        }
    }

    @Test
    fun `new in presets are typed IN and new out presets are typed OUT`() {
        newInPresetIds.forEach { id ->
            assertEquals("'$id' must be IN", AnimationPresetType.IN, AnimationPresets.get(id)!!.presetType)
        }
        newOutPresetIds.forEach { id ->
            assertEquals("'$id' must be OUT", AnimationPresetType.OUT, AnimationPresets.get(id)!!.presetType)
        }
    }

    @Test
    fun `pop in uses BACK_OUT and bounce in uses BOUNCE_OUT on scale`() {
        assertEquals(KeyframeEasing.BACK_OUT, firstEasing(AnimationPresetIds.POP_IN, AnimationPropertyKeys.SCALE_X))
        assertEquals(KeyframeEasing.BACK_OUT, firstEasing(AnimationPresetIds.POP_IN, AnimationPropertyKeys.SCALE_Y))
        assertEquals(KeyframeEasing.BOUNCE_OUT, firstEasing(AnimationPresetIds.BOUNCE_IN, AnimationPropertyKeys.SCALE_X))
        assertEquals(KeyframeEasing.BOUNCE_OUT, firstEasing(AnimationPresetIds.BOUNCE_IN, AnimationPropertyKeys.SCALE_Y))
    }

    @Test
    fun `pop out and bounce out fall back to CUBIC_IN on scale`() {
        // NOTE: KeyframeEasing has no BACK_IN / BOUNCE_IN (overshoot curves are OUT-only),
        // so the symmetric "in" easing for Pop Out / Bounce Out is unavailable. These presets
        // deliberately fall back to CUBIC_IN on their scale tracks.
        assertEquals(KeyframeEasing.CUBIC_IN, firstEasing(AnimationPresetIds.POP_OUT, AnimationPropertyKeys.SCALE_X))
        assertEquals(KeyframeEasing.CUBIC_IN, firstEasing(AnimationPresetIds.POP_OUT, AnimationPropertyKeys.SCALE_Y))
        assertEquals(KeyframeEasing.CUBIC_IN, firstEasing(AnimationPresetIds.BOUNCE_OUT, AnimationPropertyKeys.SCALE_X))
        assertEquals(KeyframeEasing.CUBIC_IN, firstEasing(AnimationPresetIds.BOUNCE_OUT, AnimationPropertyKeys.SCALE_Y))
    }

    @Test
    fun `new preset endpoints match contract`() {
        assertTrack(AnimationPresetIds.SLIDE_IN_RIGHT, AnimationPropertyKeys.POSITION_X, listOf(2f, identity(AnimationPropertyKeys.POSITION_X)))
        assertTrack(AnimationPresetIds.SLIDE_IN_UP, AnimationPropertyKeys.POSITION_Y, listOf(-2f, identity(AnimationPropertyKeys.POSITION_Y)))
        assertTrack(AnimationPresetIds.SLIDE_IN_DOWN, AnimationPropertyKeys.POSITION_Y, listOf(2f, identity(AnimationPropertyKeys.POSITION_Y)))
        assertTrack(AnimationPresetIds.ROTATE_IN, AnimationPropertyKeys.ROTATION, listOf(-180f, identity(AnimationPropertyKeys.ROTATION)))
        assertTrack(AnimationPresetIds.POP_IN, AnimationPropertyKeys.SCALE_X, listOf(0f, identity(AnimationPropertyKeys.SCALE_X)))
        assertTrack(AnimationPresetIds.BOUNCE_IN, AnimationPropertyKeys.SCALE_X, listOf(0.3f, identity(AnimationPropertyKeys.SCALE_X)))
        assertTrack(AnimationPresetIds.SLIDE_OUT_LEFT, AnimationPropertyKeys.POSITION_X, listOf(identity(AnimationPropertyKeys.POSITION_X), -2f))
        assertTrack(AnimationPresetIds.SLIDE_OUT_UP, AnimationPropertyKeys.POSITION_Y, listOf(identity(AnimationPropertyKeys.POSITION_Y), -2f))
        assertTrack(AnimationPresetIds.SLIDE_OUT_DOWN, AnimationPropertyKeys.POSITION_Y, listOf(identity(AnimationPropertyKeys.POSITION_Y), 2f))
        assertTrack(AnimationPresetIds.ROTATE_OUT, AnimationPropertyKeys.ROTATION, listOf(identity(AnimationPropertyKeys.ROTATION), 180f))
        assertTrack(AnimationPresetIds.POP_OUT, AnimationPropertyKeys.SCALE_X, listOf(identity(AnimationPropertyKeys.SCALE_X), 0f))
        assertTrack(AnimationPresetIds.BOUNCE_OUT, AnimationPropertyKeys.SCALE_X, listOf(identity(AnimationPropertyKeys.SCALE_X), 0.3f))
    }

    // --- Phase 2 Combo & Loop preset additions ------------------------------------------------

    private val newLoopPresetIds = listOf(
        AnimationPresetIds.FLOAT,
        AnimationPresetIds.BOB,
        AnimationPresetIds.WOBBLE,
        AnimationPresetIds.HEARTBEAT,
        AnimationPresetIds.BREATHE,
        AnimationPresetIds.SHAKE_LIGHT
    )

    private val newComboPresetIds = listOf(AnimationPresetIds.DRIFT_ZOOM)

    @Test
    fun `each new loop and combo preset exists exactly once and resolves by id`() {
        val ids = AnimationPresets.all.map { it.id }
        (newLoopPresetIds + newComboPresetIds).forEach { id ->
            assertEquals("preset '$id' must appear exactly once", 1, ids.count { it == id })
            assertNotNull("preset '$id' must resolve via get()", AnimationPresets.get(id))
        }
    }

    @Test
    fun `each new loop and combo preset has valid strictly-increasing keyframes`() {
        (newLoopPresetIds + newComboPresetIds).forEach { id ->
            val preset = AnimationPresets.get(id)!!
            assertTrue("preset '$id' must have >=1 property", preset.properties.isNotEmpty())
            preset.properties.forEach { property ->
                assertTrue("'$id'.${property.key} keyframes must be non-empty", property.keyframes.isNotEmpty())
                assertTrue(
                    "'$id'.${property.key} keyframes must be strictly increasing",
                    AnimationValidation.validateProperty(property).isValid
                )
            }
            assertTrue(AnimationValidation.validateTrack(preset.toTrack(AnimationTargetType.CLIP)).isValid)
        }
    }

    @Test
    fun `new loop presets are typed LOOP and new combo presets are typed COMBO`() {
        newLoopPresetIds.forEach { id ->
            assertEquals("'$id' must be LOOP", AnimationPresetType.LOOP, AnimationPresets.get(id)!!.presetType)
        }
        newComboPresetIds.forEach { id ->
            assertEquals("'$id' must be COMBO", AnimationPresetType.COMBO, AnimationPresets.get(id)!!.presetType)
        }
    }

    @Test
    fun `new loop tracks start and end at the same value so they tile cleanly`() {
        newLoopPresetIds.forEach { id ->
            AnimationPresets.get(id)!!.properties.forEach { property ->
                val first = property.keyframes.first().value
                val last = property.keyframes.last().value
                assertEquals("'$id'.${property.key} must start == end to tile cleanly", first, last)
            }
        }
    }

    @Test
    fun `new loop tracks start at identity except wobble which tiles SWAY-style at its rest value`() {
        // All new loops begin at the property identity (no first-frame jump) EXCEPT Wobble's
        // rotation, which mirrors the existing SWAY pattern: it tiles at a non-identity rest
        // value (-2 -> +2 -> -2). Tiling correctness is covered by the start==end test above.
        newLoopPresetIds.forEach { id ->
            AnimationPresets.get(id)!!.properties.forEach { property ->
                val first = property.keyframes.first().value
                if (id == AnimationPresetIds.WOBBLE && property.key == AnimationPropertyKeys.ROTATION) {
                    assertEquals(-2f, first)
                } else {
                    assertEquals("'$id'.${property.key} must start at identity", identity(property.key), first)
                }
            }
        }
    }

    private fun firstEasing(presetId: String, key: String): KeyframeEasing =
        AnimationPresets.get(presetId)!!.properties.single { it.key == key }.keyframes.first().easing

    private fun assertTrack(presetId: String, key: String, values: List<Float>) {
        val property = AnimationPresets.get(presetId)!!.properties.single { it.key == key }
        assertEquals(values, property.keyframes.map { it.value })
    }

    private fun identity(key: String): Float =
        AnimationPropertyKeys.defaultValue(key)!!
}
