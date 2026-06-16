package com.clipforge.ai.presentation.animation

import com.clipforge.ai.core.animation.AnimationPreset
import com.clipforge.ai.core.animation.AnimationPresetType
import com.clipforge.ai.core.animation.AnimationPresets

enum class AnimationPickerTab(
    val title: String,
    val presetType: AnimationPresetType
) {
    IN("In", AnimationPresetType.IN),
    OUT("Out", AnimationPresetType.OUT),
    COMBO("Combo", AnimationPresetType.COMBO)
}

enum class AnimationPickerCategory(
    val title: String,
    val enabled: Boolean,
    val comingSoon: Boolean
) {
    TRENDING("Trending", enabled = false, comingSoon = true),
    BASIC("Basic", enabled = true, comingSoon = false),
    LIGHT("Light", enabled = false, comingSoon = true),
    GLITCH("Glitch", enabled = false, comingSoon = true),
    MASK("Mask", enabled = false, comingSoon = true),
    THREE_D("3D", enabled = false, comingSoon = true),
    VIBRATION("Vibration", enabled = true, comingSoon = false)
}

data class AnimationPickerState(
    val title: String,
    val tabs: List<AnimationPickerTabState>,
    val categories: List<AnimationPickerSecondaryCategoryState>,
    val hasAnimation: Boolean
) {
    companion object {
        const val TITLE = "Animation"
    }
}

data class AnimationPickerTabState(
    val tab: AnimationPickerTab
)

data class AnimationPickerSecondaryCategoryState(
    val category: AnimationPickerCategory,
    val enabled: Boolean,
    val helperLabel: String?
)

data class AnimationPresetTileState(
    val presetId: String?,
    val label: String,
    val isNone: Boolean = false
) {
    companion object {
        val None = AnimationPresetTileState(
            presetId = null,
            label = "None",
            isNone = true
        )
    }
}

fun buildAnimationPickerState(hasAnimation: Boolean): AnimationPickerState =
    AnimationPickerState(
        title = AnimationPickerState.TITLE,
        tabs = AnimationPickerTab.entries.map { AnimationPickerTabState(it) },
        categories = AnimationPickerCategory.entries.map { category ->
            AnimationPickerSecondaryCategoryState(
                category = category,
                enabled = category.enabled,
                helperLabel = if (category.comingSoon) "Coming soon" else null
            )
        },
        hasAnimation = hasAnimation
    )

fun animationPickerPresetsFor(
    tab: AnimationPickerTab,
    category: AnimationPickerCategory
): List<AnimationPresetTileState> {
    if (!category.enabled) return emptyList()
    val presets = when (category) {
        AnimationPickerCategory.BASIC -> AnimationPresets.byType(tab.presetType)
        AnimationPickerCategory.VIBRATION -> {
            if (tab == AnimationPickerTab.COMBO) {
                AnimationPresets.byType(AnimationPresetType.LOOP)
            } else {
                emptyList()
            }
        }
        else -> emptyList()
    }
    return listOf(AnimationPresetTileState.None) + presets.map(AnimationPreset::toTileState)
}

private fun AnimationPreset.toTileState(): AnimationPresetTileState =
    AnimationPresetTileState(
        presetId = id,
        label = displayName
    )
