package com.clipforge.ai.presentation.effects

import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.selection.SelectionTarget

data class EffectChipUiModel(
    val id: String,
    val effectId: String,
    val label: String,
    val startMs: Long,
    val endMs: Long,
    val durationMs: Long,
    val zOrder: Int,
    val isSelected: Boolean
)

fun buildEffectChipUiModels(
    effects: List<EffectItem>,
    selectionTarget: SelectionTarget
): List<EffectChipUiModel> =
    effects
        .sortedWith(compareBy<EffectItem> { it.zOrder }.thenBy { it.startMs }.thenBy { it.id })
        .map { effect ->
            EffectChipUiModel(
                id = effect.id,
                effectId = effect.effectId,
                label = effect.effectId.replaceFirstChar { it.uppercaseChar() },
                startMs = effect.startMs,
                endMs = effect.endMs,
                durationMs = (effect.endMs - effect.startMs).coerceAtLeast(1L),
                zOrder = effect.zOrder,
                isSelected = selectionTarget == SelectionTarget.Effect(effect.id)
            )
        }
