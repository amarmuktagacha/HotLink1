package com.shohan.hotlink

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.shohan.hotlink.ui.screens.HostScreen
import com.shohan.hotlink.ui.screens.RoleSelectScreen
import com.shohan.hotlink.ui.screens.ViewerScreen
import com.shohan.hotlink.ui.theme.HotLinkTheme

class MainActivity : ComponentActivity() {

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { /* no-op either way */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            HotLinkTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HotLinkNavHost()
                }
            }
        }
    }
}

@Composable
fun HotLinkNavHost(navController: NavHostController = rememberNavController()) {
    NavHost(navController = navController, startDestination = "role_select") {
        composable("role_select") {
            RoleSelectScreen(
                onHostSelected = { navController.navigate("host") },
                onViewerSelected = { navController.navigate("viewer") }
            )
        }
        composable("host") {
            HostScreen(onBack = { navController.popBackStack() })
        }
        composable("viewer") {
            ViewerScreen(onBack = { navController.popBackStack() })
        }
    }
}
