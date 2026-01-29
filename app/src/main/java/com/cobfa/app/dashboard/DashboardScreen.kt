package com.cobfa.app.dashboard

import android.content.pm.PackageManager
import android.Manifest
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.lifecycle.viewmodel.compose.viewModel
import com.cobfa.app.utils.PreferenceManager
import com.google.firebase.auth.FirebaseAuth
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.remote.FirestoreService
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.data.repository.SyncManager
import com.cobfa.app.insights_ml.debug.MlDevPrefs
import com.cobfa.app.sms.SmsFilters
import com.cobfa.app.sms.SmsInboxReader
import com.cobfa.app.sms.SmsProcessor
import com.cobfa.app.ui.expense.manual.ManualExpenseDialog
import com.cobfa.app.ui.expense.pending.PendingExpensesViewModel
import com.cobfa.app.ui.insights.InsightCard
import com.cobfa.app.utils.ExpenseLogger
import kotlinx.coroutines.launch
import java.net.URLEncoder

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val autoTrackingEnabled = PreferenceManager.isAutoTrackingEnabled(context)
    var autoTracking by remember { mutableStateOf(autoTrackingEnabled) }

    val db = remember { ExpenseDatabase.getInstance(context) }
    val firestoreService = remember { FirestoreService() }
    val syncManager = remember { SyncManager(db, firestoreService) }

    val pendingVm = remember {
        PendingExpensesViewModel(
            ExpenseRepository(db.expenseDao(), syncManager)
        )
    }

    val vm: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(context)
    )

    val activeAlert by vm.activeAlert.collectAsState()
    val summary by vm.summary.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    var showManualDialog by remember { mutableStateOf(false) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val insights by vm.personalizedInsights.collectAsState()

    // SMS scanning callback
    LaunchedEffect(Unit) {
        vm.onRefreshRequest = {
            performSmsScan(context, db)
        }
    }

    LaunchedEffect(autoTracking) {
        if (!autoTracking) return@LaunchedEffect

        val granted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) {
            performSmsScan(context, db)
        } else {
            navController.navigate("sms_permission")
        }
    }

    LaunchedEffect(Unit) {
        val prefs = context.getSharedPreferences("cobfa_gamification", android.content.Context.MODE_PRIVATE)
        val lastSeen = prefs.getLong("last_seen_badge_time", 0L)

        val latest = db.achievementDao().getLatestUnlocked()
        if (latest != null && latest.unlockedAt > lastSeen) {
            scope.launch {
                snackbarHostState.showSnackbar("Badge unlocked: ${latest.title}")
            }
            prefs.edit().putLong("last_seen_badge_time", latest.unlockedAt).apply()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { vm.refreshSms() },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp),
                verticalArrangement = Arrangement.Top
            ) {
                // ✅ 1. PATTERN BANNER (top priority)
                activeAlert?.let { alert ->
                    if (alert.ruleType.startsWith("MERCHANT_") ||
                        alert.ruleType.startsWith("CATEGORY_") ||
                        alert.ruleType.startsWith("HIGHVALUE_")
                    ) {

                        var showPatternActions by remember { mutableStateOf(false) }

                        AlertBanner(
                            alert = alert,
                            onDismiss = {
                                vm.logAlertAction(alert.ruleType, "dismiss")
                                vm.onAlertDismissed()
                            },
                            onAction = {
                                showPatternActions = true
                            }
                        )

                        if (showPatternActions) {
                            PatternActionSheet(
                                alert = alert,
                                vm = vm,
                                navController = navController,
                                onDismiss = { showPatternActions = false }
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                    }
                }

                // ✅ 2. Budget warning badges (80-99%)
                vm.budgetWarnings.collectAsState().value.forEach { warning ->
                    BudgetWarningBadge(
                        warning = warning,
                        vm = vm,
                        navController = navController
                    )
                    Spacer(Modifier.height(8.dp))
                }

                // ✅ 3. Auto tracking toggle
                AutoTrackingToggle(
                    autoTracking = autoTracking,
                    onToggle = { checked ->
                        val granted = ContextCompat.checkSelfPermission(
                            context, Manifest.permission.READ_SMS
                        ) == PackageManager.PERMISSION_GRANTED

                        if (checked && granted) {
                            PreferenceManager.setAutoTrackingEnabled(context, true)
                            autoTracking = true
                        } else if (checked) {
                            PreferenceManager.setPendingAutoTracking(context, true)
                            navController.navigate("sms_permission")
                        } else {
                            PreferenceManager.setAutoTrackingEnabled(context, false)
                            autoTracking = false
                        }
                    }
                )

                Spacer(Modifier.height(24.dp))
                Text("Dashboard",
                    modifier = Modifier.combinedClickable(
                        onClick = { /* existing */ },
                        onLongClick = {
                            val enabled = MlDevPrefs.toggleRemoteAi(context)
                            Toast.makeText(context, "Remote AI suggestions: $enabled", Toast.LENGTH_SHORT).show()
                        }
                    ),
                    style = MaterialTheme.typography.headlineSmall)

                // ✅ 4. CRITICAL 100% MODAL (only overspend)
                activeAlert?.let { alert ->
                    if (alert.ruleType == "BUDGET_100") {
                        CriticalAlertDialog(
                            title = "Budget Exceeded!",
                            message = alert.message,
                            onDismiss = { vm.onAlertDismissed() },
                            onAdjust = {
                                vm.onAlertActionTaken("adjust")
                                navController.navigate("budgets")
                            }
                        )
                    }
                }

                // Summary cards
                summary?.let { SummarySectionCards(it) }

                Spacer(Modifier.height(16.dp))
                Text("Welcome to CoB-FA")

                Spacer(Modifier.height(16.dp))
                InsightCard(insights = insights)
                Spacer(Modifier.height(16.dp))

                PendingExpensesSectionScrollable(vm = pendingVm)
                Spacer(Modifier.height(24.dp))

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

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PatternActionSheet(
    alert: DashboardViewModel.BudgetAlert,
    vm: DashboardViewModel,
    navController: NavController,
    onDismiss: () -> Unit
) {
    val merchant = alert.category
    var showBudgetDialog by remember { mutableStateOf(false) }
    var quickBudgetAmount by remember { mutableStateOf(300.0) }

     LaunchedEffect(merchant) {
     quickBudgetAmount = vm.suggestPatternBudget(merchant)
     }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Smart Actions for $merchant",
                style = MaterialTheme.typography.headlineSmall)
        },
        text = {
            LazyColumn {
                // 🚫 BLOCK 24h
                item {
                    ListItem(
                        headlineContent = { Text("🚫 Block $merchant (24h)") },
                        supportingContent = {
                            Text("Skip future SMS from this merchant")
                        },
                        trailingContent = {
                            Icon(Icons.Default.Block, null)
                        },
                        modifier = Modifier.clickable {
                            vm.blockMerchantFor24h(merchant)
                            onDismiss()
                        }
                    )
                }

                // 💰 QUICK BUDGET
                item {
                    ListItem(
                        headlineContent = { Text("💰 Set $merchant Budget") },
                        supportingContent = {
                            Text("₹${String.format("%.0f", quickBudgetAmount)} daily limit")
                        },
                        trailingContent = {
                            Icon(Icons.Default.AccountBalanceWallet, null)
                        },
                        modifier = Modifier.clickable { showBudgetDialog = true }
                    )
                }

                // 📊
                item {
                    ListItem(
                        headlineContent = { Text("📊 $merchant History") },
                        supportingContent = {
                            Text("View all transactions")
                        },
                        trailingContent = {
                            Icon(Icons.Default.History, null)
                        },
                        modifier = Modifier.clickable {
                            navController.navigate("expenses?merchant=${URLEncoder.encode(merchant, "UTF-8")}")
                            vm.logPatternAction("view_history", merchant)
                            onDismiss()
                        }
                    )
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Dismiss") }
        }
    )

    // Quick Budget Dialog
    if (showBudgetDialog) {
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("Set $merchant Budget") },
            text = {
                Column {
                    TextField(
                        value = quickBudgetAmount.toString(),
                        onValueChange = {
                            quickBudgetAmount = it.toDoubleOrNull() ?: 300.0
                        },
                        label = { Text("Daily Limit") },
                        prefix = { Text("₹") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.createPatternBudget(merchant, quickBudgetAmount)
                    showBudgetDialog = false
                    onDismiss()
                }) { Text("Set Budget") }
            },
            dismissButton = {
                TextButton(onClick = { showBudgetDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun BudgetWarningBadge(
    warning: DashboardViewModel.BudgetWarning,
    vm: DashboardViewModel,
    navController: NavController,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer  // ✅ Standard warning
            // OR: Color(0xFFFFF3C4)  // Soft yellow
        )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "${warning.category} ${warning.percentage}% ⚠️",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    "₹${String.format("%.0f", warning.spent)} / ₹${String.format("%.0f", warning.budget)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            TextButton(onClick = {
                vm.logGenericNudge(type = "BUDGET_80", category = warning.category, action = "details")
                navController.navigate("budgets")
            }) {
                Text("Details")
            }
            IconButton(onClick = {
                Log.d("WARNING_DISMISS", "Dismissing ${warning.category}")
                vm.logGenericNudge(type = "BUDGET_80", category = warning.category, action = "dismiss")
                vm.dismiss80Warning(warning.category)
            }) {
                Icon(Icons.Default.Close, "Dismiss warning")
            }
        }
    }
}

@Composable
fun AlertBanner(
    alert: DashboardViewModel.BudgetAlert,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit,
    onAction: () -> Unit
) {
    Card(
        modifier = modifier.padding(horizontal = 0.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("⚠️ ${alert.ruleType}", style = MaterialTheme.typography.titleSmall)
                Text(alert.message, style = MaterialTheme.typography.bodySmall)
            }
            Row {
                TextButton(onClick = onAction) { Text("Fix") }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, null)
                }
            }
        }
    }
}

