package com.batmudcn

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.batmudcn.ui.screen.GameScreen
import com.batmudcn.ui.screen.SettingsScreen
import com.batmudcn.ui.theme.BatMudTheme
import com.batmudcn.viewmodel.GameViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Keep screen on while playing
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        setContent {
            BatMudTheme {
                var showSettings by remember { mutableStateOf(false) }
                val viewModel: GameViewModel = viewModel()

                // If first run, show settings first
                val settings by viewModel.settings.collectAsStateWithLifecycle()
                LaunchedEffect(settings.isFirstRun) {
                    if (settings.isFirstRun) {
                        showSettings = true
                    }
                }

                if (showSettings) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBack = {
                            showSettings = false
                            viewModel.completeFirstRun()
                        },
                    )
                } else {
                    GameScreen(
                        viewModel = viewModel,
                        onOpenSettings = { showSettings = true },
                    )
                }
            }
        }
    }
}
