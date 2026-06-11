package com.clipforge.ai.presentation.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing
import com.clipforge.ai.domain.model.TransitionType

private fun transitionIcon(type: TransitionType): String = when (type) {
    TransitionType.NONE -> "x"
    TransitionType.DISSOLVE, TransitionType.CROSS_DISSOLVE -> "D"
    TransitionType.FADE -> "F"
    TransitionType.FADE_BLACK -> "B"
    TransitionType.FADE_WHITE -> "W"
    TransitionType.SLIDE_LEFT -> "<"
    TransitionType.SLIDE_RIGHT -> ">"
    TransitionType.PUSH_LEFT -> "<<"
    TransitionType.PUSH_RIGHT -> ">>"
    TransitionType.ZOOM_IN -> "+"
    TransitionType.ZOOM_OUT -> "-"
    TransitionType.BLUR -> "~"
    TransitionType.MOTION_BLUR -> "MB"
    TransitionType.MOTION_BLUR_LEFT -> "M<"
    TransitionType.MOTION_BLUR_RIGHT -> "M>"
    TransitionType.MOTION_BLUR_UP -> "M^"
    TransitionType.MOTION_BLUR_DOWN -> "Mv"
    TransitionType.GAUSSIAN_BLUR -> "GB"
    TransitionType.SPIN -> "@"
    TransitionType.ROTATE -> "R"
    TransitionType.CAMERA_ROLL -> "CR"
    TransitionType.WHIP_PAN_LEFT -> "<~"
    TransitionType.WHIP_PAN_RIGHT -> "~>"
    TransitionType.WHIP_PAN_UP -> "^~"
    TransitionType.WHIP_PAN_DOWN -> "v~"
    TransitionType.FLASH -> "*"
    TransitionType.FLASH_BLACK -> "B*"
    TransitionType.FLASH_WARM -> "W*"
    TransitionType.FLASH_BLUE -> "U*"
    TransitionType.BOUNCE -> "Bo"
    TransitionType.SHAKE -> "Sh"
    TransitionType.SWING -> "Sw"
    TransitionType.POP -> "Po"
    TransitionType.WIPE -> "/"
    TransitionType.SLIDE_UP -> "^"
    TransitionType.SLIDE_DOWN -> "v"
    TransitionType.PUSH_UP -> "^^"
    TransitionType.PUSH_DOWN -> "vv"
    TransitionType.WIPE_UP -> "/^"
    TransitionType.WIPE_DOWN -> "/v"
    TransitionType.MIRROR_FLIP -> "M"
    TransitionType.GLITCH -> "G"
    TransitionType.RGB_SPLIT -> "RGB"
    TransitionType.CHROMATIC_ABERRATION -> "CA"
    TransitionType.CUBE_LEFT -> "3L"
    TransitionType.CUBE_RIGHT -> "3R"
    TransitionType.CUBE_UP -> "3U"
    TransitionType.CUBE_DOWN -> "3D"
    TransitionType.FLIP_LEFT -> "FL"
    TransitionType.FLIP_RIGHT -> "FR"
    TransitionType.FLIP_UP -> "FU"
    TransitionType.FLIP_DOWN -> "FD"
    TransitionType.FLIP_HORIZONTAL -> "FH"
    TransitionType.FLIP_VERTICAL -> "FV"
    TransitionType.DOOR_OPEN -> "DO"
    TransitionType.DOOR_CLOSE -> "DC"
    TransitionType.CAROUSEL -> "Ca"
    TransitionType.BOOK_TURN -> "BT"
    TransitionType.PAGE_TURN -> "PT"
    TransitionType.PAGE_TURN_LEFT -> "PL"
    TransitionType.PAGE_TURN_RIGHT -> "PR"
    TransitionType.PAGE_TURN_UP -> "PU"
    TransitionType.PAGE_TURN_DOWN -> "PD"
    TransitionType.FOLD -> "Fo"
    TransitionType.TUNNEL -> "Tu"
    TransitionType.PRISM -> "Pr"
    else -> "?"
}

