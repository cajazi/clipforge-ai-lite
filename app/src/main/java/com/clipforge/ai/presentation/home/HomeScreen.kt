package com.clipforge.ai.presentation.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
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
fun HomeScreen(
    onCreateProject: () -> Unit,
    onOpenProject: (String) -> Unit,
    onSubscription: () -> Unit,
    onSettings: () -> Unit,
    onProjectHistory: () -> Unit,
    viewModel: HomeViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
        contentPadding = PaddingValues(bottom = 100.dp),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {

        // ── TOP BAR ───────────────────────────────────────────────────────
        item(key = "topbar") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.md)
                    .padding(top = AppSpacing.sm),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary))),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("CF", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White)
                    }
                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                    Column {
                        Text("ClipForge AI", fontWeight = FontWeight.Bold, color = AppColors.OnBackground, fontSize = 16.sp)
                        Text("AI Video Editor", fontSize = 11.sp, color = AppColors.TextSecondary)
                    }
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (!uiState.isPro) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary)))
                                .clickable(onClick = onSubscription)
                                .padding(horizontal = AppSpacing.md, vertical = 7.dp)
                        ) {
                            Text(
                                "\u2B50 Go Pro",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AppColors.SurfaceVariant)
                                .border(1.dp, AppColors.Accent.copy(alpha = 0.35f), CircleShape)
                                .padding(horizontal = AppSpacing.md, vertical = 7.dp)
                        ) {
                            Text("PRO", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = AppColors.Accent)
                        }
                    }
                    IconButton(onClick = onSettings, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings",
                            tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }

        // ── HERO BANNER ───────────────────────────────────────────────────
        item(key = "hero") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.md)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.linearGradient(listOf(AppColors.SurfaceVariant, AppColors.Surface, AppColors.Background))
                    )
                    .border(1.dp, AppColors.Border, RoundedCornerShape(20.dp))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(AppSpacing.lg),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AppColors.Primary.copy(alpha = 0.16f))
                                .border(1.dp, AppColors.Primary.copy(alpha = 0.35f), CircleShape)
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("\u2728 AI-Powered", fontSize = 11.sp, color = AppColors.Primary,
                                fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                        }
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(AppColors.Accent.copy(alpha = 0.15f))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text("Smart Edit", fontSize = 11.sp, color = AppColors.Accent,
                                fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                        }
                    }

                    Text(
                        "Create\nStunning Videos",
                        fontSize = 32.sp, fontWeight = FontWeight.Bold,
                        color = AppColors.OnBackground, lineHeight = 38.sp
                    )

                    Text(
                        "AI-assisted editing, transitions, overlays,\nmusic sync and 1080p export.",
                        fontSize = 14.sp, color = AppColors.TextSecondary, lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(AppSpacing.xs))

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary)))
                            .clickable(onClick = onCreateProject)
                            .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null,
                                tint = Color.White, modifier = Modifier.size(18.dp))
                            Text("New Project", fontWeight = FontWeight.Bold,
                                color = Color.White, fontSize = 15.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }

        // ── WHAT YOU CAN DO ───────────────────────────────────────────────
        item(key = "features_label") {
            Text(
                "What you can do",
                fontSize = 18.sp, color = AppColors.OnBackground, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = AppSpacing.md)
            )
        }

        item(key = "features_row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = AppSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                val features = listOf(
                    Triple("\u2702\uFE0F", "Smart\nTimeline",   AppColors.Primary),
                    Triple("\u2728",       "AI\nTransitions",   AppColors.Secondary),
                    Triple("\uD83C\uDFB5", "Music\nSync",       AppColors.Accent),
                    Triple("\uD83D\uDD24", "Text\nOverlay",     AppColors.Primary),
                    Triple("\uD83C\uDF9E\uFE0F", "1080p\nExport", AppColors.Secondary),
                    Triple("\uD83D\uDCCB", "Layer\nOverlays",   AppColors.Accent)
                )
                items(features, key = { it.second }) { (emoji, label, color) ->
                    Column(
                        modifier = Modifier
                            .width(104.dp)
                            .height(92.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.Surface)
                            .border(1.dp, AppColors.Border, RoundedCornerShape(12.dp))
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(emoji, fontSize = 22.sp, color = color)
                        Text(label, fontSize = 12.sp, color = AppColors.TextSecondary,
                            textAlign = TextAlign.Center, lineHeight = 15.sp)
                    }
                }
            }
        }

        // ── QUICK START ───────────────────────────────────────────────────
        item(key = "quickstart_label") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Quick Start", fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground)
                Text("Tap to create", fontSize = 12.sp, color = AppColors.TextSecondary)
            }
        }

        item(key = "quickstart_row") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = AppSpacing.md),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                val formats = listOf(
                    Triple("9:16", "TikTok\nReels",      listOf(AppColors.Primary, AppColors.Secondary)),
                    Triple("16:9", "YouTube\nWidescreen", listOf(AppColors.Secondary, AppColors.Primary)),
                    Triple("1:1",  "Instagram\nSquare",   listOf(AppColors.Primary, AppColors.Accent)),
                    Triple("4:5",  "Instagram\nFeed",     listOf(AppColors.Accent, AppColors.Primary))
                )
                items(formats, key = { it.first }) { (ratio, label, colors) ->
                    val (w, h) = when (ratio) {
                        "9:16" -> Pair(30f, 54f)
                        "16:9" -> Pair(54f, 30f)
                        "1:1"  -> Pair(40f, 40f)
                        else   -> Pair(40f, 50f)
                    }
                    Column(
                        modifier = Modifier
                            .width(116.dp)
                            .height(148.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(AppColors.Surface)
                            .border(1.dp, AppColors.Border, RoundedCornerShape(14.dp))
                            .clickable(onClick = onCreateProject)
                            .padding(AppSpacing.md),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                    ) {
                        Box(
                            modifier = Modifier
                                .width(w.dp)
                                .height(h.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Brush.linearGradient(colors))
                        )
                        Text(ratio, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground)
                        Text(label, fontSize = 12.sp, color = AppColors.TextSecondary,
                            textAlign = TextAlign.Center, lineHeight = 15.sp)
                    }
                }
            }
        }

        // ── RECENT PROJECTS HEADER ────────────────────────────────────────
        item(key = "projects_label") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (uiState.projects.isEmpty()) "Your Projects"
                    else "Recent Projects (${uiState.projects.size})",
                    fontSize = 18.sp, fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground
                )
                if (uiState.projects.isNotEmpty()) {
                    Row(
                        modifier = Modifier.clickable(onClick = onProjectHistory),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("See all", fontSize = 12.sp, color = AppColors.Primary)
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null,
                            tint = AppColors.Primary, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // ── PROJECTS / EMPTY / LOADING ────────────────────────────────────
        when {
            uiState.isLoading -> item(key = "loading") {
                Box(modifier = Modifier.fillMaxWidth().padding(AppSpacing.xl), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = AppColors.Primary, modifier = Modifier.size(32.dp))
                }
            }
            uiState.projects.isEmpty() -> item(key = "empty") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.md)
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.Surface)
                        .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
                        .clickable(onClick = onCreateProject)
                        .padding(AppSpacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
                    ) {
                        Box(
                            modifier = Modifier.size(48.dp).clip(CircleShape)
                                .background(AppColors.Primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null,
                                tint = AppColors.Primary, modifier = Modifier.size(24.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Create your first project", fontWeight = FontWeight.SemiBold,
                                color = AppColors.OnBackground, fontSize = 14.sp)
                            Text("Tap to start editing", color = AppColors.TextSecondary, fontSize = 12.sp)
                        }
                        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null,
                            tint = AppColors.TextSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
            else -> items(uiState.projects.take(5), key = { it.id }) { project ->
                ProjectListItem(
                    project  = project,
                    onClick  = { onOpenProject(project.id) },
                    onDelete = { viewModel.deleteProject(project.id) }
                )
            }
        }

        // ── PRO UPGRADE CARD ──────────────────────────────────────────────
        if (!uiState.isPro) {
            item(key = "pro_card") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AppSpacing.md)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Brush.linearGradient(listOf(AppColors.SurfaceVariant, AppColors.Surface)))
                        .border(1.dp, AppColors.Border, RoundedCornerShape(16.dp))
                        .clickable(onClick = onSubscription)
                        .padding(AppSpacing.md)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(
                            modifier = Modifier.weight(1f).padding(end = AppSpacing.sm),
                            verticalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Text("\u2B50 Upgrade to Pro",
                                fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground, fontSize = 14.sp,
                                maxLines = 1, softWrap = false)
                            Text("1080p \u2022 No watermark \u2022 No ads \u2022 All transitions",
                                color = AppColors.TextSecondary, fontSize = 12.sp,
                                maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Box(
                            modifier = Modifier
                                .widthIn(min = 90.dp)
                                .clip(CircleShape)
                                .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary)))
                                .padding(horizontal = AppSpacing.md, vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("See Plans", color = Color.White, fontWeight = FontWeight.Bold,
                                fontSize = 12.sp, maxLines = 1, softWrap = false)
                        }
                    }
                }
            }
        }
    }
}

