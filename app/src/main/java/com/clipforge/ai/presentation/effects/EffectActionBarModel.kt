package com.clipforge.ai.presentation.effects

import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.selection.SelectionController
import com.clipforge.ai.domain.selection.SelectionTarget

enum class EffectAction {
    Delete
}

data class EffectActionBarState(
    val visible: Boolean,
    val selectedEffectId: String?,
    val label: String,
    val actions: List<EffectAction>
) {
    companion object {
        val Hidden = EffectActionBarState(
            visible = false,
            selectedEffectId = null,
            label = "",
            actions = emptyList()
        )
    }
}

fun buildEffectActionBarState(
    effects: List<EffectItem>,
    selectionTarget: SelectionTarget
): EffectActionBarState {
    val selectedEffectId = (selectionTarget as? SelectionTarget.Effect)?.id ?: return EffectActionBarState.Hidden
    val effect = effects.firstOrNull { it.id == selectedEffectId } ?: return EffectActionBarState.Hidden
    return EffectActionBarState(
        visible = true,
        selectedEffectId = effect.id,
        label = effect.effectId.replaceFirstChar { it.uppercaseChar() },
        actions = listOf(EffectAction.Delete)
    )
}

suspend fun deleteSelectedEffect(
    selectionController: SelectionController,
    deleteEffect: suspend (String) -> Unit
): Boolean {
    val selectedEffectId = (selectionController.current as? SelectionTarget.Effect)?.id ?: return false
    deleteEffect(selectedEffectId)
    if (selectionController.current == SelectionTarget.Effect(selectedEffectId)) {
        selectionController.clear()
    }
    return true
}
