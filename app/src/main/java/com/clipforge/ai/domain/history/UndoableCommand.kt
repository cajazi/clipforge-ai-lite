package com.clipforge.ai.domain.history

interface UndoableCommand {
    val label: String

    suspend fun execute()

    suspend fun undo()

    fun coalesceWith(next: UndoableCommand): UndoableCommand? = null
}
