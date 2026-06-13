package com.clipforge.ai.presentation.effects

import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.selection.SelectionController
import com.clipforge.ai.domain.selection.SelectionTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EffectActionBarModelTest {
    @Test
    fun `effect action bar is hidden for none selection`() {
        val state = buildEffectActionBarState(
            effects = listOf(effect("effect-1")),
            selectionTarget = SelectionTarget.None
        )

        assertFalse(state.visible)
        assertNull(state.selectedEffectId)
        assertEquals(emptyList<EffectAction>(), state.actions)
    }

    @Test
    fun `effect action bar is hidden for clip selection`() {
        val state = buildEffectActionBarState(
            effects = listOf(effect("effect-1")),
            selectionTarget = SelectionTarget.Clip("clip-1")
        )

        assertFalse(state.visible)
    }

    @Test
    fun `effect action bar is visible for selected persisted effect`() {
        val state = buildEffectActionBarState(
            effects = listOf(effect("effect-1", effectId = "vhs")),
            selectionTarget = SelectionTarget.Effect("effect-1")
        )

        assertTrue(state.visible)
        assertEquals("effect-1", state.selectedEffectId)
        assertEquals("Vhs", state.label)
        assertEquals(listOf(EffectAction.Delete), state.actions)
    }

    @Test
    fun `effect action bar is hidden when selected effect no longer exists`() {
        val state = buildEffectActionBarState(
            effects = listOf(effect("effect-1")),
            selectionTarget = SelectionTarget.Effect("missing")
        )

        assertFalse(state.visible)
    }

    @Test
    fun `effect and clip action visibility remains mutually exclusive`() {
        val effects = listOf(effect("effect-1"))
        val controller = SelectionController()

        controller.selectEffect("effect-1")
        assertTrue(buildEffectActionBarState(effects, controller.current).visible)

        controller.selectClip("clip-1")
        assertEquals(SelectionTarget.Clip("clip-1"), controller.current)
        assertFalse(buildEffectActionBarState(effects, controller.current).visible)

        controller.selectEffect("effect-1")
        assertEquals(SelectionTarget.Effect("effect-1"), controller.current)
        assertTrue(buildEffectActionBarState(effects, controller.current).visible)
    }

    @Test
    fun `delete selected effect invokes delete and clears selection`() = runBlocking {
        val controller = SelectionController(SelectionTarget.Effect("effect-1"))
        val deleted = mutableListOf<String>()

        val handled = deleteSelectedEffect(controller) { effectId ->
            deleted += effectId
        }

        assertTrue(handled)
        assertEquals(listOf("effect-1"), deleted)
        assertEquals(SelectionTarget.None, controller.current)
    }

    @Test
    fun `delete selected effect is ignored for clip selection`() = runBlocking {
        val controller = SelectionController(SelectionTarget.Clip("clip-1"))
        val deleted = mutableListOf<String>()

        val handled = deleteSelectedEffect(controller) { effectId ->
            deleted += effectId
        }

        assertFalse(handled)
        assertEquals(emptyList<String>(), deleted)
        assertEquals(SelectionTarget.Clip("clip-1"), controller.current)
    }

    private fun effect(
        id: String,
        effectId: String = "blur"
    ) = EffectItem(
        id = id,
        projectId = "project",
        effectId = effectId,
        scope = EffectScope.GLOBAL,
        startMs = 0L,
        endMs = 1_000L,
        zOrder = 0,
        params = emptyMap()
    )
}
