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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.MailOutline
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.clipforge.ai.core.designsystem.AppColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegisterScreen(
    onRegisterSuccess: () -> Unit,
    onBack: () -> Unit,
    viewModel: RegisterViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showPassword by remember { mutableStateOf(false) }
    var showConfirm  by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.isSuccess) { if (uiState.isSuccess) onRegisterSuccess() }

    Box(modifier = Modifier.fillMaxSize().background(AppColors.Background)
        .windowInsetsPadding(WindowInsets.safeDrawing)) {

        // Email confirmation success screen
        if (uiState.needsConfirmation) {
            Column(modifier = Modifier.fillMaxSize().padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center) {
                Text("\uD83D\uDCE7", fontSize = 64.sp)
                Spacer(Modifier.height(20.dp))
                Text("Check your email", fontSize = 24.sp, fontWeight = FontWeight.SemiBold,
                    color = AppColors.OnBackground)
                Spacer(Modifier.height(10.dp))
                Text("Account created! Please check your email\nand click the confirmation link\nbefore signing in.",
                    fontSize = 15.sp, color = AppColors.TextSecondary,
                    textAlign = TextAlign.Center, lineHeight = 22.sp)
                Spacer(Modifier.height(32.dp))
                PremiumButton(text = "Back to Sign In", isLoading = false,
                    enabled = true, onClick = onBack)
            }
            return@Box
        }

        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 28.dp), horizontalAlignment = Alignment.CenterHorizontally) {

            Spacer(Modifier.height(20.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Back", tint = AppColors.OnBackground)
                }
            }
            Box(modifier = Modifier.size(72.dp).background(
                Brush.linearGradient(listOf(AppColors.Primary, AppColors.Secondary)),
                RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                Text("CF", fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            }
            Spacer(Modifier.height(16.dp))
            Text("Create your account", fontSize = 32.sp, fontWeight = FontWeight.Bold,
                color = AppColors.OnBackground)
            Spacer(Modifier.height(6.dp))
            Text("Start editing videos with AI tools", fontSize = 14.sp,
                color = AppColors.TextSecondary, textAlign = TextAlign.Center)
            Spacer(Modifier.height(28.dp))

            OutlinedTextField(value = uiState.name, onValueChange = viewModel::onNameChange,
                label = { Text("Full name") }, isError = uiState.nameError != null,
                supportingText = uiState.nameError?.let { { Text(it, color = AppColors.Error, fontSize = 12.sp) } },
                leadingIcon = { Icon(Icons.Default.Person, null, tint = AppColors.TextSecondary) },
                singleLine = true, keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = premiumFieldColors())
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(value = uiState.email, onValueChange = viewModel::onEmailChange,
                label = { Text("Email address") }, isError = uiState.emailError != null,
                supportingText = uiState.emailError?.let { { Text(it, color = AppColors.Error, fontSize = 12.sp) } },
                leadingIcon = { Icon(Icons.Default.MailOutline, null, tint = AppColors.TextSecondary) },
                singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = premiumFieldColors())
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(value = uiState.password, onValueChange = viewModel::onPasswordChange,
                label = { Text("Password") }, isError = uiState.passwordError != null,
                supportingText = uiState.passwordError?.let { { Text(it, color = AppColors.Error, fontSize = 12.sp) } },
                singleLine = true, visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Next),
                trailingIcon = { TextButton(onClick = { showPassword = !showPassword }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(if (showPassword) "Hide" else "Show", fontSize = 12.sp, color = AppColors.Primary) } },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = premiumFieldColors())
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(value = uiState.confirmPassword, onValueChange = viewModel::onConfirmPasswordChange,
                label = { Text("Confirm password") }, isError = uiState.confirmError != null,
                supportingText = uiState.confirmError?.let { { Text(it, color = AppColors.Error, fontSize = 12.sp) } },
                singleLine = true, visualTransformation = if (showConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                trailingIcon = { TextButton(onClick = { showConfirm = !showConfirm }, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(if (showConfirm) "Hide" else "Show", fontSize = 12.sp, color = AppColors.Primary) } },
                modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(18.dp), colors = premiumFieldColors())

            if (uiState.error != null) {
                Spacer(Modifier.height(10.dp))
                Text(uiState.error!!, color = AppColors.Error, fontSize = 13.sp, textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().background(AppColors.Surface, RoundedCornerShape(12.dp)).padding(12.dp))
            }

            Spacer(Modifier.height(20.dp))
            PremiumButton(text = "Create Account", isLoading = uiState.isLoading, enabled = !uiState.isLoading, onClick = viewModel::register)
            Spacer(Modifier.height(20.dp))
            AuthDivider()
            Spacer(Modifier.height(20.dp))
            GoogleSignInButton(onClick = {
                viewModel.getGoogleOAuthUrl()?.let { url ->
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                }
            })
            Spacer(Modifier.height(28.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Already have an account?  ", color = AppColors.TextSecondary, fontSize = 15.sp)
                Text("Sign in", color = AppColors.Primary, fontWeight = FontWeight.Bold,
                    fontSize = 15.sp, modifier = Modifier.clickable(onClick = onBack))
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}
