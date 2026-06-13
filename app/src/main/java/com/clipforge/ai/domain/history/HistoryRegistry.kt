package com.clipforge.ai.domain.history

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class HistoryState(
    val canUndo: Boolean = false,
    val canRedo: Boolean = false,
    val undoCount: Int = 0,
    val redoCount: Int = 0,
    val pendingCoalescedCommand: Boolean = false
)

class HistoryRegistry(
    private val maxEntries: Int = DEFAULT_MAX_ENTRIES
) {
    private val undoStack = ArrayDeque<UndoableCommand>()
    private val redoStack = ArrayDeque<UndoableCommand>()
    private var coalescingKey: String? = null
    private var pendingCoalescedCommand: UndoableCommand? = null
    private val mutableState = MutableStateFlow(HistoryState())

    val state: StateFlow<HistoryState> = mutableState.asStateFlow()

    suspend fun execute(command: UndoableCommand) {
        flushCoalesced()
        command.execute()
        pushUndo(command)
        redoStack.clear()
        publish()
    }

    fun record(command: UndoableCommand) {
        flushCoalesced()
        pushUndo(command)
        redoStack.clear()
        publish()
    }

    suspend fun undo(): Boolean {
        flushCoalesced()
        val command = undoStack.removeLastOrNull() ?: return false
        command.undo()
        redoStack.addLast(command)
        publish()
        return true
    }

    suspend fun redo(): Boolean {
        flushCoalesced()
        val command = redoStack.removeLastOrNull() ?: return false
        command.execute()
        undoStack.addLast(command)
        publish()
        return true
    }

    suspend fun executeCoalesced(
        key: String,
        command: UndoableCommand,
        gestureEnded: Boolean
    ) {
        if (coalescingKey != null && coalescingKey != key) {
            flushCoalesced()
        }
        command.execute()
        pendingCoalescedCommand = pendingCoalescedCommand
            ?.coalesceWith(command)
            ?: command
        coalescingKey = key
        redoStack.clear()
        if (gestureEnded) {
            flushCoalesced()
        } else {
            publish()
        }
    }

    fun flushCoalesced() {
        val pending = pendingCoalescedCommand ?: return
        pushUndo(pending)
        pendingCoalescedCommand = null
        coalescingKey = null
        publish()
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
        pendingCoalescedCommand = null
        coalescingKey = null
        publish()
    }

    private fun pushUndo(command: UndoableCommand) {
        undoStack.addLast(command)
        while (undoStack.size > maxEntries) {
            undoStack.removeFirst()
        }
    }

    private fun publish() {
        mutableState.value = HistoryState(
            canUndo = undoStack.isNotEmpty() || pendingCoalescedCommand != null,
            canRedo = redoStack.isNotEmpty(),
            undoCount = undoStack.size,
            redoCount = redoStack.size,
            pendingCoalescedCommand = pendingCoalescedCommand != null
        )
    }

    private companion object {
        const val DEFAULT_MAX_ENTRIES = 100
    }
}
