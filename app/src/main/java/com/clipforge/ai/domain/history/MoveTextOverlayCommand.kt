package com.clipforge.ai.domain.history

import com.clipforge.ai.domain.model.TextOverlay
import com.clipforge.ai.domain.repository.TextOverlayRepository

class MoveTextOverlayCommand(
    private val repository: TextOverlayRepository,
    private val before: TextOverlay,
    private val after: TextOverlay
) : UndoableCommand {
    override val label: String = "Move Text"

    init {
        require(before.id == after.id) { "MoveTextOverlayCommand requires the same overlay id" }
    }

    override suspend fun execute() {
        repository.upsertTextOverlay(after)
    }

    override suspend fun undo() {
        repository.upsertTextOverlay(before)
    }
}
