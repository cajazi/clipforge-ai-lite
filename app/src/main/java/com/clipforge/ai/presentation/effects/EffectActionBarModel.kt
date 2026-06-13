package com.clipforge.ai.presentation.effects

import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.selection.SelectionTarget

enum class EffectAction {
    Delete
}

data class EffectActionBarState(
    val visible: Boolean,
    val selectedEffectId: String?,
    val label: String,
    val actions: List<EffectAction>,
    val canUndo: Boolean,
    val canRedo: Boolean
) {
    companion object {
        val Hidden = EffectActionBarState(
            visible = false,
            selectedEffectId = null,
            label = "",
            actions = emptyList(),
            canUndo = false,
            canRedo = false
        )
    }
}

fun buildEffectActionBarState(
    effects: List<EffectItem>,
    selectionTarget: SelectionTarget,
    canUndo: Boolean = false,
    canRedo: Boolean = false
): EffectActionBarState {
    val selectedEffectId = (selectionTarget as? SelectionTarget.Effect)?.id ?: return EffectActionBarState.Hidden
    val effect = effects.firstOrNull { it.id == selectedEffectId } ?: return EffectActionBarState.Hidden
    return EffectActionBarState(
        visible = true,
        selectedEffectId = effect.id,
        label = effect.effectId.replaceFirstChar { it.uppercaseChar() },
        actions = listOf(EffectAction.Delete),
        canUndo = canUndo,
        canRedo = canRedo
    )
}
