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
import com.cobfa.app.launch.LaunchScreen
import com.cobfa.app.ui.expense.list.ExpenseListScreen
import com.cobfa.app.ui.expense.list.ExpenseListViewModelFactory
import com.cobfa.app.ui.permission.SmsPermissionScreen
import com.cobfa.app.utils.PreferenceManager

@Composable
fun AppNavigation() {

    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "launch"
    ) {

        composable("launch") {
            LaunchScreen { route ->
                navController.navigate(route) {
                    popUpTo("launch") { inclusive = true }
                }
            }
        }

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
            val context = LocalContext.current

            ProfileSetupScreen(
                onProfileCompleted = {
                    val decided = PreferenceManager.isSmsPermissionDecided(context)

                    if (decided) {
                        navController.navigate("dashboard") {
                            popUpTo("profile") { inclusive = true }
                        }
                    } else {
                        navController.navigate("sms_permission") {
                            popUpTo("profile") { inclusive = true }
                        }
                    }
                }
            )
        }


        composable("sms_permission") {
            val context = LocalContext.current

            SmsPermissionScreen(
                onPermissionGranted = {
                    val pending = PreferenceManager.isPendingAutoTracking(context)
                    if (pending) {
                        PreferenceManager.setAutoTrackingEnabled(context, true)
                        PreferenceManager.setPendingAutoTracking(context, false)
                    }

                    navController.navigate("dashboard") {
                        popUpTo("sms_permission") { inclusive = true }
                    }
                },
                onSkipClick = {
                    PreferenceManager.markSmsPermissionSkipped(context)
                    PreferenceManager.setPendingAutoTracking(context, false)
                    navController.navigate("dashboard") {
                        popUpTo("sms_permission") { inclusive = true }
                    }
                }
            )
        }

        composable("dashboard") {
            DashboardScreen(
                navController = navController,
                onLogout = {
                    navController.navigate("auth") {
                        popUpTo(0)
                    }
                }
            )
        }

        composable("expenses") {
            val context = LocalContext.current

            ExpenseListScreen(
                vm = viewModel(
                    factory = ExpenseListViewModelFactory(context)
                )
            )
        }
    }

}
