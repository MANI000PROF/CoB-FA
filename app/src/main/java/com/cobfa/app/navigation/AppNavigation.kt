package com.cobfa.app.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cobfa.app.auth.otp.OtpScreen
import com.cobfa.app.auth.phone.PhoneAuthScreen
import com.cobfa.app.auth.phone.PhoneAuthViewModel
import com.cobfa.app.auth.profile.ProfileSetupScreen
import com.cobfa.app.auth.session.DeviceId
import com.cobfa.app.auth.session.SingleDeviceEnforcer
import com.cobfa.app.dashboard.AchievementsViewModel
import com.cobfa.app.dashboard.AnalyticsViewModel
import com.cobfa.app.dashboard.DashboardScreen
import com.cobfa.app.dashboard.LeaderboardViewModel
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.launch.LaunchScreen
import com.cobfa.app.ui.achievements.AchievementsScreen
import com.cobfa.app.ui.analytics.AnalyticsScreen
import com.cobfa.app.ui.budget.BudgetScreen
import com.cobfa.app.ui.expense.list.ExpenseListScreen
import com.cobfa.app.ui.expense.list.ExpenseListViewModel
import com.cobfa.app.ui.expense.list.ExpenseListViewModelFactory
import com.cobfa.app.ui.leaderboard.LeaderboardScreen
import com.cobfa.app.ui.permission.SmsPermissionScreen
import com.cobfa.app.utils.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

@RequiresApi(Build.VERSION_CODES.O)
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
            val context = LocalContext.current
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val deviceId = remember { DeviceId.get(context) }

            DisposableEffect(uid) {
                if (uid == null) return@DisposableEffect onDispose { }

                val enforcer = SingleDeviceEnforcer(
                    uid = uid,
                    localDeviceId = deviceId,
                    onKicked = {
                        navController.navigate("auth") {
                            popUpTo(0)
                            launchSingleTop = true
                        }
                    }
                )
                enforcer.start()
                onDispose { enforcer.stop() }
            }

            DashboardScreen(
                navController = navController,
                onLogout = {
                    FirebaseAuth.getInstance().signOut()
                    navController.navigate("auth") { popUpTo(0) }
                }
            )
        }

        composable("settings") {
            com.cobfa.app.ui.settings.SettingsScreen(navController)
        }

        composable("analytics") {
            val context = LocalContext.current
            val db = remember { ExpenseDatabase.getInstance(context) }

            val vm: AnalyticsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return AnalyticsViewModel(db) as T
                    }
                }
            )

            val ui by vm.uiState.collectAsState()
            val range by vm.range.collectAsState()

            AnalyticsScreen(
                ui = ui,
                selectedRange = range,
                onRangeChange = { vm.setRange(it) }
            )
        }

        composable(
            "expenses?merchant={merchant}",
            arguments = listOf(
                navArgument("merchant") {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                }
            )
        ) { backStackEntry ->
            val context = LocalContext.current
            val merchant = backStackEntry.arguments?.getString("merchant")

            val vm: ExpenseListViewModel = viewModel(
                factory = ExpenseListViewModelFactory(context)
            )

            // ✅ Auto-apply merchant filter
            LaunchedEffect(merchant) {
                if (merchant != null) {
                    vm.updateMerchantFilter(merchant)
                } else {
                    vm.clearFilters()
                }
            }

            ExpenseListScreen(vm)
        }

        composable("achievements") {
            val context = LocalContext.current
            val db = remember { ExpenseDatabase.getInstance(context) }

            val vm: AchievementsViewModel = viewModel(
                factory = object : ViewModelProvider.Factory {
                    @Suppress("UNCHECKED_CAST")
                    override fun <T : ViewModel> create(modelClass: Class<T>): T {
                        return AchievementsViewModel(context, db) as T
                    }
                }
            )

            val points = vm.pointsBalance.collectAsState().value
            val achievements = vm.achievements.collectAsState().value
            val recent = vm.recentPoints.collectAsState().value

            AchievementsScreen(
                pointsBalance = points,
                achievements = achievements,
                recentPoints = recent
            )
        }

        composable("leaderboard") {
            val context = LocalContext.current
            val vm: LeaderboardViewModel = viewModel()

            val mode by vm.mode.collectAsState()
            val rows by vm.rows.collectAsState()
            val loading by vm.loading.collectAsState()
            val error by vm.error.collectAsState()

            // Read city/state from RealtimeDB profile (since you store it there)
            val uid = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.uid
            var city by remember { androidx.compose.runtime.mutableStateOf("") }
            var state by remember { androidx.compose.runtime.mutableStateOf("") }

            LaunchedEffect(uid) {
                if (uid != null) {
                    val snap = FirebaseDatabase.getInstance().reference
                        .child("users").child(uid)
                        .get().await()

                    city = snap.child("city").getValue(String::class.java) ?: ""
                    state = snap.child("state").getValue(String::class.java) ?: ""
                }
            }

            LeaderboardScreen(
                city = city,
                state = state,
                mode = mode,
                rows = rows,
                loading = loading,
                error = error,
                onModeChange = { vm.setMode(it) },
                onReload = { if (city.isNotBlank() && state.isNotBlank()) vm.load(city, state) }
            )
        }

        composable("budgets") {
            BudgetScreen()
        }

    }

}
