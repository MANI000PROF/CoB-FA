package com.cobfa.app.dashboard

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.remote.FirestoreService
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.data.repository.SyncManager
import com.cobfa.app.sms.SmsFilters
import com.cobfa.app.sms.SmsInboxReader
import com.cobfa.app.sms.SmsProcessor
import com.cobfa.app.ui.expense.pending.PendingExpensesViewModel
import com.cobfa.app.utils.ExpenseLogger
import com.cobfa.app.utils.PreferenceManager
import java.net.URLEncoder


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

suspend fun performSmsScan(
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
fun SummarySectionCards(summary: com.cobfa.app.domain.model.MonthlySummary) {
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
fun ActionButtons(
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
fun PendingExpensesSectionScrollable(vm: PendingExpensesViewModel) {
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

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            expenses.take(3).forEach { e ->
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
                        Button(onClick = { selectedExpenseId = e.id }) { Text("Confirm") }
                    }
                }

                if (selectedExpenseId == e.id) {
                    com.cobfa.app.ui.expense.list.CategoryPickerBottomSheet(
                        onCategorySelected = { category ->
                            val hash = e.smsHash
                            if (hash != null) vm.confirmBySmsHash(hash, category)
                            selectedExpenseId = null
                        },
                        onDismiss = { selectedExpenseId = null }
                    )
                }
            }

            if (expenses.size > 3) {
                Text(
                    text = "+ ${expenses.size - 3} more pending (open Expenses to confirm)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

