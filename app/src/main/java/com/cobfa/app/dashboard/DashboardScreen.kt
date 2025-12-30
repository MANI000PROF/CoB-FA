package com.cobfa.app.dashboard

import android.content.pm.PackageManager
import android.Manifest
import android.util.Log
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.remote.FirestoreService
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.data.repository.SyncManager
import com.cobfa.app.sms.SmsFilters
import com.cobfa.app.sms.SmsInboxReader
import com.cobfa.app.sms.SmsProcessor
import com.cobfa.app.ui.expense.manual.ManualExpenseDialog
import com.cobfa.app.ui.expense.pending.PendingExpensesViewModel
import com.cobfa.app.utils.ExpenseLogger

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    navController: NavController,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    val autoTrackingEnabled = PreferenceManager.isAutoTrackingEnabled(context)

    var autoTracking by remember { mutableStateOf(autoTrackingEnabled) }

    val db = remember { ExpenseDatabase.getInstance(context) }

    // ✅ Create syncManager once
    val firestoreService = remember { FirestoreService() }
    val syncManager = remember { SyncManager(db, firestoreService) }

    val pendingVm = remember {
        PendingExpensesViewModel(
            ExpenseRepository(db.expenseDao(), syncManager)  // ✅ Pass syncManager
        )
    }

    val vm: DashboardViewModel = viewModel(
        factory = DashboardViewModelFactory(context)
    )

    val summary by vm.summary.collectAsState()
    val isRefreshing by vm.isRefreshing.collectAsState()
    var showManualDialog by remember { mutableStateOf(false) }

    // ✅ Setup SMS scanning callback once
    LaunchedEffect(Unit) {
        vm.onRefreshRequest = {
            Log.d("REFRESH_DEBUG", "SMS scan triggered via callback")
            performSmsScan(context, db)
        }
    }

    // ✅ Initial scan when auto-tracking enabled
    LaunchedEffect(autoTracking) {
        if (!autoTracking) return@LaunchedEffect
        Log.d("REFRESH_DEBUG", "Initial auto-scan on app load")
        performSmsScan(context, db)
    }

    // ✅ Log refresh state changes
    LaunchedEffect(isRefreshing) {
        Log.d("REFRESH_DEBUG", "Refresh state changed: isRefreshing=$isRefreshing")
    }

    // ✅ Native Material 3 1.3.0 Pull-to-Refresh
    Log.d("REFRESH_DEBUG", "Rendering PullToRefreshBox with isRefreshing=$isRefreshing")

    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = {
            Log.d("REFRESH_DEBUG", "onRefresh callback triggered by user pull")
            vm.refreshSms()
        },
        modifier = Modifier.fillMaxSize()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Top
        ) {
            // Auto tracking toggle
            AutoTrackingToggle(
                autoTracking = autoTracking,
                onToggle = { checked ->
                    val granted = ContextCompat.checkSelfPermission(
                        context,
                        Manifest.permission.READ_SMS
                    ) == PackageManager.PERMISSION_GRANTED

                    if (checked) {
                        if (granted) {
                            PreferenceManager.setAutoTrackingEnabled(context, true)
                            autoTracking = true
                        } else {
                            PreferenceManager.setPendingAutoTracking(context, true)
                            navController.navigate("sms_permission")
                        }
                    } else {
                        PreferenceManager.setAutoTrackingEnabled(context, false)
                        autoTracking = false
                    }
                }
            )

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Dashboard",
                style = MaterialTheme.typography.headlineSmall
            )

            // Summary cards
            summary?.let {
                SummarySectionCards(it)
            }

            Spacer(Modifier.height(16.dp))
            Text("Welcome to CoB-FA")
            Spacer(Modifier.height(16.dp))

            // ✅ SCROLLABLE: Pending expenses now in scrollable container
            PendingExpensesSectionScrollable(vm = pendingVm)

            Spacer(Modifier.height(24.dp))

            // Action buttons
            ActionButtons(
                onLogout = {
                    FirebaseAuth.getInstance().signOut()
                    onLogout()
                },
                onViewExpenses = {
                    navController.navigate("expenses")
                },
                onAddExpense = {
                    showManualDialog = true
                }
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

    val firestoreService = FirestoreService()  // ✅ NEW
    val syncManager = SyncManager(db, firestoreService)  // ✅ NEW

    val repo = ExpenseRepository(db.expenseDao(), syncManager)
    val messages = SmsInboxReader.readRecentSms(context, limit = 50)
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

        if (inserted) {
            processedCount++
        } else {
            skippedCount++
        }
    }

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
    onViewExpenses: () -> Unit
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

// ✅ OPTIONAL: Keep old non-scrollable version for reference (can delete later)
@Composable
fun PendingExpensesSection(vm: PendingExpensesViewModel) {
    val expenses by vm.pendingExpenses.collectAsState(initial = emptyList())
    var selectedExpenseId by remember { mutableStateOf<Long?>(null) }

    if (expenses.isEmpty()) return

    Column {
        Text("Pending expenses", style = MaterialTheme.typography.titleMedium)

        expenses.forEach { e ->
            Card(modifier = Modifier.padding(vertical = 8.dp)) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            text = "${e.type.name}  ₹${e.amount}",
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(e.merchant ?: "Unknown", style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { selectedExpenseId = e.id }) {
                        Text("Confirm")
                    }
                }
                selectedExpenseId?.let { expenseId ->
                    com.cobfa.app.ui.expense.list.CategoryPickerBottomSheet(
                        onCategorySelected = { category ->
                            vm.confirm(expenseId, category)
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

@Composable
fun SummaryCard(
    title: String,
    amount: Long,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.padding(horizontal = 4.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "₹${amount}",
                style = MaterialTheme.typography.titleMedium,
                color = color
            )
        }
    }
}
