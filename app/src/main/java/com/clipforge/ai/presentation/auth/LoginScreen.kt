package com.clipforge.ai.presentation.auth

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipforge.ai.core.designsystem.AppColors

@Composable
fun LoginScreen(
    onLoginSuccess: () -> Unit,
    onRegister: () -> Unit,
    onForgotPassword: () -> Unit,
    viewModel: LoginViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSuccess) { if (uiState.isSuccess) onLoginSuccess() }

    Box(
        modifier = Modifier.fillMaxSize()
            .background(AppColors.Background)
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(56.dp))

            // Logo
            Box(
                modifier = Modifier.size(80.dp)
                    .background(
                        Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary)),
                        RoundedCornerShape(22.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text("CF", fontSize = 32.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }

            Spacer(Modifier.height(20.dp))
            Text("Welcome back", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                color = AppColors.OnBackground)
            Spacer(Modifier.height(6.dp))
            Text("Create stunning videos faster", fontSize = 14.sp,
                color = AppColors.TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))

            // Email field
            OutlinedTextField(
                value         = uiState.email,
                onValueChange = viewModel::onEmailChange,
                label         = { Text("Email address") },
                isError       = uiState.emailError != null,
                supportingText = uiState.emailError?.let {
                    { Text(it, color = AppColors.Error, fontSize = 12.sp) }
                },
                leadingIcon   = { Icon(Icons.Default.MailOutline, null, tint = AppColors.TextSecondary) },
                singleLine    = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(18.dp),
                colors        = premiumFieldColors()
            )

            Spacer(Modifier.height(12.dp))

            // Password field — text Show/Hide toggle
            OutlinedTextField(
                value         = uiState.password,
                onValueChange = viewModel::onPasswordChange,
                label         = { Text("Password") },
                isError       = uiState.passwordError != null,
                supportingText = uiState.passwordError?.let {
                    { Text(it, color = AppColors.Error, fontSize = 12.sp) }
                },
                singleLine    = true,
                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon  = {
                    TextButton(onClick = { showPassword = !showPassword },
                        contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(if (showPassword) "Hide" else "Show",
                            fontSize = 12.sp, color = AppColors.Primary)
                    }
                },
                modifier      = Modifier.fillMaxWidth(),
                shape         = RoundedCornerShape(18.dp),
                colors        = premiumFieldColors()
            )

            // Forgot password
            Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                Text("Forgot password?", fontSize = 13.sp, color = AppColors.Primary,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onForgotPassword).padding(vertical = 10.dp))
            }

            // Error
            if (uiState.error != null) {
                Spacer(Modifier.height(4.dp))
                Text(uiState.error!!, color = AppColors.Error, fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                        .background(AppColors.Surface, RoundedCornerShape(12.dp))
                        .padding(10.dp))
            }

            Spacer(Modifier.height(20.dp))
            PremiumButton(text = "Sign In", isLoading = uiState.isLoading,
                enabled = !uiState.isLoading, onClick = viewModel::login)
            Spacer(Modifier.height(20.dp))
            AuthDivider()
            Spacer(Modifier.height(20.dp))
            GoogleSignInButton(onClick = {
                viewModel.getGoogleOAuthUrl()?.let { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            })
            Spacer(Modifier.height(32.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Don't have an account?  ", color = AppColors.TextSecondary, fontSize = 15.sp)
                Text("Create one", color = AppColors.Primary, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, modifier = Modifier.clickable(onClick = onRegister))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