@Composable
fun CriticalAlertDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onAdjust: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.error) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onAdjust) { Text("Adjust Budget") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Later") }
        }
    )
}

private suspend fun performSmsScan(
    context: android.content.Context,
    db: ExpenseDatabase
) {
    val granted = ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    if (!granted) {
        ExpenseLogger.logValidationFailed("permission", "READ_SMS", "not granted")
        return
    }

    ExpenseLogger.logScanStart("DashboardScreen")

    val firestoreService = FirestoreService()
    val syncManager = SyncManager(db, firestoreService)
    val repo = ExpenseRepository(db.expenseDao(), syncManager)

    // ---- Incremental scan cursor (bootstrap + overlap) ----
    val now = System.currentTimeMillis()
    val lastTs0 = PreferenceManager.getLastSmsTimestamp(context)

    // Bootstrap: if never scanned, start from last 90 days (bounded, safe for demo)
    val bootstrapStart = now - 90L * 24 * 60 * 60 * 1000
    val base = if (lastTs0 == 0L) bootstrapStart else lastTs0

    // Overlap avoids missing messages around the boundary
    val overlapMs = 2L * 60 * 60 * 1000
    val since = (base - overlapMs).coerceAtLeast(0L)
    // ------------------------------------------------------

    val messages = SmsInboxReader.readRecentSmsSince(context, sinceMs = since, limit = 200)
    ExpenseLogger.logSmsRead(messages.size)

    var processedCount = 0
    var skippedCount = 0

    for (sms in messages) {
        if (SmsFilters.isBlocked(sms.body)) {
            skippedCount++
            continue
        }

        val inserted = SmsProcessor.processWithDedup(
            sender = sms.address,
            body = sms.body,
            timestamp = sms.timestamp,
            repo = repo,
            syncManager = syncManager
        )

        if (inserted) processedCount++ else skippedCount++
    }

    // IMPORTANT: advance cursor based on newest *seen* SMS, not only inserted ones
    val newestTs = messages.maxOfOrNull { it.timestamp } ?: 0L
    if (newestTs > 0) PreferenceManager.setLastSmsTimestamp(context, newestTs)

    ExpenseLogger.logScanComplete(processedCount, skippedCount, "DashboardScreen")
}