private fun transitionDisplayName(type: TransitionType): String = when (type) {
    TransitionType.NONE -> "None"
    TransitionType.FADE -> "Fade"
    TransitionType.DISSOLVE -> "Dissolve"
    TransitionType.CROSS_DISSOLVE -> "Crossfade"
    TransitionType.FADE_BLACK -> "Dip to Black"
    TransitionType.FADE_WHITE -> "Dip to White"
    TransitionType.SLIDE_LEFT -> "Slide Left"
    TransitionType.SLIDE_RIGHT -> "Slide Right"
    TransitionType.SLIDE_UP -> "Slide Up"
    TransitionType.SLIDE_DOWN -> "Slide Down"
    TransitionType.PUSH_LEFT -> "Push Left"
    TransitionType.PUSH_RIGHT -> "Push Right"
    TransitionType.PUSH_UP -> "Push Up"
    TransitionType.PUSH_DOWN -> "Push Down"
    TransitionType.ZOOM_IN -> "Zoom In"
    TransitionType.ZOOM_OUT -> "Zoom Out"
    TransitionType.BLUR -> "Blur"
    TransitionType.GLITCH -> "Glitch"
    TransitionType.SPIN -> "Spin"
    TransitionType.FLASH -> "Flash"
    TransitionType.FLASH_WARM -> "Flash Warm"
    TransitionType.FLASH_BLUE -> "Flash Blue"
    TransitionType.WIPE -> "Wipe"
    TransitionType.WIPE_UP -> "Wipe Up"
    TransitionType.WIPE_DOWN -> "Wipe Down"
    TransitionType.MIRROR_FLIP -> "Mirror Flip"
    else -> type.label
}

private data class TransitionCategory(val name: String, val types: List<TransitionType>)

private val BASIC_TYPES = listOf(
    TransitionType.FADE,
    TransitionType.DISSOLVE,
    TransitionType.CROSS_DISSOLVE,
    TransitionType.FADE_BLACK,
    TransitionType.FADE_WHITE
)
private val SLIDE_TYPES = listOf(
    TransitionType.SLIDE_LEFT,
    TransitionType.SLIDE_RIGHT,
    TransitionType.SLIDE_UP,
    TransitionType.SLIDE_DOWN
)
private val PUSH_TYPES = listOf(
    TransitionType.PUSH_LEFT,
    TransitionType.PUSH_RIGHT,
    TransitionType.PUSH_UP,
    TransitionType.PUSH_DOWN
)
private val ZOOM_TYPES = listOf(
    TransitionType.ZOOM_IN,
    TransitionType.ZOOM_OUT
)
private val BLUR_TYPES = listOf(
    TransitionType.BLUR,
    TransitionType.MOTION_BLUR,
    TransitionType.MOTION_BLUR_LEFT,
    TransitionType.MOTION_BLUR_RIGHT,
    TransitionType.MOTION_BLUR_UP,
    TransitionType.MOTION_BLUR_DOWN,
    TransitionType.GAUSSIAN_BLUR
)
private val GLITCH_TYPES = listOf(
    TransitionType.GLITCH,
    TransitionType.RGB_SPLIT,
    TransitionType.CHROMATIC_ABERRATION
)
private val CAMERA_TYPES = listOf(
    TransitionType.SPIN,
    TransitionType.ROTATE,
    TransitionType.CAMERA_ROLL,
    TransitionType.WHIP_PAN_LEFT,
    TransitionType.WHIP_PAN_RIGHT,
    TransitionType.WHIP_PAN_UP,
    TransitionType.WHIP_PAN_DOWN
)
private val EFFECT_TYPES = listOf(
    TransitionType.FLASH,
    TransitionType.FLASH_BLACK,
    TransitionType.FLASH_WARM,
    TransitionType.FLASH_BLUE,
    TransitionType.BOUNCE,
    TransitionType.SHAKE,
    TransitionType.SWING,
    TransitionType.POP
)
private val THREE_D_TYPES = listOf(
    TransitionType.CUBE_LEFT,
    TransitionType.CUBE_RIGHT,
    TransitionType.CUBE_UP,
    TransitionType.CUBE_DOWN,
    TransitionType.FLIP_LEFT,
    TransitionType.FLIP_RIGHT,
    TransitionType.FLIP_UP,
    TransitionType.FLIP_DOWN,
    TransitionType.FLIP_HORIZONTAL,
    TransitionType.FLIP_VERTICAL,
    TransitionType.DOOR_OPEN,
    TransitionType.DOOR_CLOSE,
    TransitionType.CAROUSEL,
    TransitionType.BOOK_TURN,
    TransitionType.PAGE_TURN,
    TransitionType.PAGE_TURN_LEFT,
    TransitionType.PAGE_TURN_RIGHT,
    TransitionType.PAGE_TURN_UP,
    TransitionType.PAGE_TURN_DOWN,
    TransitionType.FOLD,
    TransitionType.TUNNEL,
    TransitionType.PRISM
)

