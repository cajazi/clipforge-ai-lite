package com.clipforge.ai.domain.history

import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.selection.SelectionTarget

class TimelineEffectSnapshotCommand<T>(
    override val label: String,
    private val beforeTimeline: List<T>,
    private val afterTimeline: List<T>,
    private val beforeEffects: List<EffectItem>,
    private val afterEffects: List<EffectItem>,
    private val selectedBefore: SelectionTarget,
    private val selectedAfter: SelectionTarget,
    private val restore: suspend (
        timelineItems: List<T>,
        effectItems: List<EffectItem>,
        selection: SelectionTarget
    ) -> Unit
) : UndoableCommand {
    override suspend fun execute() {
        restore(afterTimeline, afterEffects, selectedAfter)
    }

    override suspend fun undo() {
        restore(beforeTimeline, beforeEffects, selectedBefore)
    }

    override fun coalesceWith(next: UndoableCommand): UndoableCommand? = null
}
