package com.clipforge.ai.presentation.transitions

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing
import com.clipforge.ai.domain.model.TransitionType

private const val TAG = "TransitionPickerScreen"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransitionScreen(
    projectId: String,
    clipId: String? = null,
    onBack: () -> Unit,
    onApplied: () -> Unit = onBack,
    viewModel: TransitionViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.applySucceeded) {
        if (uiState.applySucceeded) {
            Log.d(TAG, "Returning to Timeline")
            onApplied()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Transitions", color = AppColors.OnBackground, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.OnBackground)
                    }
                },
                actions = {
                    TextButton(
                        enabled = !uiState.isSaving,
                        onClick = {
                            if (clipId == null) {
                                viewModel.applyToAll(projectId)
                            } else {
                                viewModel.applyToClip(projectId, clipId)
                            }
                        }
                    ) {
                        if (uiState.isSaving) {
                            CircularProgressIndicator(
                                color = AppColors.Primary,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Text("Apply", color = AppColors.Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
            )
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Selected transition preview
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(0.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(0.7f)
                ) {
                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(0.7f)
                            .clip(RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp))
                            .background(AppColors.TimelineClip),
                        contentAlignment = Alignment.Center
                    ) { Text("Clip A", color = Color.White, fontSize = 12.sp) }

                    Box(
                        modifier = Modifier.width(40.dp).fillMaxHeight(0.7f)
                            .background(
                                Brush.horizontalGradient(listOf(AppColors.TimelineClip, AppColors.Primary, AppColors.Secondary))
                            ),
                        contentAlignment = Alignment.Center
                    ) { Text(uiState.selectedType.label.first().toString(), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold) }

                    Box(
                        modifier = Modifier.weight(1f).fillMaxHeight(0.7f)
                            .clip(RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp))
                            .background(AppColors.Secondary),
                        contentAlignment = Alignment.Center
                    ) { Text("Clip B", color = Color.White, fontSize = 12.sp) }
                }
                Text(
                    uiState.selectedType.label,
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.sm))

            uiState.errorMessage?.let { message ->
                Text(
                    text = message,
                    color = AppColors.Error,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = AppSpacing.md)
                )
                Spacer(modifier = Modifier.height(AppSpacing.sm))
            }

            if (clipId != null) {
                OutlinedButton(
                    enabled = !uiState.isSaving,
                    onClick = { viewModel.applyToAll(projectId) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.md)
                ) {
                    Text("Apply to all transitions")
                }
                Spacer(modifier = Modifier.height(AppSpacing.sm))
            }

            // Section labels
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("All Transitions", fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground, fontSize = 15.sp)
                if (!uiState.isPro) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary)))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Unlock All - Pro", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.sm))

            // Transition grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(AppSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                items(TransitionType.values().toList()) { type ->
                    TransitionCard(
                        type = type,
                        isSelected = uiState.selectedType == type,
                        isLocked = type.isPremium && !uiState.isPro,
                        onClick = { viewModel.selectTransition(type) }
                    )
                }
            }
        }
    }
}

@Composable
private fun TransitionCard(
    type: TransitionType,
    isSelected: Boolean,
    isLocked: Boolean,
    onClick: () -> Unit
) {
    val iconMap = mapOf(
        TransitionType.NONE to "X",
        TransitionType.FADE to "\uD83C\uDF05",
        TransitionType.CROSS_DISSOLVE to "\u2728",
        TransitionType.SLIDE_LEFT to "\u25C0",
        TransitionType.SLIDE_RIGHT to "\u25B6",
        TransitionType.ZOOM_IN to "\uD83D\uDD0D",
        TransitionType.ZOOM_OUT to "\uD83D\uDD0E",
        TransitionType.FLASH to "\u26A1",
        TransitionType.WIPE to "\uD83E\uDDF9",
        TransitionType.WIPE_RIGHT to "\u2192",
        TransitionType.SLIDE_UP to "\u2191",
        TransitionType.SLIDE_DOWN to "\u2193",
        TransitionType.PUSH_UP to "\u2191",
        TransitionType.PUSH_DOWN to "\u2193",
        TransitionType.WIPE_UP to "\u2197",
        TransitionType.WIPE_DOWN to "\u2198",
        TransitionType.MIRROR_FLIP to "\u21C4",
        TransitionType.GLITCH to "\u26A1"
    )

    Card(
        modifier = Modifier
            .aspectRatio(1f)
            .clickable(onClick = onClick)
            .then(
                if (isSelected) Modifier.border(2.dp, AppColors.Primary, RoundedCornerShape(12.dp))
                else Modifier
            ),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) AppColors.Primary.copy(alpha = 0.15f) else AppColors.Surface
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(AppSpacing.sm)
            ) {
                Text(iconMap[type] ?: "?", fontSize = 28.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    type.label, fontSize = 11.sp,
                    color = if (isSelected) AppColors.Primary else AppColors.OnSurface,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center, maxLines = 2
                )
            }
            if (isLocked) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Lock, contentDescription = "Pro", tint = AppColors.Warning, modifier = Modifier.size(20.dp))
                        Text("PRO", fontSize = 10.sp, color = AppColors.Warning, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}
