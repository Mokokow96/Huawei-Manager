package com.huawei.manager.newui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import com.huawei.manager.newui.ui.screens.DashboardScreen
import com.huawei.manager.newui.ui.screens.LoginScreen
import com.huawei.manager.newui.ui.screens.SignalHunterScreen
import com.huawei.manager.newui.ui.theme.DarkNavyBase
import com.huawei.manager.newui.ui.theme.HuaweiManagerTheme
import com.huawei.manager.newui.viewmodel.AppScreen
import com.huawei.manager.newui.viewmodel.ModemViewModel

class MainActivity : ComponentActivity() {

    // Instance yang sama dengan viewModel() di Compose (sama-sama terikat ke Activity ini)
    private val modemViewModel: ModemViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HuaweiManagerTheme {
                val viewModel: ModemViewModel = viewModel()
                MainAppNavHost(viewModel = viewModel)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        // Aplikasi dibuka kembali: pulihkan sesi modem & mulai refresh realtime lagi
        modemViewModel.onAppForegrounded()
    }

    override fun onStop() {
        // Aplikasi masuk background: hentikan polling (hemat baterai, hindari sesi basi)
        modemViewModel.onAppBackgrounded()
        super.onStop()
    }
}

@Composable
fun MainAppNavHost(
    viewModel: ModemViewModel,
    modifier: Modifier = Modifier
) {
    val currentScreen by viewModel.currentScreen.collectAsState()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = DarkNavyBase
    ) { innerPadding ->
        AnimatedContent(
            targetState = currentScreen,
            modifier = Modifier.padding(innerPadding),
            transitionSpec = {
                when {
                    // Navigate to Signal Hunter (Slide in from right)
                    targetState == AppScreen.SIGNAL_HUNTER -> {
                        (slideInHorizontally(animationSpec = tween(350)) { it } + fadeIn(animationSpec = tween(300)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(350)) { -it / 3 } + fadeOut(animationSpec = tween(250)))
                    }
                    // Navigate back to Dashboard from Signal Hunter (Slide in from left)
                    initialState == AppScreen.SIGNAL_HUNTER && targetState == AppScreen.DASHBOARD -> {
                        (slideInHorizontally(animationSpec = tween(350)) { -it / 3 } + fadeIn(animationSpec = tween(300)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(350)) { it } + fadeOut(animationSpec = tween(250)))
                    }
                    // Login to Dashboard
                    targetState == AppScreen.DASHBOARD -> {
                        (slideInHorizontally(animationSpec = tween(350)) { it } + fadeIn(animationSpec = tween(300)))
                            .togetherWith(slideOutHorizontally(animationSpec = tween(350)) { -it } + fadeOut(animationSpec = tween(250)))
                    }
                    // Default smooth fade
                    else -> {
                        fadeIn(animationSpec = tween(300))
                            .togetherWith(fadeOut(animationSpec = tween(300)))
                    }
                }
            },
            label = "screen_transition"
        ) { screen ->
            when (screen) {
                AppScreen.LOGIN -> LoginScreen(viewModel = viewModel)
                AppScreen.DASHBOARD -> DashboardScreen(viewModel = viewModel)
                AppScreen.SIGNAL_HUNTER -> SignalHunterScreen(viewModel = viewModel)
            }
        }
    }
}
