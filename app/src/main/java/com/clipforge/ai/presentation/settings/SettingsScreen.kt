package com.clipforge.ai.presentation.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowRight
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
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = AppColors.OnBackground, fontWeight = FontWeight.SemiBold) },
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
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
        ) {

            val glTestCtx = LocalContext.current
            Button(
                onClick = {
                    com.clipforge.ai.core.gl.GlExportTest.runTransformerTest(
                        glTestCtx,
                        android.net.Uri.fromFile(java.io.File(glTestCtx.getExternalFilesDir(null), "input_test.mp4"))
                    ) { ok, msg -> android.util.Log.d("GL_EXPORT_TEST", "result ok=$ok $msg") }
                },
                modifier = Modifier.padding(16.dp)
            ) { Text("GL export test") }

            Button(
                onClick = {
                    val ctx = glTestCtx
                    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.Main) {
                        val projectId = "6bda9039-9806-4e7c-bf64-af949540a4f1"
                        val paths = com.clipforge.ai.core.gl.ProjectExporter.resolveAllVideoPaths(ctx, projectId)
                        com.clipforge.ai.core.gl.CrossfadeRenderPlan.build(ctx, projectId)
                        if (paths.size < 2) {
                            android.widget.Toast.makeText(ctx, "Need 2+ clips (got ${paths.size})", android.widget.Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        val durUs = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                            val mmr = android.media.MediaMetadataRetriever()
                            try {
                                mmr.setDataSource(paths[0])
                                val ms = mmr.extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                                ms * 1000L
                            } catch (e: Exception) { 0L } finally { mmr.release() }
                        }
                        android.util.Log.d("CROSSFADE_TEST", "clipA durUs=$durUs pathA=${paths[0]} pathB=${paths[1]}")
                        if (durUs <= 0L) {
                            android.widget.Toast.makeText(ctx, "Could not read clip A duration", android.widget.Toast.LENGTH_LONG).show()
                            return@launch
                        }
                        com.clipforge.ai.core.gl.CrossfadeExecutor.renderProjectDissolvePair(
                            context = ctx,
                            projectId = projectId,
                            onProgress = { pct -> android.util.Log.d("CROSSFADE_TEST", "progress=$pct") },
                            onResult = { r ->
                                android.util.Log.d("CROSSFADE_TEST", "result=$r")
                                android.widget.Toast.makeText(ctx, "Crossfade: $r", android.widget.Toast.LENGTH_LONG).show()
                            }
                        )
                    }
                },
                modifier = Modifier.padding(16.dp)
            ) { Text("Crossfade test") }

            // Account / Pro status card
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(AppSpacing.md)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (uiState.isPro)
                            Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary))
                        else Brush.linearGradient(listOf(AppColors.Surface, AppColors.SurfaceVariant))
                    )
                    .padding(AppSpacing.md)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            if (uiState.isPro) "ClipForge Pro" else "Free Plan",
                            fontWeight = FontWeight.Bold, color = Color.White, fontSize = 18.sp
                        )
                        Text(
                            if (uiState.isPro) "All features unlocked" else "3 exports/day \u2022 720p \u2022 Watermark",
                            color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp
                        )
                    }
                    if (!uiState.isPro) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.2f))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) { Text("Upgrade", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
                    } else {
                        Text("\u2B50", fontSize = 32.sp)
                    }
                }
            }

            SettingsSection("Export") {
                SettingsDropdownRow(
                    label = "Default Quality",
                    value = uiState.defaultQuality,
                    options = if (uiState.isPro) listOf("720p", "1080p") else listOf("720p"),
                    onSelect = { viewModel.setDefaultQuality(it) }
                )
                SettingsDivider()
                SettingsInfoRow("Daily Export Limit", if (uiState.isPro) "Unlimited" else "3 / day")
                SettingsDivider()
                SettingsInfoRow("Watermark", if (uiState.isPro) "Disabled" else "Enabled (Free)")
            }

            SettingsSection("Notifications") {
                SettingsSwitchRow(
                    label = "Export Notifications",
                    subtext = "Notify when render is complete",
                    checked = uiState.notificationsEnabled,
                    onToggle = { viewModel.toggleNotifications(it) }
                )
            }

            SettingsSection("About") {
                SettingsInfoRow("App Version", uiState.appVersion)
                SettingsDivider()
                SettingsLinkRow("Privacy Policy") { }
                SettingsDivider()
                SettingsLinkRow("Terms of Service") { }
                SettingsDivider()
                SettingsLinkRow("Send Feedback") { }
            }

            SettingsSection("Account") {
                SettingsActionRow(
                    label = if (uiState.isLoggingOut) "Logging out..." else "Log Out",
                    enabled = !uiState.isLoggingOut,
                    color = AppColors.Error,
                    onClick = { viewModel.logout(onLoggedOut) }
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.xxxl))
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = AppSpacing.md)) {
        Text(
            title.uppercase(),
            fontSize = 11.sp, color = AppColors.TextSecondary,
            fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
        )
        Card(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
        ) {
            Column(content = content)
        }
    }
}

@Composable
private fun SettingsInfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppColors.OnBackground, fontSize = 15.sp)
        Text(value, color = AppColors.TextSecondary, fontSize = 14.sp)
    }
}

@Composable
private fun SettingsLinkRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = AppSpacing.md, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AppColors.OnBackground, fontSize = 15.sp)
        Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun SettingsActionRow(
    label: String,
    enabled: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = AppSpacing.md, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = color, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        if (enabled) {
            Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
        } else {
            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = color)
        }
    }
}

@Composable
private fun SettingsSwitchRow(label: String, subtext: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, color = AppColors.OnBackground, fontSize = 15.sp)
            Text(subtext, color = AppColors.TextSecondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onToggle,
            colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = AppColors.Primary)
        )
    }
}

@Composable
private fun SettingsDropdownRow(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier.fillMaxWidth().clickable { expanded = true }.padding(horizontal = AppSpacing.md, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, color = AppColors.OnBackground, fontSize = 15.sp)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(value, color = AppColors.Primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Icon(Icons.Default.KeyboardArrowRight, contentDescription = null, tint = AppColors.TextSecondary, modifier = Modifier.size(16.dp))
            }
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = { onSelect(option); expanded = false }
                )
            }
        }
    }
}

@Composable
private fun SettingsDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = AppSpacing.md), color = AppColors.SurfaceVariant, thickness = 0.5.dp)
}
