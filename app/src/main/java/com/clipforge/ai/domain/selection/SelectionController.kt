package com.clipforge.ai.domain.selection

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class SelectionController(
    initialSelection: SelectionTarget = SelectionTarget.None
) {
    private val mutableSelection = MutableStateFlow(initialSelection)

    val selection: StateFlow<SelectionTarget> = mutableSelection.asStateFlow()

    val current: SelectionTarget
        get() = mutableSelection.value

    fun selectClip(id: String): SelectionTarget =
        setSelection(SelectionTarget.Clip(id))

    fun selectEffect(id: String): SelectionTarget =
        setSelection(SelectionTarget.Effect(id))

    fun clear(): SelectionTarget =
        setSelection(SelectionTarget.None)

    fun restore(snapshot: SelectionSnapshot): SelectionTarget =
        setSelection(SelectionTarget.fromSnapshot(snapshot))

    private fun setSelection(target: SelectionTarget): SelectionTarget {
        mutableSelection.value = target
        return target
    }
}
