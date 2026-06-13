package com.clipforge.ai.presentation.effects

import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.selection.SelectionController
import com.clipforge.ai.domain.selection.SelectionTarget
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectLaneUiModelTest {
    @Test
    fun `empty lane returns no chips`() {
        val chips = buildEffectChipUiModels(emptyList(), SelectionTarget.None)

        assertEquals(emptyList<EffectChipUiModel>(), chips)
    }

    @Test
    fun `populated lane is ordered by z order start and id`() {
        val chips = buildEffectChipUiModels(
            listOf(
                effect(id = "b", startMs = 400L, endMs = 800L, zOrder = 1),
                effect(id = "a", startMs = 200L, endMs = 500L, zOrder = 1),
                effect(id = "c", startMs = 100L, endMs = 300L, zOrder = 0)
            ),
            SelectionTarget.None
        )

        assertEquals(listOf("c", "a", "b"), chips.map { it.id })
        assertEquals(listOf(200L, 300L, 400L), chips.map { it.durationMs })
    }

    @Test
    fun `select effect marks one visual chip selected`() {
        val chips = buildEffectChipUiModels(
            listOf(
                effect(id = "effect-1", startMs = 0L, endMs = 300L),
                effect(id = "effect-2", startMs = 300L, endMs = 600L)
            ),
            SelectionTarget.Effect("effect-2")
        )

        assertFalse(chips.first { it.id == "effect-1" }.isSelected)
        assertTrue(chips.first { it.id == "effect-2" }.isSelected)
    }

    @Test
    fun `effect selection replaces clip selection in lane model`() {
        val controller = SelectionController()
        controller.selectClip("clip-1")
        controller.selectEffect("effect-1")

        val chips = buildEffectChipUiModels(
            listOf(effect(id = "effect-1", startMs = 0L, endMs = 300L)),
            controller.current
        )

        assertEquals(SelectionTarget.Effect("effect-1"), controller.current)
        assertTrue(chips.single().isSelected)
    }

    @Test
    fun `clip selection replaces effect selection in lane model`() {
        val controller = SelectionController()
        controller.selectEffect("effect-1")
        controller.selectClip("clip-1")

        val chips = buildEffectChipUiModels(
            listOf(effect(id = "effect-1", startMs = 0L, endMs = 300L)),
            controller.current
        )

        assertEquals(SelectionTarget.Clip("clip-1"), controller.current)
        assertFalse(chips.single().isSelected)
    }

    @Test
    fun `visual selected state is derived from selection target only`() {
        val effect = effect(id = "effect-1", effectId = "vhs", startMs = 20L, endMs = 120L, zOrder = 4)

        val unselected = buildEffectChipUiModels(listOf(effect), SelectionTarget.None).single()
        val selected = buildEffectChipUiModels(listOf(effect), SelectionTarget.Effect("effect-1")).single()

        assertFalse(unselected.isSelected)
        assertTrue(selected.isSelected)
        assertEquals("vhs", selected.effectId)
        assertEquals("Vhs", selected.label)
        assertEquals(20L, selected.startMs)
        assertEquals(120L, selected.endMs)
        assertEquals(4, selected.zOrder)
    }

    private fun effect(
        id: String,
        effectId: String = "blur",
        startMs: Long,
        endMs: Long,
        zOrder: Int = 0
    ) = EffectItem(
        id = id,
        projectId = "project",
        effectId = effectId,
        scope = EffectScope.GLOBAL,
        startMs = startMs,
        endMs = endMs,
        zOrder = zOrder,
        params = emptyMap()
    )
}
