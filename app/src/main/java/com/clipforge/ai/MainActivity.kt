package com.clipforge.ai

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.rememberNavController
import com.clipforge.ai.core.network.NetworkResult
import com.clipforge.ai.core.designsystem.AppColors
import com.clipforge.ai.core.designsystem.ClipForgeTheme
import com.clipforge.ai.presentation.navigation.AppNavGraph
import kotlinx.coroutines.launch

private const val TAG = "MainActivity"

class MainActivity : ComponentActivity() {
    private val authManager get() = (application as ClipForgeApp).authManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ClipForgeTheme {
                Surface(modifier = Modifier.fillMaxSize(), color = AppColors.Background) {
                    AppNavGraph(navController = rememberNavController())
                }
            }
        }
        intent?.data?.let { handleDeepLink(it) }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        intent.data?.let { handleDeepLink(it) }
    }

    private fun handleDeepLink(uri: Uri) {
        if (uri.scheme != "clipforgeai") return
        Log.d(TAG, "Deep link received: ${uri.safeAuthSummary()}")
        lifecycleScope.launch {
            when (val result = authManager.handleDeepLink(uri)) {
                is NetworkResult.Success -> Log.d(TAG, "Auth callback handled")
                is NetworkResult.Error -> Log.w(TAG, "Auth callback failed: ${result.message}")
                NetworkResult.Loading -> Unit
            }
        }
    }
}

private fun Uri.safeAuthSummary(): String {
    val raw = fragment?.takeIf { it.isNotBlank() } ?: query.orEmpty()
    return "scheme=$scheme host=$host accessToken=${raw.contains("access_token=")} " +
        "refreshToken=${raw.contains("refresh_token=")} code=${raw.contains("code=")} " +
        "error=${raw.contains("error=") || raw.contains("error_description=")}"
}
