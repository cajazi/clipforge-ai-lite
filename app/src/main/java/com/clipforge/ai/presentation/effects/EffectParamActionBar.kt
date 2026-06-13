package com.clipforge.ai.presentation.effects

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipforge.ai.core.designsystem.AppColors

const val EFFECT_PARAM_ACTION_BAR_TAG = "effect_param_action_bar"
const val EFFECT_PARAM_DELETE_TAG = "effect_param_delete"
const val EFFECT_PARAM_SLIDER_TAG = "effect_param_slider"

@Composable
fun EffectParamActionBar(
    state: EffectActionBarState,
    onDelete: () -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onSliderChanged: (String, Float) -> Unit,
    onSliderChangeFinished: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.visible) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color(0xFF17171F))
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag(EFFECT_PARAM_ACTION_BAR_TAG),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = state.label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(18.dp), verticalAlignment = Alignment.CenterVertically) {
                if (state.canUndo) {
                    Text(
                        text = "Undo",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onUndo)
                    )
                }
                if (state.canRedo) {
                    Text(
                        text = "Redo",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clickable(onClick = onRedo)
                    )
                }
                Text(
                    text = "Delete",
                    color = AppColors.Error,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clickable(onClick = onDelete)
                        .testTag(EFFECT_PARAM_DELETE_TAG)
                )
            }
        }

        state.sliders.forEach { slider ->
            EffectParamSliderRow(
                slider = slider,
                onSliderChanged = onSliderChanged,
                onSliderChangeFinished = onSliderChangeFinished
            )
        }
    }
}

@Composable
private fun EffectParamSliderRow(
    slider: EffectParamSliderState,
    onSliderChanged: (String, Float) -> Unit,
    onSliderChangeFinished: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = slider.label,
                color = AppColors.TextSecondary,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = formatSliderValue(slider.value),
                color = AppColors.TextMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }
        Slider(
            value = slider.value,
            onValueChange = { value -> onSliderChanged(slider.key, value) },
            valueRange = slider.min..slider.max,
            onValueChangeFinished = { onSliderChangeFinished(slider.key) },
            modifier = Modifier.testTag(EFFECT_PARAM_SLIDER_TAG)
        )
    }
}

private fun formatSliderValue(value: Float): String =
    "%.2f".format(value)