private val TRANSITION_CATEGORIES = listOf(
    TransitionCategory(
        "All",
        listOf(TransitionType.NONE) + BASIC_TYPES + SLIDE_TYPES + PUSH_TYPES + ZOOM_TYPES +
            BLUR_TYPES + GLITCH_TYPES + CAMERA_TYPES + EFFECT_TYPES + THREE_D_TYPES
    ),
    TransitionCategory("Basic", BASIC_TYPES),
    TransitionCategory("Slide", SLIDE_TYPES),
    TransitionCategory("Push", PUSH_TYPES),
    TransitionCategory("Zoom", ZOOM_TYPES),
    TransitionCategory("Blur", BLUR_TYPES),
    TransitionCategory("Glitch", GLITCH_TYPES),
    TransitionCategory("Camera", CAMERA_TYPES),
    TransitionCategory("Effects", EFFECT_TYPES),
    TransitionCategory("3D", THREE_D_TYPES)
)

// 0.1s .. 3.0s
private val DURATION_OPTIONS = listOf(100L, 300L, 500L, 1_000L, 1_500L, 2_000L, 3_000L)

private fun durationLabel(durationMs: Long): String {
    val seconds = durationMs / 1_000f
    return "%.1fs".format(seconds)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitionPickerSheet(
    clipId: String,
    current: TransitionType,
    currentDurationMs: Long,
    isPro: Boolean,
    onApplyOne: (clipId: String, type: TransitionType, durationMs: Long) -> Unit,
    onApplyAll: (type: TransitionType, durationMs: Long) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(current) }
    var selectedDurationMs by remember(currentDurationMs) {
        mutableLongStateOf(currentDurationMs.takeIf { it > 0L } ?: 500L)
    }
    var query by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("All") }

    val visibleTypes = remember(query, selectedCategory) {
        if (query.isBlank()) {
            TRANSITION_CATEGORIES.firstOrNull { it.name == selectedCategory }?.types
                ?: TRANSITION_CATEGORIES.first().types
        } else {
            TRANSITION_CATEGORIES.first().types.filter {
                transitionDisplayName(it).contains(query.trim(), ignoreCase = true)
            }
        }
    }

    val applyDurationMs = if (selected == TransitionType.NONE) 0L else selectedDurationMs

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = AppColors.Surface,
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(top = 12.dp, bottom = 4.dp)
                    .size(width = 40.dp, height = 4.dp)
                    .clip(CircleShape)
                    .background(AppColors.SurfaceVariant)
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDismiss) { Text("Cancel", color = AppColors.TextSecondary) }
                Text("Transitions", fontWeight = FontWeight.Bold, color = AppColors.OnBackground, fontSize = 16.sp)
                TextButton(onClick = { onApplyOne(clipId, selected, applyDurationMs) }) {
                    Text("Apply", color = AppColors.Primary, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .padding(horizontal = AppSpacing.md)
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(0.75f).fillMaxHeight(0.65f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp))
                            .background(AppColors.TimelineClip),
                        contentAlignment = Alignment.Center
                    ) { Text("A", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                    Box(
                        modifier = Modifier.width(32.dp).fillMaxHeight()
                            .background(
                                if (selected != TransitionType.NONE)
                                    Brush.horizontalGradient(listOf(AppColors.TimelineClip, AppColors.Primary, AppColors.Secondary))
                                else
                                    Brush.horizontalGradient(listOf(AppColors.TimelineClip, AppColors.TimelineClip))
                            ),
                        contentAlignment = Alignment.Center
                    ) { Text(transitionIcon(selected), fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight()
                            .clip(RoundedCornerShape(topEnd = 6.dp, bottomEnd = 6.dp))
                            .background(AppColors.Secondary),
                        contentAlignment = Alignment.Center
                    ) { Text("B", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
                }
                Text(
                    if (selected == TransitionType.NONE) "None"
                    else "${transitionDisplayName(selected)}  ${durationLabel(selectedDurationMs)}",
                    color = Color.White.copy(alpha = 0.72f),
                    fontSize = 10.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                )
            }

            Spacer(Modifier.height(AppSpacing.sm))

            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                placeholder = { Text("Search transitions", fontSize = 13.sp) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md)
            )

            Spacer(Modifier.height(AppSpacing.sm))

            if (query.isBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = AppSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TRANSITION_CATEGORIES.forEach { category ->
                        val isSel = selectedCategory == category.name
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isSel) AppColors.Primary else AppColors.SurfaceVariant)
                                .clickable { selectedCategory = category.name }
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                category.name,
                                fontSize = 12.sp,
                                color = if (isSel) Color.White else AppColors.OnBackground,
                                fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
                Spacer(Modifier.height(AppSpacing.sm))
            }

            Text("Duration", color = AppColors.TextSecondary, fontSize = 12.sp, modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md))
            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = AppSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DURATION_OPTIONS.forEach { duration ->
                    val isSel = selectedDurationMs == duration
                    Button(
                        onClick = { selectedDurationMs = duration },
                        enabled = selected != TransitionType.NONE,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isSel) AppColors.Primary else AppColors.SurfaceVariant,
                            contentColor = if (isSel) Color.White else AppColors.OnBackground,
                            disabledContainerColor = AppColors.SurfaceVariant.copy(alpha = 0.45f),
                            disabledContentColor = AppColors.TextSecondary
                        ),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                    ) { Text(durationLabel(duration), fontSize = 12.sp) }
                }
            }

            Spacer(Modifier.height(AppSpacing.md))

            if (visibleTypes.isEmpty()) {
                Text(
                    "No transitions match \"$query\"",
                    color = AppColors.TextSecondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(AppSpacing.md)
                )
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).padding(horizontal = AppSpacing.md),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    items(visibleTypes) { type ->
                        val isSel = selected == type
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSel) 2.dp else 0.dp,
                                    color = if (isSel) AppColors.Primary else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .background(if (isSel) AppColors.Primary.copy(alpha = 0.12f) else AppColors.SurfaceVariant)
                                .clickable {
                                    selected = type
                                    if (type == TransitionType.NONE) selectedDurationMs = 500L
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                modifier = Modifier.fillMaxSize().padding(AppSpacing.xs),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Text(transitionIcon(type), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = if (isSel) AppColors.Primary else AppColors.OnBackground)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    transitionDisplayName(type),
                                    fontSize = 10.sp,
                                    color = if (isSel) AppColors.Primary else AppColors.OnBackground,
                                    fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                    textAlign = TextAlign.Center,
                                    maxLines = 2,
                                    lineHeight = 12.sp
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.md))

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Button(
                    onClick = { onApplyOne(clipId, selected, applyDurationMs) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.Primary)
                ) { Text("Apply", fontSize = 13.sp, color = Color.White) }
                OutlinedButton(
                    onClick = { onApplyAll(selected, applyDurationMs) },
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Primary)
                ) { Text("Apply all", fontSize = 13.sp, color = AppColors.Primary) }
            }
        }
    }
}
