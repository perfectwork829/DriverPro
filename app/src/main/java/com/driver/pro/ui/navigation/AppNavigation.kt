package com.driver.pro.ui.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.compose.*
import com.driver.pro.network.ApiService
import com.driver.pro.network.User
import com.driver.pro.ui.screens.MainScreen
import com.driver.pro.ui.screens.PostcodeDetailScreen


@Composable
fun AppNavigation(
    user: User,
    apiService: ApiService,
    onLogout: () -> Unit,
) {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "main"
    ) {

        composable("main") {
            MainScreen(
                user = user,
                navController = navController,
                onLogout = onLogout,
            )
        }

        composable("postcodeDetail/{postcode}") { backStackEntry ->

            val postcode = Uri.decode(backStackEntry.arguments?.getString("postcode").orEmpty())

            PostcodeDetailScreen(
                postcode = postcode,
                navController = navController,
                userId = user.id,
                apiService = apiService
            )
        }
    }
}
