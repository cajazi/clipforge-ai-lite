package com.clipforge.ai.domain.history

import com.clipforge.ai.domain.model.TextOverlay
import com.clipforge.ai.domain.repository.TextOverlayRepository

class AddTextOverlayCommand(
    private val repository: TextOverlayRepository,
    private val textOverlay: TextOverlay
) : UndoableCommand {
    override val label: String = "Add Text"

    override suspend fun execute() {
        repository.upsertTextOverlay(textOverlay)
    }

    override suspend fun undo() {
        repository.deleteTextOverlay(textOverlay.id)
    }
}
