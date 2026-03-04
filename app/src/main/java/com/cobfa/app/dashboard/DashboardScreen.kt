package com.cobfa.app.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.remote.FirestoreService
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.data.repository.SyncManager
import com.cobfa.app.ui.expense.manual.ManualExpenseDialog
import com.cobfa.app.ui.expense.pending.PendingExpensesViewModel
import com.cobfa.app.ui.insights.InsightCard
import com.cobfa.app.utils.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val db = remember { ExpenseDatabase.getInstance(context) }
    val firestoreService = remember { FirestoreService() }
    val syncManager = remember { SyncManager(db, firestoreService) }

    val pendingVm = remember {
        PendingExpensesViewModel(
            ExpenseRepository(db.expenseDao(), syncManager)
        )
    }

    val vm: DashboardViewModel = viewModel(factory = DashboardViewModelFactory(context))

    val activeAlert by vm.activeAlert.collectAsState()
    val summary by vm.summary.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    val insights by vm.personalizedInsights.collectAsState()

    var showManualDialog by remember { mutableStateOf(false) }
    var showPatternActions by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val autoTrackingEnabled = PreferenceManager.isAutoTrackingEnabled(context)
    val warnings by vm.budgetWarnings.collectAsState()

    val budgetHealth by vm.budgetHealth.collectAsState()

    var showExpenseSheet by remember { mutableStateOf(false) }
    var showBalanceSheet by remember { mutableStateOf(false) }

    val recentExpenses by db.expenseDao().observeConfirmedNewest().collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        if (!autoTrackingEnabled) return@LaunchedEffect

        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) vm.refreshSms() else navController.navigate("sms_permission")
    }

    // Badge snackbar (keep your existing behavior)
    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("cobfa_gamification", android.content.Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong("last_seen_badge_time", 0L)

        val latest = db.achievementDao().getLatestUnlocked()
        if (latest != null && latest.unlockedAt > lastSeen) {
            scope.launch { snackbarHostState.showSnackbar("Badge unlocked: ${latest.title}") }
            prefs.edit().putLong("last_seen_badge_time", latest.unlockedAt).apply()
        }
    }

    var balanceSteps by remember { mutableStateOf<List<DashboardViewModel.BalanceStep>>(emptyList()) }
    var loadingBalance by remember { mutableStateOf(false) }

    LaunchedEffect(showBalanceSheet) {
        if (showBalanceSheet) {
            loadingBalance = true
            balanceSteps = vm.computeBalanceBreakdown()
            loadingBalance = false
        }
    }

    if (showExpenseSheet) {
        ExpenseDetailSheet(
            expenses = recentExpenses,
            onViewAll = {
                showExpenseSheet = false
                navController.navigate("expenses")
            },
            onDismiss = { showExpenseSheet = false }
        )
    }

    summary?.let { s ->
        if (showBalanceSheet) {
            BalanceDetailSheet(
                summary = s,
                steps = balanceSteps,
                loading = loadingBalance,
                onDismiss = { showBalanceSheet = false }
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Dashboard") },
                actions = {
                    IconButton(onClick = { navController.navigate("settings") }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->

        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { vm.refreshSms() },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {

                // 1) Pattern/banner alert (top priority)
                item {
                    activeAlert?.let { alert ->
                        AlertBanner(
                            alert = alert,
                            onDismiss = {
                                vm.onAlertDismissed()
                            },
                            onAction = { showPatternActions = true }
                        )

                        if (showPatternActions) {
                            PatternActionSheet(
                                alert = alert,
                                vm = vm,
                                navController = navController,
                                onDismiss = { showPatternActions = false }
                            )
                        }
                    }
                }

                // 2) Budget warnings (show max 2 to reduce clutter)
                item {
                    warnings.take(2).forEach { warning ->
                        BudgetWarningBadge(
                            warning = warning,
                            vm = vm,
                            navController = navController
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                }

                // Critical dialog only for 100% budget exceeded
                item {
                    activeAlert?.let { alert ->
                        if (alert.ruleType == "BUDGET_100") {
                            CriticalAlertDialog(
                                title = "Budget Exceeded!",
                                message = alert.message,
                                onDismiss = { vm.onAlertActionTaken("later") }, // record “Later”
                                onAdjust = {
                                    vm.onAlertActionTaken("adjust")             // record “Adjust”
                                    navController.navigate("budgets")
                                }
                            )
                        }
                    }
                }

                item {
                    BudgetHealthCard(
                        health = budgetHealth,
                        onViewBudgets = { navController.navigate("budgets") }
                    )
                }

                // Summary
                item {
                    summary?.let {
                        SummarySectionCards(
                            summary = it,
                            onIncomeClick = { /* maybe later: show income breakdown */ },
                            onExpenseClick = { showExpenseSheet = true },
                            onBalanceClick = { showBalanceSheet = true }
                        )
                    }
                }

                // Insights
                item {
                    InsightCard(insights = insights)
                }

                // Pending expenses (non-scroll, top 3)
                item {
                    PendingExpensesSectionScrollable(vm = pendingVm)
                }

                // Actions (keep your modular component)
                item {
                    ActionButtons(
                        onLogout = {
                            FirebaseAuth.getInstance().signOut()
                            onLogout()
                        },
                        onViewExpenses = { navController.navigate("expenses") },
                        onAddExpense = { showManualDialog = true },
                        onViewBudgets = { navController.navigate("budgets") },
                        onViewAnalytics = { navController.navigate("analytics") },
                        onViewAchievements = { navController.navigate("achievements") },
                        onViewLeaderboard = { navController.navigate("leaderboard") }
                    )
                }

                item {
                    if (showManualDialog) {
                        ManualExpenseDialog(
                            onDismiss = { showManualDialog = false },
                            db = db,
                            syncManager = syncManager
                        )
                    }
                }
            }
        }
    }
}