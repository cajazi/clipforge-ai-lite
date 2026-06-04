package com.clipforge.ai.presentation.music

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing
import com.clipforge.ai.core.utils.TimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MusicScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: MusicViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Music", color = AppColors.OnBackground, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.OnBackground)
                    }
                },
                actions = {
                    if (uiState.selectedTrackId != null) {
                        TextButton(onClick = { viewModel.addToProject(); onBack() }) {
                            Text("Add", color = AppColors.Primary, fontWeight = FontWeight.Bold)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
            )
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {

            // Header info
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Royalty-Free Tracks", fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground, fontSize = 15.sp)
                if (!uiState.isPro) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary)))
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) { Text("Pro unlocks all", fontSize = 11.sp, color = Color.White, fontWeight = FontWeight.Bold) }
                }
            }

            // No copyrighted music notice
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                shape = RoundedCornerShape(8.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
            ) {
                Row(modifier = Modifier.padding(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Text("\uD83C\uDFB5", fontSize = 16.sp)
                    Text("All tracks are royalty-free and safe for all platforms", fontSize = 12.sp, color = AppColors.TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.sm))

            // Track list
            LazyColumn(
                contentPadding = PaddingValues(horizontal = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                items(uiState.tracks, key = { it.id }) { track ->
                    val isSelected = track.id == uiState.selectedTrackId
                    val isLocked = track.isPremium && !uiState.isPro

                    Card(
                        modifier = Modifier.fillMaxWidth().clickable { viewModel.selectTrack(track.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) AppColors.Primary.copy(alpha = 0.15f) else AppColors.Surface
                        ),
                        border = if (isSelected) CardDefaults.outlinedCardBorder().copy(
                            brush = Brush.linearGradient(listOf(AppColors.Primary, AppColors.Primary))
                        ) else null
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(AppSpacing.sm),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            // Play button / Lock
                            Box(
                                modifier = Modifier.size(44.dp).clip(CircleShape)
                                    .background(if (isSelected) AppColors.Primary else AppColors.SurfaceVariant)
                                    .clickable { if (!isLocked) viewModel.togglePlay(track.id) },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isLocked) {
                                    Icon(Icons.Default.Lock, contentDescription = "Locked", tint = AppColors.Warning, modifier = Modifier.size(18.dp))
                                } else {
                                    Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                                        tint = if (isSelected) Color.White else AppColors.TextSecondary, modifier = Modifier.size(22.dp))
                                }
                            }

                            // Track info
                            Column(modifier = Modifier.weight(1f)) {
                                Text(track.title, fontWeight = FontWeight.Medium, color = if (isSelected) AppColors.Primary else AppColors.OnBackground,
                                    fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, color = AppColors.TextSecondary, fontSize = 12.sp)
                            }

                            // Duration / Pro badge
                            Column(horizontalAlignment = Alignment.End) {
                                Text(TimeFormatter.formatMs(track.durationMs), color = AppColors.TextSecondary, fontSize = 12.sp)
                                if (track.isPremium) {
                                    Text("PRO", fontSize = 9.sp, color = AppColors.Warning, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }

                item {
                    // Custom audio option
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm).clickable { /* TODO: open audio file picker */ },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                        ) {
                            Box(
                                modifier = Modifier.size(44.dp).clip(CircleShape)
                                    .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary))),
                                contentAlignment = Alignment.Center
                            ) { Text("+", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold) }
                            Column {
                                Text("Use My Own Music", fontWeight = FontWeight.Medium, color = AppColors.OnBackground, fontSize = 14.sp)
                                Text("Pick an audio file from your device", color = AppColors.TextSecondary, fontSize = 12.sp)
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                }
            }
        }
    }
}
