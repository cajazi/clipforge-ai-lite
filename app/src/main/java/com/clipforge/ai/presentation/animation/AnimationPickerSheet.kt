package com.clipforge.ai.presentation.animation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipforge.ai.core.designsystem.AppColors

const val ANIMATION_PICKER_SHEET_TAG = "animation_picker_sheet"
const val ANIMATION_PICKER_TAB_TAG = "animation_picker_tab"
const val ANIMATION_PICKER_CATEGORY_TAG = "animation_picker_category"
const val ANIMATION_PICKER_PRESET_TAG = "animation_picker_preset"

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AnimationPickerSheet(
    visible: Boolean,
    state: AnimationPickerState,
    totalDurationMs: Long,
    selectedPresetId: String?,
    onDismiss: () -> Unit,
    onApplyPreset: (String) -> Unit,
    onClearAnimation: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    var selectedTab by rememberSaveable { mutableStateOf(AnimationPickerTab.IN) }
    var selectedCategory by rememberSaveable { mutableStateOf(AnimationPickerCategory.BASIC) }
    val selectedCategoryState = state.categories.first { it.category == selectedCategory }
    val presets = animationPickerPresetsFor(selectedTab, selectedCategory)
    val pickerEnabled = totalDurationMs > 0L

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(Color(0xFF17171F))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(ANIMATION_PICKER_SHEET_TAG),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.title,
                color = Color.White,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "Close",
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onDismiss)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            state.tabs.forEach { tabState ->
                AnimationFilterChip(
                    label = tabState.tab.title,
                    helperLabel = null,
                    selected = selectedTab == tabState.tab,
                    enabled = true,
                    onClick = { selectedTab = tabState.tab },
                    modifier = Modifier.testTag(ANIMATION_PICKER_TAB_TAG)
                )
            }
        }

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.categories.forEach { categoryState ->
                AnimationFilterChip(
                    label = categoryState.category.title,
                    helperLabel = categoryState.helperLabel,
                    selected = selectedCategory == categoryState.category,
                    enabled = categoryState.enabled,
                    onClick = { selectedCategory = categoryState.category },
                    modifier = Modifier.testTag(ANIMATION_PICKER_CATEGORY_TAG)
                )
            }
        }

        if (!selectedCategoryState.enabled) {
            Text(
                text = "Coming soon",
                color = AppColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { preset ->
                    val selected = if (preset.isNone) {
                        selectedPresetId == null && !state.hasAnimation
                    } else {
                        preset.presetId == selectedPresetId
                    }
                    AnimationPresetTile(
                        preset = preset,
                        enabled = pickerEnabled || preset.isNone,
                        selected = selected,
                        onClick = {
                            val presetId = preset.presetId
                            if (presetId == null) {
                                onClearAnimation()
                            } else {
                                onApplyPreset(presetId)
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimationFilterChip(
    label: String,
    helperLabel: String?,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = when {
        selected -> Color.White
        enabled -> Color(0xFF3A3A46)
        else -> Color(0xFF2C2C36)
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color(0xFF2B2B36) else Color(0xFF20202A))
            .border(1.dp, borderColor, RoundedCornerShape(6.dp))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            color = if (enabled) Color.White else AppColors.TextMuted,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        helperLabel?.let {
            Text(
                text = it,
                color = AppColors.TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimationPresetTile(
    preset: AnimationPresetTileState,
    enabled: Boolean,
    selected: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = preset.label,
        color = when {
            selected -> Color.Black
            enabled -> Color.White
            else -> AppColors.TextMuted
        },
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(min = 104.dp, max = 148.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(if (selected) Color.White else Color(0xFF20202A))
            .border(1.dp, if (selected) Color.White else Color(0xFF3A3A46), RoundedCornerShape(6.dp))
            .combinedClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(ANIMATION_PICKER_PRESET_TAG)
    )
}
