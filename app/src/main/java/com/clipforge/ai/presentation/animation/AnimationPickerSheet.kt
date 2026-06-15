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
const val ANIMATION_PICKER_CATEGORY_TAG = "animation_picker_category"
const val ANIMATION_PICKER_PRESET_TAG = "animation_picker_preset"
const val ANIMATION_PICKER_REMOVE_TAG = "animation_picker_remove"

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
fun AnimationPickerSheet(
    visible: Boolean,
    state: AnimationPickerState,
    totalDurationMs: Long,
    onDismiss: () -> Unit,
    onApplyPreset: (String) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!visible) return

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
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (state.hasAnimation) {
                    Text(
                        text = "Remove",
                        color = Color(0xFFFF8A80),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .clickable(onClick = onRemove)
                            .testTag(ANIMATION_PICKER_REMOVE_TAG)
                    )
                }
                Text(
                    text = "Close",
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clickable(onClick = onDismiss)
                )
            }
        }

        state.categories.forEach { category ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = category.category.title,
                    color = AppColors.TextSecondary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.testTag(ANIMATION_PICKER_CATEGORY_TAG)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    category.presets.forEach { preset ->
                        AnimationPresetTile(
                            preset = preset,
                            enabled = totalDurationMs > 0L,
                            onClick = { onApplyPreset(preset.presetId) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AnimationPresetTile(
    preset: AnimationPresetTileState,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = preset.label,
        color = if (enabled) Color.White else AppColors.TextMuted,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier
            .widthIn(min = 104.dp, max = 148.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFF20202A))
            .border(1.dp, Color(0xFF3A3A46), RoundedCornerShape(6.dp))
            .combinedClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 10.dp)
            .testTag(ANIMATION_PICKER_PRESET_TAG)
    )
}
