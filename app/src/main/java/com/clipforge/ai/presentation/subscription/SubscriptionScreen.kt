package com.clipforge.ai.presentation.subscription

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionScreen(
    onBack: () -> Unit,
    viewModel: SubscriptionViewModel = viewModel()
) {
    val uiState  by viewModel.uiState.collectAsState()
    val activity = LocalContext.current as Activity

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Go Pro", color = AppColors.OnBackground, fontWeight = FontWeight.SemiBold) },
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
            modifier = Modifier
                .fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(AppSpacing.md),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Already Pro
            if (uiState.isPro) {
                Spacer(modifier = Modifier.height(AppSpacing.xxl))
                Box(
                    modifier = Modifier.size(100.dp).clip(RoundedCornerShape(24.dp))
                        .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary))),
                    contentAlignment = Alignment.Center
                ) { Text("\u2B50", fontSize = 48.sp) }
                Spacer(modifier = Modifier.height(AppSpacing.lg))
                Text("You are Pro!", fontSize = 26.sp, fontWeight = FontWeight.Black, color = AppColors.Primary)
                Text("All features are unlocked.", fontSize = 14.sp, color = AppColors.TextSecondary)
                Spacer(modifier = Modifier.height(AppSpacing.xl))
                Button(onClick = onBack, shape = RoundedCornerShape(12.dp)) {
                    Text("Back to App")
                }
                return@Column
            }

            // Hero
            Box(
                modifier = Modifier.size(90.dp).clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary))),
                contentAlignment = Alignment.Center
            ) { Text("\u2B50", fontSize = 40.sp) }

            Spacer(modifier = Modifier.height(AppSpacing.md))
            Text("ClipForge AI Pro", fontSize = 26.sp, fontWeight = FontWeight.Black, color = AppColors.OnBackground)
            Text("Unlock the full editor experience", fontSize = 14.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(AppSpacing.lg))

            // Features
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
            ) {
                Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                    listOf(
                        "\uD83C\uDF9E\uFE0F" to "1080p HD export quality",
                        "\uD83D\uDEAB" to "No watermark on videos",
                        "\uD83D\uDCF5" to "No ads — ever",
                        "\u2728"       to "All premium transitions",
                        "\u267E\uFE0F" to "Unlimited daily exports",
                        "\u26A1"       to "Priority render queue"
                    ).forEach { (emoji, text) ->
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                            Text(emoji, fontSize = 18.sp)
                            Text(text, fontSize = 14.sp, color = AppColors.OnBackground)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.lg))
            Text("Choose your plan", fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground, fontSize = 15.sp, modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(AppSpacing.sm))

            // Yearly
            PlanCard(
                planId    = "yearly",
                title     = "Yearly",
                price     = uiState.yearlyPrice,
                period    = "/ year",
                badge     = "BEST VALUE",
                subtext   = "Save over 50%",
                isSelected = uiState.selectedPlan == "yearly",
                onClick   = { viewModel.selectPlan("yearly") }
            )
            Spacer(modifier = Modifier.height(AppSpacing.sm))

            // Monthly
            PlanCard(
                planId    = "monthly",
                title     = "Monthly",
                price     = uiState.monthlyPrice,
                period    = "/ month",
                badge     = null,
                subtext   = "Billed monthly",
                isSelected = uiState.selectedPlan == "monthly",
                onClick   = { viewModel.selectPlan("monthly") }
            )

            Spacer(modifier = Modifier.height(AppSpacing.lg))

            // Subscribe button
            Button(
                onClick  = { viewModel.subscribe(activity) },
                enabled  = !uiState.isLoading,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary)), shape = RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text(
                            "Subscribe ${if (uiState.selectedPlan == "yearly") "Yearly" else "Monthly"}",
                            fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.sm))
            TextButton(onClick = viewModel::restorePurchases) {
                Text("Restore Purchases", color = AppColors.TextSecondary, fontSize = 13.sp)
            }

            uiState.errorMessage?.let {
                Text(it, color = AppColors.Error, fontSize = 12.sp, textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(AppSpacing.sm))
            Text(
                "Subscription renews automatically. Cancel anytime in Google Play.",
                fontSize = 11.sp, color = AppColors.TextSecondary, textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(AppSpacing.lg))
        }
    }
}

@Composable
private fun PlanCard(
    planId: String, title: String, price: String, period: String,
    badge: String?, subtext: String, isSelected: Boolean, onClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .border(
                width = if (isSelected) 2.dp else 1.dp,
                color = if (isSelected) AppColors.Primary else AppColors.SurfaceVariant,
                shape = RoundedCornerShape(14.dp)
            )
            .background(if (isSelected) AppColors.Primary.copy(alpha = 0.1f) else AppColors.Surface)
            .clickable(onClick = onClick)
            .padding(AppSpacing.md)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                RadioButton(selected = isSelected, onClick = onClick,
                    colors = RadioButtonDefaults.colors(selectedColor = AppColors.Primary))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        Text(title, fontWeight = FontWeight.SemiBold, color = AppColors.OnBackground, fontSize = 15.sp)
                        badge?.let {
                            Box(
                                modifier = Modifier.clip(RoundedCornerShape(4.dp))
                                    .background(AppColors.Accent)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) { Text(it, fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Black) }
                        }
                    }
                    Text(subtext, fontSize = 12.sp, color = AppColors.TextSecondary)
                }
            }
            Row(verticalAlignment = Alignment.Bottom) {
                Text(price, fontSize = 20.sp, fontWeight = FontWeight.Black,
                    color = if (isSelected) AppColors.Primary else AppColors.OnBackground)
                Text(period, fontSize = 12.sp, color = AppColors.TextSecondary, modifier = Modifier.padding(bottom = 2.dp))
            }
        }
    }
}
