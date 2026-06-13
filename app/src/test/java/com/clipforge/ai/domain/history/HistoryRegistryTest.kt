package com.clipforge.ai.domain.history

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HistoryRegistryTest {
    @Test
    fun `one stack preserves mixed command order`() = runBlocking {
        val target = mutableListOf<String>()
        val registry = HistoryRegistry()

        registry.execute(ListCommand(target, "clip"))
        registry.execute(ListCommand(target, "effect"))

        assertEquals(listOf("clip", "effect"), target)
        registry.undo()
        assertEquals(listOf("clip"), target)
        registry.undo()
        assertEquals(emptyList<String>(), target)
        registry.redo()
        assertEquals(listOf("clip"), target)
        registry.redo()
        assertEquals(listOf("clip", "effect"), target)
    }

    @Test
    fun `stack consistency updates can undo and redo`() = runBlocking {
        val target = mutableListOf<String>()
        val registry = HistoryRegistry()

        assertFalse(registry.state.value.canUndo)
        assertFalse(registry.state.value.canRedo)

        registry.execute(ListCommand(target, "one"))
        assertTrue(registry.state.value.canUndo)
        assertFalse(registry.state.value.canRedo)
        assertEquals(1, registry.state.value.undoCount)

        registry.undo()
        assertFalse(registry.state.value.canUndo)
        assertTrue(registry.state.value.canRedo)
        assertEquals(1, registry.state.value.redoCount)

        registry.redo()
        assertTrue(registry.state.value.canUndo)
        assertFalse(registry.state.value.canRedo)
    }

    @Test
    fun `coalesced gesture records one command at gesture end`() = runBlocking {
        val value = Box(0)
        val registry = HistoryRegistry()

        registry.executeCoalesced("slider", SetValueCommand(value, before = 0, after = 10), gestureEnded = false)
        registry.executeCoalesced("slider", SetValueCommand(value, before = 10, after = 20), gestureEnded = false)
        registry.executeCoalesced("slider", SetValueCommand(value, before = 20, after = 30), gestureEnded = true)

        assertEquals(30, value.value)
        assertEquals(1, registry.state.value.undoCount)

        registry.undo()
        assertEquals(0, value.value)

        registry.redo()
        assertEquals(30, value.value)
    }

    private class ListCommand(
        private val target: MutableList<String>,
        private val value: String
    ) : UndoableCommand {
        override val label: String = value

        override suspend fun execute() {
            target += value
        }

        override suspend fun undo() {
            target.removeLast()
        }
    }

    private data class Box(var value: Int)

    private class SetValueCommand(
        private val box: Box,
        private val before: Int,
        private val after: Int
    ) : UndoableCommand {
        override val label: String = "Set Value"

        override suspend fun execute() {
            box.value = after
        }

        override suspend fun undo() {
            box.value = before
        }

        override fun coalesceWith(next: UndoableCommand): UndoableCommand? {
            val nextValue = next as? SetValueCommand ?: return null
            return SetValueCommand(box, before, nextValue.after)
        }
    }
}
