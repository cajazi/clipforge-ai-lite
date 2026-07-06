package com.clipforge.ai.presentation.navigation

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.*
import androidx.navigation.compose.*
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.core.auth.AuthState
import com.clipforge.ai.presentation.auth.ForgotPasswordScreen
import com.clipforge.ai.presentation.auth.LoginScreen
import com.clipforge.ai.presentation.auth.RegisterScreen
import com.clipforge.ai.presentation.export.ExportProgressScreen
import com.clipforge.ai.presentation.history.ProjectHistoryScreen
import com.clipforge.ai.presentation.home.HomeScreen
import com.clipforge.ai.presentation.music.MusicScreen
import com.clipforge.ai.presentation.overlays.OverlayScreen
import com.clipforge.ai.presentation.preview.PreviewScreen
import com.clipforge.ai.presentation.project.CreateProjectScreen
import com.clipforge.ai.presentation.settings.SettingsScreen
import com.clipforge.ai.presentation.splash.SplashScreen
import com.clipforge.ai.presentation.subscription.SubscriptionScreen
import com.clipforge.ai.presentation.text.TextOverlayScreen
import com.clipforge.ai.presentation.timeline.TimelineScreen
import com.clipforge.ai.presentation.transitions.TransitionScreen
import com.clipforge.ai.presentation.upload.MediaImportScreen
import com.clipforge.ai.presentation.upload.UploadMediaScreen

private const val TAG = "AppNavGraph"

