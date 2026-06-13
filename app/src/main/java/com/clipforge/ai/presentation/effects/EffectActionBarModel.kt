package com.clipforge.ai.presentation.effects

import com.clipforge.ai.core.effects.EffectParamResolver
import com.clipforge.ai.core.effects.EffectRegistry
import com.clipforge.ai.core.effects.ParamSpec
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.selection.SelectionTarget

enum class EffectAction {
    Delete
}

data class EffectParamSliderState(
    val key: String,
    val label: String,
    val min: Float,
    val max: Float,
    val value: Float
)

data class EffectActionBarState(
    val visible: Boolean,
    val selectedEffectId: String?,
    val label: String,
    val actions: List<EffectAction>,
    val sliders: List<EffectParamSliderState>,
    val canUndo: Boolean,
    val canRedo: Boolean
) {
    companion object {
        val Hidden = EffectActionBarState(
            visible = false,
            selectedEffectId = null,
            label = "",
            actions = emptyList(),
            sliders = emptyList(),
            canUndo = false,
            canRedo = false
        )
    }
}

fun buildEffectActionBarState(
    effects: List<EffectItem>,
    selectionTarget: SelectionTarget,
    registry: EffectRegistry? = null,
    canUndo: Boolean = false,
    canRedo: Boolean = false
): EffectActionBarState {
    val selectedEffectId = (selectionTarget as? SelectionTarget.Effect)?.id ?: return EffectActionBarState.Hidden
    val effect = effects.firstOrNull { it.id == selectedEffectId } ?: return EffectActionBarState.Hidden
    val descriptor = registry?.get(effect.effectId)?.descriptor
    return EffectActionBarState(
        visible = true,
        selectedEffectId = effect.id,
        label = descriptor?.displayName ?: effect.effectId.replaceFirstChar { it.uppercaseChar() },
        actions = listOf(EffectAction.Delete),
        sliders = descriptor?.paramSpecs?.toSliderStates(effect).orEmpty(),
        canUndo = canUndo,
        canRedo = canRedo
    )
}

private fun List<ParamSpec>.toSliderStates(effect: EffectItem): List<EffectParamSliderState> {
    if (isEmpty()) return emptyList()
    val windowStartUs = effect.startMs * 1000L
    val provider = EffectParamResolver.resolve(
        itemId = effect.id,
        storedParams = effect.params,
        specs = this,
        windowStartUs = windowStartUs,
        constantMode = EffectParamResolver.ConstantMode.Snapshot,
        logPrefix = "EFFECT_ACTION_BAR"
    ).provider
    return map { spec ->
        EffectParamSliderState(
            key = spec.key,
            label = spec.label,
            min = spec.min,
            max = spec.max,
            value = provider.valueAt(spec.key, windowStartUs)
        )
    }
}
