package com.driver.pro

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import com.driver.pro.network.ApiClient
import com.driver.pro.network.SessionManager
import com.driver.pro.service.ScreenCaptureService
import com.driver.pro.ui.navigation.AppNavigation
import com.driver.pro.ui.screens.LoginScreen
import com.driver.pro.viewmodels.MainViewModel

@Composable
fun SplashScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        // Logo or progress indicator
        CircularProgressIndicator()
        // or Image(painterResource(R.drawable.logo), ...)
    }
}

@Composable
fun DriverPROApp() {

    val viewModel: MainViewModel = viewModel()

    val user by viewModel.user.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val context = LocalContext.current

    // Create single instances
    val sessionManager = remember { SessionManager(context) }
    val apiService = remember { ApiClient.create(sessionManager) }

    LaunchedEffect(Unit) {
        viewModel.loadUser(sessionManager, apiService)
    }

    if (loading) {
        SplashScreen()
    }
    else if (user == null) {
        LoginScreen(
            sessionManager,
            apiService,
            onLoginSuccess = { loggedInUser ->
                viewModel.setUser(loggedInUser)
            }
        )

    } else {

        AppNavigation(
            user = user!!,
            apiService = apiService,
            onLogout = {
                try {
                    context.stopService(Intent(context, ScreenCaptureService::class.java))
                } catch (_: Exception) {
                }
                sessionManager.clearTokens()
                viewModel.clearUser()
            },
        )
    }
}