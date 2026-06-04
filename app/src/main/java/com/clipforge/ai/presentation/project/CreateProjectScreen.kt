package com.clipforge.ai.presentation.project

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing
import com.clipforge.ai.domain.model.AspectRatio
import com.clipforge.ai.domain.model.ExportQuality
import com.clipforge.ai.domain.model.ProjectType
import com.clipforge.ai.domain.model.TransitionType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CreateProjectScreen(
    onProjectCreated: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: ProjectViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.createdProjectId) {
        uiState.createdProjectId?.let { onProjectCreated(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Create New Project", color = AppColors.OnBackground, fontWeight = FontWeight.SemiBold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = AppColors.OnBackground)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColors.Background)
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.Background)
                    .navigationBarsPadding()
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
            ) {
                uiState.error?.let {
                    Text(it, color = AppColors.Error, fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(bottom = AppSpacing.sm))
                }
                Button(
                    onClick        = viewModel::createProject,
                    enabled        = !uiState.isCreating,
                    modifier       = Modifier.fillMaxWidth().height(54.dp),
                    shape          = RoundedCornerShape(14.dp),
                    colors         = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize().background(
                            if (!uiState.isCreating)
                                Brush.linearGradient(listOf(AppColors.Primary, AppColors.PrimaryVariant))
                            else Brush.linearGradient(listOf(AppColors.SurfaceVariant, AppColors.SurfaceVariant)),
                            shape = RoundedCornerShape(14.dp)
                        ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.isCreating) {
                            Row(verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                CircularProgressIndicator(modifier = Modifier.size(20.dp),
                                    color = Color.White, strokeWidth = 2.dp)
                                Text("Creating...", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                            }
                        } else {
                            Text("Create Project", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White)
                        }
                    }
                }
            }
        },
        containerColor = AppColors.Background
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = AppSpacing.md, end = AppSpacing.md,
                top = AppSpacing.sm, bottom = AppSpacing.md),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {

            // ── 1. PROJECT NAME ───────────────────────────────────────────
            item(key = "name") {
                SectionCard("Project Name") {
                    OutlinedTextField(
                        value           = uiState.title,
                        onValueChange   = viewModel::onTitleChange,
                        placeholder     = { Text("e.g. My Travel Reel", color = AppColors.TextSecondary) },
                        isError         = uiState.titleError != null,
                        supportingText  = {
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(uiState.titleError ?: "", color = AppColors.Error, fontSize = 12.sp)
                                Text("${uiState.title.length}/50",
                                    color = if (uiState.title.length > 45) AppColors.Warning else AppColors.TextSecondary,
                                    fontSize = 12.sp)
                            }
                        },
                        singleLine      = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Sentences,
                            imeAction      = ImeAction.Next
                        ),
                        modifier        = Modifier.fillMaxWidth(),
                        shape           = RoundedCornerShape(12.dp),
                        colors          = outlinedTextFieldColors()
                    )
                }
            }

            // ── 2. PROJECT MODE ───────────────────────────────────────────
            item(key = "mode") {
                SectionCard("Project Mode") {
                    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        // Use a plain list of objects to avoid Triple limitation
                        val modeTypes  = listOf(ProjectType.MANUAL, ProjectType.AUTO_EDIT, ProjectType.TEMPLATE)
                        val modeEmojis = listOf("\u270D\uFE0F", "\u26A1", "\uD83C\uDFA8")
                        val modeLabels = listOf("Manual Edit", "Auto Edit", "Template")
                        val modeSubs   = listOf(
                            "Build your timeline step by step",
                            "AI builds timeline from your settings",
                            "Start from a pre-built structure"
                        )
                        modeTypes.forEachIndexed { index, type ->
                            val selected = uiState.projectType == type
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(
                                        if (selected) AppColors.Primary.copy(alpha = 0.10f)
                                        else AppColors.SurfaceVariant
                                    )
                                    .border(
                                        if (selected) 2.dp else 1.dp,
                                        if (selected) AppColors.Primary else Color.Transparent,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { viewModel.onProjectTypeSelected(type) }
                                    .padding(AppSpacing.md),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                            ) {
                                Text(modeEmojis[index], fontSize = 20.sp)
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(modeLabels[index], fontWeight = FontWeight.SemiBold,
                                        color = if (selected) AppColors.Primary else AppColors.OnBackground,
                                        fontSize = 14.sp)
                                    Text(modeSubs[index], fontSize = 11.sp, color = AppColors.TextSecondary)
                                }
                                RadioButton(
                                    selected = selected,
                                    onClick  = { viewModel.onProjectTypeSelected(type) },
                                    colors   = RadioButtonDefaults.colors(selectedColor = AppColors.Primary)
                                )
                            }
                        }
                    }
                }
            }

            // ── 3. AUTO EDIT SETTINGS (animated) ─────────────────────────
            item(key = "auto_settings") {
                AnimatedVisibility(
                    visible = uiState.projectType == ProjectType.AUTO_EDIT,
                    enter   = expandVertically(),
                    exit    = shrinkVertically()
                ) {
                    SectionCard("\u26A1 Auto Edit Settings") {
                        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {

                            // Final duration slider
                            Column {
                                Row(modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("Final Duration", fontSize = 13.sp,
                                        color = AppColors.OnBackground, fontWeight = FontWeight.Medium)
                                    Text("${uiState.autoFinalDuration}s",
                                        fontSize = 13.sp, color = AppColors.Primary, fontWeight = FontWeight.Bold)
                                }
                                Slider(
                                    value         = uiState.autoFinalDuration.toFloat(),
                                    onValueChange = { viewModel.onAutoFinalDurationChange(it.toInt()) },
                                    valueRange    = 5f..300f,
                                    colors        = SliderDefaults.colors(
                                        thumbColor = AppColors.Primary, activeTrackColor = AppColors.Primary)
                                )
                                Row(modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text("5s", fontSize = 10.sp, color = AppColors.TextSecondary)
                                    Text("300s", fontSize = 10.sp, color = AppColors.TextSecondary)
                                }
                            }

                            // Seconds per clip
                            OutlinedTextField(
                                value         = uiState.autoSecondsPerClipText,
                                onValueChange = viewModel::onAutoSecondsPerClipChange,
                                label         = { Text("Seconds per clip") },
                                placeholder   = { Text("e.g. 3", color = AppColors.TextSecondary) },
                                isError       = uiState.autoSecondsError != null,
                                supportingText = uiState.autoSecondsError?.let {
                                    { Text(it, color = AppColors.Error, fontSize = 12.sp) }
                                },
                                singleLine      = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier        = Modifier.fillMaxWidth(),
                                shape           = RoundedCornerShape(12.dp),
                                colors          = outlinedTextFieldColors(),
                                trailingIcon    = {
                                    val clips = if (uiState.autoSecondsPerClip > 0)
                                        uiState.autoFinalDuration / uiState.autoSecondsPerClip else 0
                                    if (clips > 0) {
                                        Text("~$clips clips", fontSize = 11.sp,
                                            color = AppColors.Primary,
                                            modifier = Modifier.padding(end = AppSpacing.sm))
                                    }
                                }
                            )

                            // Default transition
                            Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                Text("Default Transition", fontSize = 13.sp,
                                    color = AppColors.OnBackground, fontWeight = FontWeight.Medium)
                                val freeTransitions = TransitionType.values().filter { !it.isPremium }
                                Row(modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                                    freeTransitions.forEach { t ->
                                        val sel = uiState.autoTransition == t
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(8.dp))
                                                .background(
                                                    if (sel) AppColors.Primary.copy(alpha = 0.12f)
                                                    else AppColors.SurfaceVariant
                                                )
                                                .border(if (sel) 2.dp else 1.dp,
                                                    if (sel) AppColors.Primary else Color.Transparent,
                                                    RoundedCornerShape(8.dp))
                                                .clickable { viewModel.onAutoTransitionChange(t) }
                                                .padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(t.label, fontSize = 11.sp, maxLines = 1,
                                                color = if (sel) AppColors.Primary else AppColors.OnBackground,
                                                fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal)
                                        }
                                    }
                                }
                            }

                            // Music toggle
                            Row(modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically) {
                                Column {
                                    Text("Background Music", fontSize = 13.sp,
                                        color = AppColors.OnBackground, fontWeight = FontWeight.Medium)
                                    Text("Auto-sync music to clips",
                                        fontSize = 11.sp, color = AppColors.TextSecondary)
                                }
                                Switch(
                                    checked         = uiState.autoMusicEnabled,
                                    onCheckedChange = viewModel::onAutoMusicToggle,
                                    colors          = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = AppColors.Primary)
                                )
                            }

                            // Clip count info
                            val clipCount = if (uiState.autoSecondsPerClip > 0)
                                uiState.autoFinalDuration / uiState.autoSecondsPerClip else 0
                            if (clipCount > 0) {
                                Box(
                                    modifier = Modifier.fillMaxWidth()
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(AppColors.Primary.copy(alpha = 0.08f))
                                        .border(1.dp, AppColors.Primary.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
                                        .padding(AppSpacing.sm)
                                ) {
                                    Text(
                                        "\u2139\uFE0F  Auto Edit will use $clipCount clips \u00D7 ${uiState.autoSecondsPerClip}s = ${uiState.autoFinalDuration}s total",
                                        fontSize = 12.sp, color = AppColors.Primary, lineHeight = 16.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 4. ASPECT RATIO ───────────────────────────────────────────
            item(key = "ratio") {
                SectionCard("Aspect Ratio") {
                    val ratioList   = listOf(AspectRatio.RATIO_9_16, AspectRatio.RATIO_16_9, AspectRatio.RATIO_1_1, AspectRatio.RATIO_4_5)
                    val ratioLabels = listOf("9:16", "16:9", "1:1", "4:5")
                    val ratioSubs   = listOf("TikTok / Reels", "YouTube", "Instagram", "Feed")
                    val ratioPW     = listOf(18f, 32f, 24f, 24f)
                    val ratioPH     = listOf(32f, 18f, 24f, 30f)
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        ratioList.forEachIndexed { i, ratio ->
                            val sel = uiState.selectedRatio == ratio
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (sel) AppColors.Primary.copy(alpha = 0.12f) else AppColors.SurfaceVariant)
                                    .border(if (sel) 2.dp else 1.dp,
                                        if (sel) AppColors.Primary else Color.Transparent,
                                        RoundedCornerShape(12.dp))
                                    .clickable { viewModel.onRatioSelected(ratio) }
                                    .padding(vertical = AppSpacing.sm, horizontal = AppSpacing.xs),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
                            ) {
                                Box(modifier = Modifier.width(ratioPW[i].dp).height(ratioPH[i].dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(
                                        if (sel) Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary))
                                        else Brush.linearGradient(listOf(AppColors.Surface, AppColors.CardBackground))
                                    ))
                                Text(ratioLabels[i], fontSize = 13.sp, fontWeight = FontWeight.Bold,
                                    color = if (sel) AppColors.Primary else AppColors.OnBackground)
                                Text(ratioSubs[i], fontSize = 9.sp, color = AppColors.TextSecondary,
                                    textAlign = TextAlign.Center, lineHeight = 12.sp)
                            }
                        }
                    }
                }
            }

            // ── 5. OUTPUT QUALITY ─────────────────────────────────────────
            item(key = "quality") {
                SectionCard("Output Quality") {
                    Row(modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                        val qualities = listOf(ExportQuality.QUALITY_720P, ExportQuality.QUALITY_1080P)
                        val qLabels   = listOf("720p", "1080p")
                        val qIsPro    = listOf(false, true)
                        qualities.forEachIndexed { i, quality ->
                            val sel    = uiState.selectedQuality == quality
                            val locked = qIsPro[i] && !uiState.isPro
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(when {
                                        sel    -> AppColors.Primary.copy(alpha = 0.12f)
                                        locked -> AppColors.SurfaceVariant.copy(alpha = 0.5f)
                                        else   -> AppColors.SurfaceVariant
                                    })
                                    .border(if (sel) 2.dp else 1.dp,
                                        if (sel) AppColors.Primary else Color.Transparent,
                                        RoundedCornerShape(12.dp))
                                    .clickable(enabled = !locked) { viewModel.onQualitySelected(quality) }
                                    .padding(AppSpacing.md)
                            ) {
                                Column(modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                    Row(verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                                        Text(qLabels[i], fontSize = 18.sp, fontWeight = FontWeight.Black,
                                            color = when { sel -> AppColors.Primary; locked -> AppColors.TextSecondary; else -> AppColors.OnBackground })
                                        if (locked) Icon(Icons.Default.Lock, contentDescription = "Pro",
                                            tint = AppColors.Warning, modifier = Modifier.size(14.dp))
                                    }
                                    if (locked) {
                                        Box(modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                            .background(AppColors.Warning.copy(alpha = 0.15f))
                                            .padding(horizontal = 8.dp, vertical = 2.dp)) {
                                            Text("PRO", fontSize = 10.sp, color = AppColors.Warning, fontWeight = FontWeight.Black)
                                        }
                                    } else {
                                        Text(if (quality == ExportQuality.QUALITY_720P) "Free" else "Pro",
                                            fontSize = 11.sp, color = AppColors.TextSecondary)
                                    }
                                }
                            }
                        }
                    }
                    if (!uiState.isPro) {
                        Spacer(modifier = Modifier.height(AppSpacing.xs))
                        Text("\uD83D\uDD12 Upgrade to Pro to unlock 1080p export",
                            fontSize = 12.sp, color = AppColors.TextSecondary,
                            modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                    }
                }
            }
        }
    }
}

// ── HELPERS ───────────────────────────────────────────────────────────────────

@Composable
private fun outlinedTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor      = AppColors.Primary,
    unfocusedBorderColor    = AppColors.SurfaceVariant,
    errorBorderColor        = AppColors.Error,
    focusedTextColor        = AppColors.OnBackground,
    unfocusedTextColor      = AppColors.OnBackground,
    focusedLabelColor       = AppColors.Primary,
    cursorColor             = AppColors.Primary,
    focusedContainerColor   = AppColors.Surface,
    unfocusedContainerColor = AppColors.Surface,
    errorContainerColor     = AppColors.Error.copy(alpha = 0.05f)
)

@Composable
private fun SectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(title, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground)
        Card(
            modifier  = Modifier.fillMaxWidth(),
            shape     = RoundedCornerShape(14.dp),
            colors    = CardDefaults.cardColors(containerColor = AppColors.Surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(AppSpacing.md), content = content)
        }
    }
}
