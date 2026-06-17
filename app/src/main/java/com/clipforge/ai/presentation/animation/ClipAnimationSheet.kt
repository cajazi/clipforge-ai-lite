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
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipforge.ai.core.animation.AnimationPreset
import com.clipforge.ai.core.animation.AnimationPresetType
import com.clipforge.ai.core.animation.AnimationPresets
import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.core.designsystem.AppColors
import kotlin.math.roundToLong

const val CLIP_ANIMATION_SHEET_TAG = "clip_animation_sheet"
const val CLIP_ANIMATION_ROLE_TAB_TAG = "clip_animation_role_tab"
const val CLIP_ANIMATION_DURATION_SLIDER_TAG = "clip_animation_duration_slider"
const val CLIP_ANIMATION_MARKER_TAG = "clip_animation_marker"

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun ClipAnimationSheet(
    visible: Boolean,
    state: AnimationPickerState,
    clipAnimationState: ClipAnimationUiState,
    selectedCategory: AnimationPickerCategory,
    maxDurationMs: Long,
    onDismiss: () -> Unit,
    onRoleSelected: (AnimationRole) -> Unit,
    onCategorySelected: (AnimationPickerCategory) -> Unit,
    onDurationDragging: (Long) -> Unit,
    onDurationCommitted: () -> Unit,
    onApplyPreset: (String) -> Unit,
    onClearAnimation: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    val selectedCategoryState = state.categories.first { it.category == selectedCategory }
    val presets = clipAnimationPresetsFor(clipAnimationState.selectedRole, selectedCategory)
    val hasSelectedClip = clipAnimationState.selectedClipId != null

    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .background(Color(0xFF17171F))
            .padding(horizontal = 16.dp, vertical = 12.dp)
            .testTag(CLIP_ANIMATION_SHEET_TAG),
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
            AnimationRole.entries.forEach { role ->
                AnimationFilterChip(
                    label = role.title,
                    helperLabel = roleSummaryLabel(clipAnimationState, role),
                    selected = clipAnimationState.selectedRole == role,
                    enabled = true,
                    onClick = { onRoleSelected(role) },
                    modifier = Modifier.testTag(CLIP_ANIMATION_ROLE_TAB_TAG)
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
                    onClick = { onCategorySelected(categoryState.category) },
                    modifier = Modifier.testTag(ANIMATION_PICKER_CATEGORY_TAG)
                )
            }
        }

        if (!hasSelectedClip) {
            Text(
                text = "Select a clip to add animation",
                color = AppColors.TextMuted,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold
            )
        } else if (!selectedCategoryState.enabled) {
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
                presets.forEach { tile ->
                    val selected = if (tile.isNone) {
                        clipAnimationState.selectedSummary() == null
                    } else {
                        tile.presetId == clipAnimationState.sessionSelectedPresetId
                    }
                    ClipAnimationPresetTile(
                        preset = tile,
                        enabled = true,
                        selected = selected,
                        onClick = {
                            val presetId = tile.presetId
                            if (presetId == null) onClearAnimation() else onApplyPreset(presetId)
                        }
                    )
                }
            }

            ClipAnimationDurationControl(
                requestedDurationMs = clipAnimationState.requestedDurationMs,
                effectiveDurationMs = clipAnimationState.effectiveDurationMs,
                maxDurationMs = maxDurationMs,
                onDurationDragging = onDurationDragging,
                onDurationCommitted = onDurationCommitted
            )
        }
    }
}

@Composable
private fun ClipAnimationDurationControl(
    requestedDurationMs: Long,
    effectiveDurationMs: Long,
    maxDurationMs: Long,
    onDurationDragging: (Long) -> Unit,
    onDurationCommitted: () -> Unit
) {
    val safeMax = maxDurationMs.coerceAtLeast(MIN_CLIP_ANIMATION_DURATION_MS)
    val requested = requestedDurationMs.coerceIn(MIN_CLIP_ANIMATION_DURATION_MS, safeMax)
    val stepCount = ((safeMax - MIN_CLIP_ANIMATION_DURATION_MS) / CLIP_ANIMATION_DURATION_STEP_MS)
        .toInt()
        .coerceAtLeast(0)
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Duration", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Text(
                text = if (effectiveDurationMs in 1 until requestedDurationMs) {
                    "${requestedDurationMs}ms -> ${effectiveDurationMs}ms"
                } else {
                    "${requestedDurationMs}ms"
                },
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = requested.toFloat(),
            onValueChange = { value ->
                onDurationDragging(value.roundToLong().roundToDurationStep().coerceIn(MIN_CLIP_ANIMATION_DURATION_MS, safeMax))
            },
            onValueChangeFinished = onDurationCommitted,
            valueRange = MIN_CLIP_ANIMATION_DURATION_MS.toFloat()..safeMax.toFloat(),
            steps = stepCount,
            modifier = Modifier.testTag(CLIP_ANIMATION_DURATION_SLIDER_TAG)
        )
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
private fun ClipAnimationPresetTile(
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

private fun clipAnimationPresetsFor(
    role: AnimationRole,
    category: AnimationPickerCategory
): List<AnimationPresetTileState> {
    if (!category.enabled) return emptyList()
    val presets = when (category) {
        AnimationPickerCategory.BASIC -> rolePresets(role)
        AnimationPickerCategory.VIBRATION -> if (role == AnimationRole.COMBO) {
            AnimationPresets.byType(AnimationPresetType.LOOP)
        } else {
            emptyList()
        }
        else -> emptyList()
    }
    return listOf(AnimationPresetTileState.None) + presets.map(AnimationPreset::toTileState)
}

private fun rolePresets(role: AnimationRole): List<AnimationPreset> =
    AnimationPresets.byType(role.toPresetType())

private fun AnimationPreset.toTileState(): AnimationPresetTileState =
    AnimationPresetTileState(
        presetId = id,
        label = displayName
    )

private val AnimationRole.title: String
    get() = when (this) {
        AnimationRole.IN -> "In"
        AnimationRole.OUT -> "Out"
        AnimationRole.COMBO -> "Combo"
    }

private fun roleSummaryLabel(state: ClipAnimationUiState, role: AnimationRole): String? =
    when (role) {
        AnimationRole.IN -> state.inAnimation
        AnimationRole.OUT -> state.outAnimation
        AnimationRole.COMBO -> state.comboAnimation
    }?.let { "${it.effectiveDurationMs}ms" }

private fun ClipAnimationUiState.selectedSummary(): AnimationSummary? = when (selectedRole) {
    AnimationRole.IN -> inAnimation
    AnimationRole.OUT -> outAnimation
    AnimationRole.COMBO -> comboAnimation
}

private fun Long.roundToDurationStep(): Long =
    ((this + CLIP_ANIMATION_DURATION_STEP_MS / 2L) / CLIP_ANIMATION_DURATION_STEP_MS)
        .coerceAtLeast(1L) * CLIP_ANIMATION_DURATION_STEP_MS
