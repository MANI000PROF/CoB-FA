package com.cobfa.app.ui.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.remote.FirestoreService
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.data.repository.SyncManager
import com.cobfa.app.ui.ShimmerCard
import com.cobfa.app.ui.expense.manual.ManualExpenseDialog
import com.cobfa.app.ui.expense.pending.PendingExpensesViewModel
import com.cobfa.app.ui.insights.InsightAction
import com.cobfa.app.ui.insights.InsightCard
import com.cobfa.app.utils.PreferenceManager
import kotlinx.coroutines.launch

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onOpenBudgets: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenExpenses: (merchant: String?) -> Unit,
    onOpenAchievements: () -> Unit,
    onOpenLeaderboard: () -> Unit,
    onRequestSmsPermission: () -> Unit,
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
    val warnings by vm.budgetWarnings.collectAsState()
    val budgetHealth by vm.budgetHealth.collectAsState()

    var showManualDialog by remember { mutableStateOf(false) }
    var showPatternActions by remember { mutableStateOf(false) }
    var showExpenseSheet by remember { mutableStateOf(false) }
    var showBalanceSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val autoTrackingEnabled = PreferenceManager.isAutoTrackingEnabled(context)
    val recentExpenses by db.expenseDao().observeConfirmedNewest().collectAsState(initial = emptyList())

    LaunchedEffect(Unit) {
        if (!autoTrackingEnabled) return@LaunchedEffect

        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) vm.refreshSms() else onRequestSmsPermission()
    }

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
                onOpenExpenses(null)
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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Dashboard") },
                scrollBehavior = scrollBehavior
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

                item {
                    activeAlert?.let { alert ->
                        AlertBanner(
                            alert = alert,
                            onDismiss = { vm.onAlertDismissed() },
                            onAction = { showPatternActions = true }
                        )

                        if (showPatternActions) {
                            PatternActionSheet(
                                alert = alert,
                                vm = vm,
                                onOpenExpensesForMerchant = { onOpenExpenses(it) },
                                onDismiss = { showPatternActions = false }
                            )
                        }
                    }
                }

                item {
                    warnings.take(2).forEach { warning ->
                        BudgetWarningBadge(
                            warning = warning,
                            vm = vm,
                            onOpenBudgets = onOpenBudgets
                        )
                    }
                }

                item {
                    activeAlert?.let { alert ->
                        if (alert.ruleType == "BUDGET_100") {
                            CriticalAlertDialog(
                                title = "Budget Exceeded!",
                                message = alert.message,
                                onDismiss = { vm.onAlertActionTaken("later") },
                                onAdjust = {
                                    vm.onAlertActionTaken("adjust")
                                    onOpenBudgets()
                                }
                            )
                        }
                    }
                }

                item {
                    Crossfade(
                        targetState = budgetHealth.totalBudgets > 0,
                        animationSpec = tween(400),
                        label = "budget_transition"
                    ) { hasBudgets ->
                        if (hasBudgets) {
                            BudgetHealthCard(
                                health = budgetHealth,
                                onViewBudgets = onOpenBudgets
                            )
                        } else {
                            ShimmerCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                            )
                        }
                    }
                }

                item {
                    SectionCard {
                        when (val s = summary) {
                            null -> {
                                Text(
                                    "Loading this month’s summary…",
                                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                                    color = androidx.compose.material3.MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            else -> {
                                SummarySectionCards(
                                    summary = s,
                                    onIncomeClick = { },
                                    onExpenseClick = { showExpenseSheet = true },
                                    onBalanceClick = { showBalanceSheet = true }
                                )
                            }
                        }
                    }
                }

                item {
                    Crossfade(
                        targetState = insights.isNotEmpty(),
                        animationSpec = tween(400),
                        label = "insights_transition"
                    ) { hasInsights ->
                        if (hasInsights) {
                            InsightCard(
                                insights = insights,
                                onAction = { action ->
                                    when (action) {
                                        is InsightAction.OpenUrl -> {
                                            val uri = android.net.Uri.parse(action.url)
                                            androidx.browser.customtabs.CustomTabsIntent.Builder()
                                                .build()
                                                .launchUrl(context, uri)
                                        }

                                        is InsightAction.SetBudget -> onOpenBudgets()
                                        is InsightAction.MarkDone -> vm.markInsightDone(action.insightKey)
                                        is InsightAction.NotUseful -> vm.dismissInsight(action.insightKey)
                                    }
                                }
                            )
                        } else {
                            ShimmerCard(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                            )
                        }
                    }
                }

                item {
                    PendingExpensesSectionScrollable(vm = pendingVm)
                }

                item {
                    SectionCard {
                        Text("Quick actions", style = androidx.compose.material3.MaterialTheme.typography.titleMedium)
                        ActionButtons(
                            onViewExpenses = { onOpenExpenses(null) },
                            onAddExpense = { showManualDialog = true },
                            onViewBudgets = onOpenBudgets,
                            onViewAnalytics = onOpenAnalytics,
                            onViewAchievements = onOpenAchievements,
                            onViewLeaderboard = onOpenLeaderboard
                        )
                    }
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
