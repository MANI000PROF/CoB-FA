package com.cobfa.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.*
import androidx.navigation.compose.rememberNavController
import com.cobfa.app.auth.otp.OtpScreen
import com.cobfa.app.auth.phone.PhoneAuthScreen
import com.cobfa.app.auth.phone.PhoneAuthViewModel
import com.cobfa.app.auth.profile.ProfileSetupScreen
import com.cobfa.app.dashboard.DashboardScreen
import com.cobfa.app.navigation.AuthState

@Composable
fun AppNavigation() {

    val navController = rememberNavController()

    val context = LocalContext.current
    val start = AuthState.getStartDestination(context)

    NavHost(
        navController = navController,
        startDestination = start
    ) {

        navigation(
            route = "auth",
            startDestination = "phone"
        ) {

            composable("phone") { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry("auth")
                }
                val vm: PhoneAuthViewModel = viewModel(parentEntry)

                PhoneAuthScreen(navController, vm)
            }

            composable("otp") { backStackEntry ->
                val parentEntry = remember(backStackEntry) {
                    navController.getBackStackEntry("auth")
                }
                val vm: PhoneAuthViewModel = viewModel(parentEntry)

                OtpScreen(navController, vm)
            }
        }

        composable("profile") {
            ProfileSetupScreen(
                onProfileCompleted = {
                    navController.navigate("dashboard") {
                        popUpTo("profile") {
                            inclusive = true
                        }
                    }
                }
            )
        }

        composable("dashboard") {
            DashboardScreen(
                onLogout = {
                    navController.navigate("auth") {
                        popUpTo(0)
                    }
                }
            )
        }
    }

}
