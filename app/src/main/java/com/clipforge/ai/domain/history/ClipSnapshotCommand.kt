package com.clipforge.ai.domain.history

import com.clipforge.ai.domain.selection.SelectionTarget

class ClipSnapshotCommand<T>(
    override val label: String,
    private val before: List<T>,
    private val after: List<T>,
    private val selectedBefore: SelectionTarget,
    private val selectedAfter: SelectionTarget,
    private val restore: suspend (items: List<T>, selection: SelectionTarget) -> Unit
) : UndoableCommand {
    override suspend fun execute() {
        restore(after, selectedAfter)
    }

    override suspend fun undo() {
        restore(before, selectedBefore)
    }

    override fun coalesceWith(next: UndoableCommand): UndoableCommand? = null
}
