package com.nexappra.filerecovery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nexappra.filerecovery.core.designsystem.theme.FileRecoveryTheme
import com.nexappra.filerecovery.domain.model.AppThemeMode
import com.nexappra.filerecovery.presentation.app.AppViewModel
import com.nexappra.filerecovery.presentation.navigation.AppNavHost
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(
        savedInstanceState: Bundle?,
    ) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContent {
            FileRecoveryApp()
        }
    }
}

@Composable
private fun FileRecoveryApp() {

    val viewModel: AppViewModel = hiltViewModel()

    val themeMode by viewModel.themeMode
        .collectAsStateWithLifecycle()

    val darkTheme = when (themeMode) {

        AppThemeMode.SYSTEM -> {
            isSystemInDarkTheme()
        }

        AppThemeMode.LIGHT -> {
            false
        }

        AppThemeMode.DARK -> {
            true
        }
    }

    FileRecoveryTheme(
        darkTheme = darkTheme,
    ) {

        Surface(
            modifier = Modifier.fillMaxSize(),
        ) {
            AppNavHost()
        }
    }
}