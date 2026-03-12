package com.cobfa.app.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.cobfa.app.auth.otp.OtpScreen
import com.cobfa.app.auth.phone.PhoneAuthScreen
import com.cobfa.app.auth.phone.PhoneAuthViewModel
import com.cobfa.app.auth.profile.ProfileSetupScreen
import com.cobfa.app.auth.session.DeviceId
import com.cobfa.app.auth.session.SingleDeviceEnforcer
import com.cobfa.app.launch.LaunchScreen
import com.cobfa.app.ui.achievements.AchievementsScreen
import com.cobfa.app.ui.achievements.AchievementsViewModel
import com.cobfa.app.ui.leaderboard.LeaderboardScreen
import com.cobfa.app.ui.leaderboard.LeaderboardViewModel
import com.cobfa.app.ui.permission.SmsPermissionScreen
import com.cobfa.app.ui.settings.SettingsScreen
import com.cobfa.app.utils.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.ui.profile.AccountInsightsScreen
import com.cobfa.app.ui.profile.ProfileScreen

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
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("auth") }
                val vm: PhoneAuthViewModel = viewModel(parentEntry)
                PhoneAuthScreen(navController, vm)
            }

            composable("otp") { backStackEntry ->
                val parentEntry = remember(backStackEntry) { navController.getBackStackEntry("auth") }
                val vm: PhoneAuthViewModel = viewModel(parentEntry)
                OtpScreen(navController, vm)
            }
        }

        composable("profile") {
            val context = LocalContext.current

            ProfileSetupScreen(
                onProfileCompleted = {
                    val decided = PreferenceManager.isSmsPermissionDecided(context)
                    val smsGranted = ContextCompat.checkSelfPermission(
                        context, Manifest.permission.READ_SMS
                    ) == PackageManager.PERMISSION_GRANTED

                    when {
                        smsGranted || decided -> {
                            navController.navigate("main") {
                                popUpTo("profile") { inclusive = true }
                            }
                        }
                        else -> {
                            navController.navigate("sms_permission") {
                                popUpTo("profile") { inclusive = true }
                            }
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

                    navController.navigate("main") {
                        popUpTo("sms_permission") { inclusive = true }
                    }
                },
                onSkipClick = {
                    PreferenceManager.markSmsPermissionSkipped(context)
                    PreferenceManager.setPendingAutoTracking(context, false)
                    navController.navigate("main") {
                        popUpTo("sms_permission") { inclusive = true }
                    }
                }
            )
        }

        // ✅ Main area (bottom nav lives inside this)
        composable("main") {
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

            MainScaffold(
                rootNavController = navController
            )
        }

        // Secondary routes (no bottom bar)
        composable("settings") {
            SettingsScreen(navController)
        }

        composable("profile_view") {
            ProfileScreen(
                onBack = { navController.popBackStack() }
            )
        }

        composable("account_insights") {
            AccountInsightsScreen(
                onBack = { navController.popBackStack() }
            )
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
            val uid = FirebaseAuth.getInstance().currentUser?.uid
            val vm: LeaderboardViewModel = viewModel()

            val mode by vm.mode.collectAsState()
            val rows by vm.rows.collectAsState()
            val loading by vm.loading.collectAsState()
            val error by vm.error.collectAsState()

            var city by remember { mutableStateOf("") }
            var state by remember { mutableStateOf("") }

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
                currentUid = uid.orEmpty(),
                city = city,
                state = state,
                mode = mode,
                rows = rows,
                loading = loading,
                error = error,
                onModeChange = { vm.setMode(it) },
                onReload = {
                    val safeUid = uid ?: return@LeaderboardScreen
                    when (mode) {
                        LeaderboardViewModel.Mode.CITY -> {
                            if (city.isNotBlank() && state.isNotBlank()) vm.load(city, state, safeUid)
                        }
                        LeaderboardViewModel.Mode.STATE -> {
                            if (state.isNotBlank()) vm.load(city, state, safeUid)
                        }
                    }
                }
            )
        }
    }
}
