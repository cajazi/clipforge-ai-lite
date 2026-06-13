package com.clipforge.ai.domain.history

import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.repository.EffectRepository
import com.clipforge.ai.domain.selection.SelectionController
import com.clipforge.ai.domain.selection.SelectionTarget

class AddEffectCommand(
    private val repository: EffectRepository,
    private val effect: EffectItem,
    private val selectionController: SelectionController? = null,
    private val selectionBefore: SelectionTarget = selectionController?.current ?: SelectionTarget.None
) : UndoableCommand {
    override val label: String = "Add Effect"

    override suspend fun execute() {
        repository.upsertEffect(effect)
        selectionController?.selectEffect(effect.id)
    }

    override suspend fun undo() {
        repository.deleteEffect(effect.id)
        selectionController?.restore(selectionBefore.toSnapshot())
    }
}

class DeleteEffectCommand(
    private val repository: EffectRepository,
    private val effect: EffectItem,
    private val selectionController: SelectionController? = null,
    private val selectionBefore: SelectionTarget = selectionController?.current ?: SelectionTarget.None
) : UndoableCommand {
    override val label: String = "Delete Effect"

    override suspend fun execute() {
        repository.deleteEffect(effect.id)
        if (selectionController?.current == SelectionTarget.Effect(effect.id)) {
            selectionController.clear()
        }
    }

    override suspend fun undo() {
        repository.upsertEffect(effect)
        selectionController?.restore(selectionBefore.toSnapshot())
    }
}

class SelectEffectCommand(
    private val selectionController: SelectionController,
    private val effectId: String,
    private val selectionBefore: SelectionTarget = selectionController.current
) : UndoableCommand {
    override val label: String = "Select Effect"

    override suspend fun execute() {
        selectionController.selectEffect(effectId)
    }

    override suspend fun undo() {
        selectionController.restore(selectionBefore.toSnapshot())
    }
}
