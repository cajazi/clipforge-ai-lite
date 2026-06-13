package com.clipforge.ai.domain.selection

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class SelectionControllerTest {
    @Test
    fun `select clip sets clip target only`() {
        val controller = SelectionController()

        controller.selectClip("clip-1")

        assertEquals(SelectionTarget.Clip("clip-1"), controller.current)
        assertEquals("clip-1", controller.current.clipId)
        assertNull(controller.current.effectId)
    }

    @Test
    fun `select effect sets effect target only`() {
        val controller = SelectionController()

        controller.selectEffect("effect-1")

        assertEquals(SelectionTarget.Effect("effect-1"), controller.current)
        assertEquals("effect-1", controller.current.effectId)
        assertNull(controller.current.clipId)
    }

    @Test
    fun `clip replaces effect`() {
        val controller = SelectionController()

        controller.selectEffect("effect-1")
        controller.selectClip("clip-1")

        assertEquals(SelectionTarget.Clip("clip-1"), controller.current)
        assertNull(controller.current.effectId)
    }

    @Test
    fun `effect replaces clip`() {
        val controller = SelectionController()

        controller.selectClip("clip-1")
        controller.selectEffect("effect-1")

        assertEquals(SelectionTarget.Effect("effect-1"), controller.current)
        assertNull(controller.current.clipId)
    }

    @Test
    fun `clear selection returns none`() {
        val controller = SelectionController()

        controller.selectClip("clip-1")
        controller.clear()

        assertEquals(SelectionTarget.None, controller.current)
        assertNull(controller.current.clipId)
        assertNull(controller.current.effectId)
    }

    @Test
    fun `selection target equality and hash behavior`() {
        val clipA = SelectionTarget.Clip("clip-1")
        val clipB = SelectionTarget.Clip("clip-1")
        val effect = SelectionTarget.Effect("clip-1")

        assertEquals(clipA, clipB)
        assertEquals(clipA.hashCode(), clipB.hashCode())
        assertNotEquals(clipA, effect)
        assertEquals(SelectionTarget.None, SelectionTarget.None)
    }

    @Test
    fun `selection snapshots round trip for future history compatibility`() {
        val targets = listOf(
            SelectionTarget.None,
            SelectionTarget.Clip("clip-1"),
            SelectionTarget.Effect("effect-1")
        )

        targets.forEach { target ->
            assertEquals(target, SelectionTarget.fromSnapshot(target.toSnapshot()))
        }
    }

    @Test
    fun `controller restores from snapshot`() {
        val controller = SelectionController(SelectionTarget.Clip("clip-1"))

        controller.restore(SelectionSnapshot(SelectionSnapshot.Type.EFFECT, "effect-1"))

        assertEquals(SelectionTarget.Effect("effect-1"), controller.current)
    }

    @Test
    fun `blank ids and invalid snapshots are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            SelectionTarget.Clip("")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SelectionTarget.Effect(" ")
        }
        assertThrows(IllegalArgumentException::class.java) {
            SelectionTarget.fromSnapshot(SelectionSnapshot(SelectionSnapshot.Type.CLIP, null))
        }
    }
}
