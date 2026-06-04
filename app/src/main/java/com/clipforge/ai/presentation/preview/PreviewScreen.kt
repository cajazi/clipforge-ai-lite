package com.clipforge.ai.presentation.preview

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing
import com.clipforge.ai.core.utils.TimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PreviewScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: PreviewViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Preview", color = AppColors.OnBackground, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.OnBackground)
                    }
                },
                actions = {
                    TextButton(onClick = viewModel::toggleMute) {
                        Text(
                            if (uiState.isMuted) "Unmute" else "Mute",
                            color = if (uiState.isMuted) AppColors.Error else AppColors.Primary,
                            fontSize = 13.sp
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Black)
            )
        },
        containerColor = Color.Black
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Video area
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f).background(Color.Black),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\uD83C\uDFAC", fontSize = 72.sp)
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                    Text("Preview", color = AppColors.TextSecondary, fontSize = 18.sp, fontWeight = FontWeight.Light)
                    Text("Add clips to preview your video", color = AppColors.TextSecondary.copy(alpha = 0.5f), fontSize = 13.sp)
                }
                // Watermark
                Box(
                    modifier = Modifier.align(Alignment.BottomEnd).padding(AppSpacing.md)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("ClipForge AI", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                }
            }

            // Controls
            Column(
                modifier = Modifier.fillMaxWidth().background(Color.Black).padding(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                // Progress bar
                Column {
                    LinearProgressIndicator(
                        progress = { if (uiState.durationMs > 0) uiState.positionMs / uiState.durationMs.toFloat() else 0f },
                        modifier = Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp)),
                        color = AppColors.Primary,
                        trackColor = Color.White.copy(alpha = 0.2f)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(TimeFormatter.formatMs(uiState.positionMs), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                        Text(TimeFormatter.formatMs(uiState.durationMs), color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp)
                    }
                }
                // Play button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    IconButton(
                        onClick = viewModel::togglePlay,
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                            .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.PrimaryVariant)))
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = "Play",
                            tint = Color.White, modifier = Modifier.size(36.dp))
                    }
                }
                Spacer(modifier = Modifier.height(AppSpacing.sm))
            }
        }
    }
}
