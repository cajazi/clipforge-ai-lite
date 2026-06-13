package com.clipforge.ai.domain.history

import com.clipforge.ai.domain.selection.SelectionTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
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
    fun `record pushes command without executing it`() = runBlocking {
        val target = mutableListOf("already-applied")
        val registry = HistoryRegistry()

        registry.record(ListCommand(target, "should-not-execute"))

        assertEquals(listOf("already-applied"), target)
        assertTrue(registry.state.value.canUndo)
    }

    @Test
    fun `redo is cleared across clip and effect command types`() = runBlocking {
        val target = mutableListOf<String>()
        val registry = HistoryRegistry()

        registry.execute(ListCommand(target, "clip"))
        registry.undo()
        assertTrue(registry.state.value.canRedo)

        registry.execute(ListCommand(target, "effect"))

        assertFalse(registry.state.value.canRedo)
        assertEquals(listOf("effect"), target)
    }

    @Test
    fun `clip snapshot restores clip effect and none selections`() = runBlocking {
        val restoredSelections = mutableListOf<SelectionTarget>()
        val registry = HistoryRegistry()
        val command = ClipSnapshotCommand(
            label = "Clip Snapshot",
            before = listOf("before"),
            after = listOf("after"),
            selectedBefore = SelectionTarget.Clip("clip-1"),
            selectedAfter = SelectionTarget.Effect("effect-1")
        ) { _, selection ->
            restoredSelections += selection
        }

        registry.record(command)
        registry.undo()
        registry.redo()

        assertEquals(
            listOf(SelectionTarget.Clip("clip-1"), SelectionTarget.Effect("effect-1")),
            restoredSelections
        )

        val noneCommand = ClipSnapshotCommand(
            label = "Clear Selection",
            before = emptyList<String>(),
            after = emptyList(),
            selectedBefore = SelectionTarget.Effect("effect-1"),
            selectedAfter = SelectionTarget.None
        ) { _, selection ->
            restoredSelections += selection
        }
        registry.record(noneCommand)
        registry.redo()
        registry.undo()
        registry.redo()

        assertEquals(SelectionTarget.None, restoredSelections.last())
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

    @Test
    fun `coalesced command is flushed before clip snapshot record`() = runBlocking {
        val value = Box(0)
        val restored = mutableListOf<List<String>>()
        val registry = HistoryRegistry()

        registry.executeCoalesced("slider", SetValueCommand(value, before = 0, after = 10), gestureEnded = false)
        registry.record(
            ClipSnapshotCommand(
                label = "Clip",
                before = listOf("before"),
                after = listOf("after"),
                selectedBefore = SelectionTarget.None,
                selectedAfter = SelectionTarget.Clip("clip-1")
            ) { items, _ ->
                restored += items
            }
        )

        assertEquals(2, registry.state.value.undoCount)
        registry.undo()
        assertEquals(listOf("before"), restored.last())
        registry.undo()
        assertEquals(0, value.value)
    }

    @Test
    fun `max entries drops oldest command`() = runBlocking {
        val target = mutableListOf<String>()
        val registry = HistoryRegistry(maxEntries = 2)

        registry.execute(ListCommand(target, "one"))
        registry.execute(ListCommand(target, "two"))
        registry.execute(ListCommand(target, "three"))

        assertEquals(2, registry.state.value.undoCount)
        registry.undo()
        registry.undo()

        assertEquals(listOf("one"), target)
        assertFalse(registry.undo())
    }

    @Test
    fun `one shared registry instance orders clip and effect commands`() = runBlocking {
        val target = mutableListOf<String>()
        val sharedRegistry = HistoryRegistry()
        val timelineRegistry = sharedRegistry
        val effectRegistry = sharedRegistry

        assertSame(timelineRegistry, effectRegistry)

        target += "clip-recorded"
        timelineRegistry.record(ListCommand(target, "clip-recorded"))
        effectRegistry.execute(ListCommand(target, "effect-executed"))

        sharedRegistry.undo()
        assertEquals(listOf("clip-recorded"), target)
        sharedRegistry.undo()
        assertEquals(emptyList<String>(), target)
        sharedRegistry.redo()
        assertEquals(listOf("clip-recorded"), target)
        sharedRegistry.redo()
        assertEquals(listOf("clip-recorded", "effect-executed"), target)
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
