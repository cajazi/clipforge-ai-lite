package com.clipforge.ai.presentation.animation

import com.clipforge.ai.core.animation.AnimationPreset
import com.clipforge.ai.core.animation.AnimationPresetType
import com.clipforge.ai.core.animation.AnimationPresets

enum class AnimationPickerCategory(
    val title: String,
    val presetType: AnimationPresetType
) {
    IN("In", AnimationPresetType.IN),
    OUT("Out", AnimationPresetType.OUT),
    COMBO("Combo", AnimationPresetType.COMBO),
    LOOP("Loop", AnimationPresetType.LOOP)
}

data class AnimationPickerState(
    val title: String,
    val categories: List<AnimationPickerCategoryState>,
    val hasAnimation: Boolean
) {
    companion object {
        const val TITLE = "Animation"
    }
}

data class AnimationPickerCategoryState(
    val category: AnimationPickerCategory,
    val presets: List<AnimationPresetTileState>
)

data class AnimationPresetTileState(
    val presetId: String,
    val label: String
)

fun buildAnimationPickerState(hasAnimation: Boolean): AnimationPickerState =
    AnimationPickerState(
        title = AnimationPickerState.TITLE,
        categories = AnimationPickerCategory.entries.map { category ->
            AnimationPickerCategoryState(
                category = category,
                presets = AnimationPresets.byType(category.presetType).map(AnimationPreset::toTileState)
            )
        },
        hasAnimation = hasAnimation
    )

private fun AnimationPreset.toTileState(): AnimationPresetTileState =
    AnimationPresetTileState(
        presetId = id,
        label = displayName
    )
