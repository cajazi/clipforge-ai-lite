package com.clipforge.ai.presentation.export

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.platform.LocalContext
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing
import com.clipforge.ai.domain.model.RenderJobStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExportProgressScreen(
    projectId: String,
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: ExportViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    androidx.compose.runtime.LaunchedEffect(projectId) { viewModel.startExport(projectId) }
    val isDone = uiState.status == RenderJobStatus.COMPLETED
    val isFailed = uiState.status == RenderJobStatus.FAILED
    val isCancelled = uiState.status == RenderJobStatus.CANCELLED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Exporting", color = AppColors.OnBackground, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
            )
        },
        containerColor = AppColors.Background
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(AppSpacing.xl),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {

            // Animated status indicator
            when {
                isDone -> {
                    val scale by rememberInfiniteTransition(label = "done").animateFloat(
                        initialValue = 0.9f, targetValue = 1.1f,
                        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
                        label = "scale"
                    )
                    Box(
                        modifier = Modifier.size(100.dp).scale(scale)
                            .clip(CircleShape)
                            .background(AppColors.Success),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Check, contentDescription = "Done", tint = Color.White, modifier = Modifier.size(52.dp))
                    }
                }
                isFailed || isCancelled -> {
                    Box(
                        modifier = Modifier.size(100.dp).clip(CircleShape).background(AppColors.Error),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Failed", tint = Color.White, modifier = Modifier.size(52.dp))
                    }
                }
                else -> {
                    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(100.dp)) {
                        CircularProgressIndicator(
                            progress = { uiState.progressPercent / 100f },
                            modifier = Modifier.fillMaxSize(),
                            color = AppColors.Primary,
                            trackColor = AppColors.Surface,
                            strokeWidth = 8.dp
                        )
                        Text("${uiState.progressPercent}%", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = AppColors.OnBackground)
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.lg))

            // Status label
            Text(
                when (uiState.status) {
                    RenderJobStatus.QUEUED      -> "Queued"
                    RenderJobStatus.UPLOADING   -> "Uploading"
                    RenderJobStatus.PROCESSING  -> "Processing"
                    RenderJobStatus.RENDERING   -> "Rendering"
                    RenderJobStatus.COMPLETED   -> "Export Complete!"
                    RenderJobStatus.FAILED      -> "Export Failed"
                    RenderJobStatus.CANCELLED   -> "Cancelled"
                },
                fontSize = 22.sp, fontWeight = FontWeight.Bold,
                color = when {
                    isDone -> AppColors.Success
                    isFailed || isCancelled -> AppColors.Error
                    else -> AppColors.OnBackground
                }
            )

            Spacer(modifier = Modifier.height(AppSpacing.sm))
            Text(uiState.statusMessage, fontSize = 14.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(AppSpacing.xl))

            // Progress bar (visible during render)
            if (!isDone && !isFailed && !isCancelled) {
                Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    LinearProgressIndicator(
                        progress = { uiState.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = AppColors.Primary,
                        trackColor = AppColors.Surface
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.xl))
                    OutlinedButton(
                        onClick = viewModel::cancelExport,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Error)
                    ) {
                        Text("Cancel Export", color = AppColors.Error)
                    }
                }
            }

            // Export info card
            if (isDone) {
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
                ) {
                    Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Quality", color = AppColors.TextSecondary, fontSize = 13.sp)
                            Text(uiState.quality, color = AppColors.OnBackground, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Watermark", color = AppColors.TextSecondary, fontSize = 13.sp)
                            Text(if (uiState.hasWatermark) "Yes (Free)" else "No (Pro)", color = AppColors.OnBackground, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                        Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                            Text("Status", color = AppColors.TextSecondary, fontSize = 13.sp)
                            Text("Ready to download", color = AppColors.Success, fontWeight = FontWeight.Medium, fontSize = 13.sp)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(AppSpacing.lg))

                Button(
                    onClick = onDone,
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize()
                            .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.PrimaryVariant)), shape = RoundedCornerShape(16.dp)),
                        contentAlignment = Alignment.Center
                    ) { Text("Back to Home", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White) }
                }

                Spacer(modifier = Modifier.height(AppSpacing.sm))

                OutlinedButton(
                    onClick = {
                        uiState.outputUrl?.let { path ->
                            val file = java.io.File(path)
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context, "${context.packageName}.fileprovider", file
                            )
                            val share = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                type = "video/mp4"
                                putExtra(android.content.Intent.EXTRA_STREAM, uri)
                                addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            context.startActivity(android.content.Intent.createChooser(share, "Share video"))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AppColors.Primary)
                ) { Text("Share Video", color = AppColors.Primary, fontWeight = FontWeight.SemiBold) }
            }

            if (isFailed) {
                Spacer(modifier = Modifier.height(AppSpacing.lg))
                Button(onClick = onBack, modifier = Modifier.fillMaxWidth().height(48.dp), shape = RoundedCornerShape(12.dp)) {
                    Text("Try Again", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}