@Composable
private fun AutoTrackingToggle(
    autoTracking: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Automatic Expense Tracking",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Detect expenses from bank & UPI SMS",
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Switch(
                checked = autoTracking,
                onCheckedChange = onToggle
            )
        }
    }
}

@Composable
private fun SummarySectionCards(summary: com.cobfa.app.domain.model.MonthlySummary) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        SummaryCard(
            title = "Income",
            amount = summary.income,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )

        SummaryCard(
            title = "Expense",
            amount = summary.expense,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )

        SummaryCard(
            title = "Balance",
            amount = summary.balance,
            color = if (summary.balance >= 0)
                MaterialTheme.colorScheme.tertiary
            else
                MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ActionButtons(
    onAddExpense: () -> Unit,
    onLogout: () -> Unit,
    onViewExpenses: () -> Unit,
    onViewBudgets: () -> Unit,
    onViewAnalytics: () -> Unit,
    onViewAchievements: () -> Unit,
    onViewLeaderboard: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onAddExpense
        ) {
            Text("Add Expense")
        }

        Spacer(Modifier.height(12.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onLogout
        ) {
            Text("Logout")
        }

        Spacer(Modifier.height(12.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onViewBudgets
        ) {
            Text("Budgets")
        }

        Spacer(Modifier.height(12.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onViewAnalytics
        ) {
            Text("Analytics")
        }

        Spacer(Modifier.height(12.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onViewAchievements
        ) { Text("Achievements") }

        Spacer(Modifier.height(12.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onViewLeaderboard
        ) { Text("Leaderboard") }

        Spacer(Modifier.height(12.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onViewExpenses
        ) {
            Text("View Expenses")
        }
    }
}

// ✅ SCROLLABLE: Pending expenses with LazyColumn
@Composable
private fun PendingExpensesSectionScrollable(vm: PendingExpensesViewModel) {
    val expenses by vm.pendingExpenses.collectAsState(initial = emptyList())
    var selectedExpenseId by remember { mutableStateOf<Long?>(null) }

    if (expenses.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 400.dp)  // ✅ Limit height so content is scrollable
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(8.dp)
            )
            .padding(8.dp)
    ) {
        Text(
            "Pending expenses",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(8.dp)
        )

        // ✅ LazyColumn for scrolling
        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(expenses.size) { index ->
                val e = expenses[index]
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${e.type.name}  ₹${e.amount}",
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                e.merchant ?: "Unknown",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = { selectedExpenseId = e.id },
                            modifier = Modifier.wrapContentWidth()
                        ) {
                            Text("Confirm")
                        }
                    }
                }

                // ✅ Show bottom sheet for category picker
                if (selectedExpenseId == e.id) {
                    com.cobfa.app.ui.expense.list.CategoryPickerBottomSheet(
                        onCategorySelected = { category ->
                            vm.confirm(e.id, category)
                            selectedExpenseId = null
                        },
                        onDismiss = {
                            selectedExpenseId = null
                        }
                    )
                }
            }
        }
    }
}