@Composable
fun AppNavGraph(navController: NavHostController) {
    val app = LocalContext.current.applicationContext as ClipForgeApp
    val authState by app.authManager.authState.collectAsState()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    LaunchedEffect(authState, currentRoute) {
        if (
            authState is AuthState.LoggedIn &&
            (currentRoute == Routes.LOGIN || currentRoute == Routes.REGISTER)
        ) {
            navController.navigate(Routes.HOME) {
                popUpTo(Routes.LOGIN) { inclusive = true }
                launchSingleTop = true
            }
        }
    }

    NavHost(navController = navController, startDestination = Routes.SPLASH) {

        composable(Routes.SPLASH) {
            SplashScreen(
                onHome  = { navController.navigate(Routes.HOME)  { popUpTo(Routes.SPLASH) { inclusive = true } } },
                onLogin = { navController.navigate(Routes.LOGIN) { popUpTo(Routes.SPLASH) { inclusive = true } } }
            )
        }
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess   = { navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                onRegister       = { navController.navigate(Routes.REGISTER) },
                onForgotPassword = { navController.navigate(Routes.FORGOT_PASSWORD) }
            )
        }
        composable(Routes.REGISTER) {
            RegisterScreen(
                onRegisterSuccess = { navController.navigate(Routes.HOME) { popUpTo(Routes.LOGIN) { inclusive = true } } },
                onBack            = { navController.popBackStack() }
            )
        }
        composable(Routes.FORGOT_PASSWORD) {
            ForgotPasswordScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.HOME) {
            HomeScreen(
                onCreateProject  = { navController.navigate(Routes.CREATE_PROJECT) },
                onOpenProject    = { navController.navigate(Routes.timeline(it)) },
                onSubscription   = { navController.navigate(Routes.SUBSCRIPTION) },
                onSettings       = { navController.navigate(Routes.SETTINGS) },
                onProjectHistory = { navController.navigate(Routes.PROJECT_HISTORY) }
            )
        }
        composable(Routes.CREATE_PROJECT) {
            CreateProjectScreen(
                onProjectCreated = { id -> navController.navigate(Routes.mediaImport(id)) { popUpTo(Routes.HOME) } },
                onBack           = { navController.popBackStack() }
            )
        }
        composable(Routes.MEDIA_IMPORT, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { bs ->
            val id = bs.arguments?.getString("projectId") ?: return@composable
            MediaImportScreen(
                projectId = id,
                onContinue = {
                    Log.d(TAG, "Navigating to Timeline projectId=$id")
                    navController.navigate(Routes.timeline(id))
                },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.TIMELINE, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { bs ->
            val id = bs.arguments?.getString("projectId") ?: return@composable
            TimelineScreen(
                projectId      = id,
                onBack         = { navController.popBackStack() },
                onAddText      = { navController.navigate(Routes.textOverlay(id)) },
                onExport       = { navController.navigate(Routes.exportProgress(id)) { launchSingleTop = true } },
                onAddMusic     = { navController.navigate(Routes.music(id)) },
                onAddOverlay   = { navController.navigate(Routes.overlays(id)) },
                onAddTransition = {
                    Log.d(TAG, "TransitionPicker opened from Timeline projectId=$id applyToAll=true")
                    navController.navigate(Routes.transitionPicker(id))
                },
                onEditTransition = { clipId ->
                    Log.d(TAG, "TransitionPicker opened from Timeline projectId=$id clipId=$clipId")
                    navController.navigate(Routes.transitionPicker(id, clipId))
                }
            )
        }
        composable(Routes.UPLOAD_MEDIA, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { bs ->
            val id = bs.arguments?.getString("projectId") ?: return@composable
            UploadMediaScreen(projectId = id, onNext = { navController.navigate(Routes.timeline(id)) }, onBack = { navController.popBackStack() })
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { bs ->
            val id = bs.arguments?.getString("projectId") ?: return@composable
            TimelineScreen(
                projectId      = id,
                onBack         = { navController.popBackStack() },
                onAddText      = { navController.navigate(Routes.textOverlay(id)) },
                onExport       = { navController.navigate(Routes.exportProgress(id)) { launchSingleTop = true } },
                onAddMusic     = { navController.navigate(Routes.music(id)) },
                onAddOverlay   = { navController.navigate(Routes.overlays(id)) },
                onAddTransition = { navController.navigate(Routes.transitionPicker(id)) },
                onEditTransition = { clipId -> navController.navigate(Routes.transitionPicker(id, clipId)) }
            )
        }
        composable(
            Routes.TRANSITION_PICKER,
            arguments = listOf(navArgument("projectId") { type = NavType.StringType })
        ) { bs ->
            TransitionScreen(
                projectId = bs.arguments?.getString("projectId") ?: return@composable,
                onBack = { navController.popBackStack() },
                onApplied = {
                    Log.d(TAG, "Returning to Timeline")
                    navController.popBackStack()
                }
            )
        }
        composable(
            Routes.TRANSITION_PICKER_FOR_CLIP,
            arguments = listOf(
                navArgument("projectId") { type = NavType.StringType },
                navArgument("clipId") { type = NavType.StringType }
            )
        ) { bs ->
            TransitionScreen(
                projectId = bs.arguments?.getString("projectId") ?: return@composable,
                clipId = bs.arguments?.getString("clipId") ?: return@composable,
                onBack = { navController.popBackStack() },
                onApplied = {
                    Log.d(TAG, "Returning to Timeline")
                    navController.popBackStack()
                }
            )
        }
        composable(Routes.OVERLAYS, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { bs ->
            OverlayScreen(projectId = bs.arguments?.getString("projectId") ?: return@composable, onBack = { navController.popBackStack() }) }
        composable(Routes.TEXT_OVERLAY, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { bs ->
            TextOverlayScreen(projectId = bs.arguments?.getString("projectId") ?: return@composable, onBack = { navController.popBackStack() }) }
        composable(Routes.MUSIC, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { bs ->
            MusicScreen(projectId = bs.arguments?.getString("projectId") ?: return@composable, onBack = { navController.popBackStack() }) }
        composable(Routes.PREVIEW, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { bs ->
            PreviewScreen(projectId = bs.arguments?.getString("projectId") ?: return@composable, onBack = { navController.popBackStack() }) }
        composable(Routes.EXPORT_PROGRESS, arguments = listOf(navArgument("projectId") { type = NavType.StringType })) { bs ->
            ExportProgressScreen(projectId = bs.arguments?.getString("projectId") ?: return@composable,
                onDone = { navController.navigate(Routes.HOME) { popUpTo(Routes.HOME) { inclusive = true } } },
                onBack = { navController.popBackStack() }) }
        composable(Routes.PROJECT_HISTORY) {
            ProjectHistoryScreen(onOpenProject = { navController.navigate(Routes.timeline(it)) }, onBack = { navController.popBackStack() }) }
        composable(Routes.SUBSCRIPTION) { SubscriptionScreen(onBack = { navController.popBackStack() }) }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onBack = { navController.popBackStack() },
                onLoggedOut = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.HOME) { inclusive = true }
                        launchSingleTop = true
                    }
                }
            )
        }
    }
}
