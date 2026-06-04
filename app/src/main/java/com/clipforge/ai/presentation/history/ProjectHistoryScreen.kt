package com.clipforge.ai.presentation.history

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.clipforge.ai.domain.model.Project
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProjectHistoryScreen(
    onOpenProject: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ProjectHistoryViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val fmt = remember { SimpleDateFormat("MMM dd, yyyy", Locale.getDefault()) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Project History", color = AppColors.OnBackground, fontWeight = FontWeight.SemiBold) },
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
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = AppColors.Primary)
            }
        } else if (uiState.projects.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("\uD83D\uDCC1", fontSize = 48.sp)
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                    Text("No projects yet", color = AppColors.TextSecondary, fontWeight = FontWeight.Medium)
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                item {
                    Text("${uiState.projects.size} project(s)",
                        color = AppColors.TextSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = AppSpacing.sm))
                }
                items(uiState.projects, key = { it.id }) { project ->
                    HistoryProjectCard(
                        project = project,
                        dateStr = fmt.format(Date(project.updatedAt)),
                        onClick = { onOpenProject(project.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun HistoryProjectCard(project: Project, dateStr: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.CardBackground)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(AppSpacing.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            Box(
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(10.dp))
                    .background(Brush.linearGradient(listOf(AppColors.Primary.copy(alpha = 0.7f), AppColors.Secondary.copy(alpha = 0.7f)))),
                contentAlignment = Alignment.Center
            ) { Text("\uD83C\uDFAC", fontSize = 24.sp) }

            Column(modifier = Modifier.weight(1f)) {
                Text(project.title, fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground,
                    fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(modifier = Modifier.height(2.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .background(AppColors.Primary.copy(alpha = 0.2f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text(project.aspectRatio.label, fontSize = 11.sp, color = AppColors.Primary, fontWeight = FontWeight.Medium) }
                    Box(
                        modifier = Modifier.clip(RoundedCornerShape(4.dp))
                            .background(AppColors.SurfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) { Text(project.exportQuality.label, fontSize = 11.sp, color = AppColors.TextSecondary) }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text("Edited $dateStr", fontSize = 12.sp, color = AppColors.TextSecondary)
            }

            Text(">", color = AppColors.TextSecondary, fontSize = 18.sp)
        }
    }
}
