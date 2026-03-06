package com.cobfa.app.navigation

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cobfa.app.ui.dashboard.DashboardScreen
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.remote.FirestoreService
import com.cobfa.app.data.repository.BudgetRepository
import com.cobfa.app.data.repository.SyncManager
import com.cobfa.app.ui.analytics.AnalyticsScreen
import com.cobfa.app.ui.analytics.AnalyticsViewModel
import com.cobfa.app.ui.budget.BudgetScreen
import com.cobfa.app.ui.budget.BudgetViewModel
import com.cobfa.app.ui.expense.list.ExpenseListScreen
import com.cobfa.app.ui.expense.list.ExpenseListViewModel
import com.cobfa.app.ui.expense.list.ExpenseListViewModelFactory
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.net.URLEncoder

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun MainScaffold(
    rootNavController: NavHostController,
    onLogoutToAuth: () -> Unit
) {
    val mainNavController = rememberNavController()

    data class Item(
        val route: String,
        val label: String,
        val icon: androidx.compose.ui.graphics.vector.ImageVector,
    )

    val items = listOf(
        Item("dashboard", "Home", Icons.Default.Home),
        Item("budgets", "Budgets", Icons.Default.AccountBalanceWallet),
        Item("analytics", "Analytics", Icons.Default.BarChart),
        Item("expenses?merchant={merchant}", "Expenses", Icons.Default.ReceiptLong),
    )

    val navBackStackEntry by mainNavController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    fun isExpensesRoute(route: String?) =
        route == "expenses?merchant={merchant}" || route?.startsWith("expenses?merchant=") == true

    val haptic = LocalHapticFeedback.current

    Scaffold(
        bottomBar = {
            NavigationBar {
                items.forEach { item ->
                    val selected =
                        when (item.route) {
                            "expenses?merchant={merchant}" -> isExpensesRoute(currentRoute)
                            else -> currentRoute == item.route
                        }
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            if (!selected) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                mainNavController.navigate(item.route) {
                                    popUpTo(mainNavController.graph.findStartDestination().id) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        },
                        icon = { Icon(item.icon, contentDescription = item.label) },
                        label = { Text(item.label) }
                    )
                }
            }
        }
    ) { innerPadding ->

        NavHost(
            navController = mainNavController,
            startDestination = "dashboard",
            modifier = Modifier.padding(innerPadding)
        ) {

            composable("dashboard") {
                DashboardScreen(
                    onOpenBudgets = { mainNavController.navigate("budgets") },
                    onOpenAnalytics = { mainNavController.navigate("analytics") },
                    onOpenExpenses = { merchant ->
                        if (merchant.isNullOrBlank()) {
                            mainNavController.navigate("expenses?merchant={merchant}") {
                                launchSingleTop = true
                            }
                        } else {
                            val encoded = URLEncoder.encode(merchant, "UTF-8")
                            mainNavController.navigate("expenses?merchant=$encoded") {
                                launchSingleTop = true
                            }
                        }
                    },
                    onOpenAchievements = { rootNavController.navigate("achievements") },
                    onOpenLeaderboard = { rootNavController.navigate("leaderboard") },
                    onRequestSmsPermission = { rootNavController.navigate("sms_permission") }
                )
            }

            composable("budgets") {
                val context = LocalContext.current
                val db = remember { ExpenseDatabase.getInstance(context) }

                val vm: BudgetViewModel = viewModel(
                    factory = object : ViewModelProvider.Factory {
                        @Suppress("UNCHECKED_CAST")
                        override fun <T : ViewModel> create(modelClass: Class<T>): T {
                            val repo = BudgetRepository(db.budgetDao())
                            val syncManager = SyncManager(db, FirestoreService())
                            return BudgetViewModel(db, repo, syncManager) as T
                        }
                    }
                )

                BudgetScreen(vm)
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
                val selectedMonth: YearMonth by vm.selectedMonth.collectAsState()

                val monthLabel = remember(selectedMonth) {
                    selectedMonth.format(DateTimeFormatter.ofPattern("MMM yyyy"))
                }

                val canGoNextMonth = remember(selectedMonth) {
                    selectedMonth.isBefore(YearMonth.now())
                }

                AnalyticsScreen(
                    ui = ui,
                    selectedRange = range,
                    onRangeChange = { vm.setRange(it) },
                    selectedMonthLabel = monthLabel,
                    onPrevMonth = { vm.prevMonth() },
                    onNextMonth = { vm.nextMonth() },
                    canGoNextMonth = canGoNextMonth
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

                LaunchedEffect(merchant) {
                    if (!merchant.isNullOrBlank()) vm.updateMerchantFilter(merchant)
                    else vm.updateMerchantFilter(null)
                }

                ExpenseListScreen(vm)
            }
        }
    }
}