// ── PROJECT LIST ITEM ─────────────────────────────────────────────────────────
@Composable
private fun ProjectListItem(project: Project, onClick: () -> Unit, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }
    val fmt = remember { SimpleDateFormat("MMM dd", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md)
            .clip(RoundedCornerShape(12.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Border, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(
                    Brush.linearGradient(listOf(
                        AppColors.Primary.copy(alpha = 0.6f),
                        AppColors.Secondary.copy(alpha = 0.6f)
                    ))
                ),
            contentAlignment = Alignment.Center
        ) { Text("\uD83C\uDFAC", fontSize = 22.sp) }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(project.title, fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground,
                fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AppColors.Primary.copy(alpha = 0.15f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(project.aspectRatio.label, fontSize = 10.sp,
                        color = AppColors.Primary, fontWeight = FontWeight.Medium)
                }
                Text(fmt.format(Date(project.updatedAt)),
                    fontSize = 11.sp, color = AppColors.TextSecondary)
            }
        }

        Box {
            IconButton(onClick = { showMenu = true }, modifier = Modifier.size(32.dp)) {
                Text("\u22EE", color = AppColors.TextSecondary, fontSize = 18.sp)
            }
            DropdownMenu(expanded = showMenu, onDismissRequest = { showMenu = false }) {
                DropdownMenuItem(text = { Text("Open") },  onClick = { showMenu = false; onClick() })
                DropdownMenuItem(
                    text    = { Text("Delete", color = AppColors.Error) },
                    onClick = { showMenu = false; onDelete() }
                )
            }
        }
    }
}
