package com.focuscoin.app

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle

internal val LightColors = lightColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFF6557C8),
    onPrimary = androidx.compose.ui.graphics.Color.White,
    secondary = androidx.compose.ui.graphics.Color(0xFF168C83),
    tertiary = androidx.compose.ui.graphics.Color(0xFFE8AB32),
    background = androidx.compose.ui.graphics.Color(0xFFF7F6FC),
    surface = androidx.compose.ui.graphics.Color.White
)
internal val DarkColors = darkColorScheme(
    primary = androidx.compose.ui.graphics.Color(0xFFB6ABFF),
    secondary = androidx.compose.ui.graphics.Color(0xFF73D5C9),
    tertiary = androidx.compose.ui.graphics.Color(0xFFFFD16E),
    background = androidx.compose.ui.graphics.Color(0xFF12121A),
    surface = androidx.compose.ui.graphics.Color(0xFF1D1D28)
)

class MainActivity : ComponentActivity() {
    private val app by lazy { application as FocusCoinApplication }
    private val viewModel: FocusCoinViewModel by viewModels {
        FocusCoinViewModelFactory(app, app.repository)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MaterialTheme(colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors) {
                val state by viewModel.uiState.collectAsStateWithLifecycle()
                var permissionRefresh by remember { mutableIntStateOf(0) }
                DisposableEffect(lifecycle) {
                    val observer = LifecycleEventObserver { _, event ->
                        if (event == Lifecycle.Event.ON_RESUME) permissionRefresh++
                    }
                    lifecycle.addObserver(observer)
                    onDispose { lifecycle.removeObserver(observer) }
                }
                val notificationLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { permissionRefresh++ }
                FocusCoinRoot(
                    state = state,
                    viewModel = viewModel,
                    permissionRefresh = permissionRefresh,
                    startWithTimer = intent.getBooleanExtra("focuscoin.start_study", false),
                    requestNotifications = {
                        if (Build.VERSION.SDK_INT >= 33) notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                    }
                )
            }
        }
    }
}
