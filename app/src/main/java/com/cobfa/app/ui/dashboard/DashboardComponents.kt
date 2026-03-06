package com.cobfa.app.ui.dashboard

import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cobfa.app.domain.model.MonthlySummary
import com.cobfa.app.ui.expense.category.CategoryPickerBottomSheet
import com.cobfa.app.ui.expense.pending.PendingExpensesViewModel

@Composable
fun SectionCard(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun PatternActionSheet(
    alert: DashboardViewModel.BudgetAlert,
    vm: DashboardViewModel,
    onOpenExpensesForMerchant: (String) -> Unit,
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
        title = { Text("Smart actions", style = MaterialTheme.typography.titleLarge) },
        text = {
            Column {
                val items = listOf(
                    Triple("Block for 24h", "Skip future SMS from this merchant", Icons.Default.Block),
                    Triple("Set budget", "₹${String.format("%.0f", quickBudgetAmount)} daily limit", Icons.Default.AccountBalanceWallet),
                    Triple("View history", "See all transactions", Icons.Default.History),
                )

                items.forEachIndexed { index, (h, s, icon) ->
                    ListItem(
                        headlineContent = { Text(h) },
                        supportingContent = { Text(s) },
                        trailingContent = { Icon(icon, null) },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        modifier = Modifier.clickable {
                            when (index) {
                                0 -> {
                                    vm.blockMerchantFor24h(merchant)
                                    onDismiss()
                                }
                                1 -> showBudgetDialog = true
                                2 -> {
                                    onOpenExpensesForMerchant(merchant)
                                    vm.logPatternAction("view_history", merchant)
                                    onDismiss()
                                }
                            }
                        }
                    )
                    if (index != items.lastIndex) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )

    if (showBudgetDialog) {
        AlertDialog(
            onDismissRequest = { showBudgetDialog = false },
            title = { Text("Set budget", style = MaterialTheme.typography.titleLarge) },
            text = {
                TextField(
                    value = quickBudgetAmount.toString(),
                    onValueChange = { quickBudgetAmount = it.toDoubleOrNull() ?: quickBudgetAmount },
                    label = { Text("Daily limit") },
                    prefix = { Text("₹") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            },
            confirmButton = {
                Button(onClick = {
                    vm.createPatternBudget(merchant, quickBudgetAmount)
                    showBudgetDialog = false
                    onDismiss()
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showBudgetDialog = false }) { Text("Cancel") } }
        )
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun BudgetWarningBadge(
    warning: DashboardViewModel.BudgetWarning,
    vm: DashboardViewModel,
    onOpenBudgets: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
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
                onOpenBudgets()
            }) { Text("Details") }

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
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(friendlyTitle(alert.ruleType), style = MaterialTheme.typography.titleSmall)
                Text(alert.message, style = MaterialTheme.typography.bodySmall)
            }
            TextButton(onClick = onAction) { Text("Fix") }
            IconButton(onClick = onDismiss) { Icon(Icons.Default.Close, null) }
        }
    }
}

private fun friendlyTitle(ruleType: String): String = when (ruleType) {
    "BUDGET_100" -> "Budget exceeded"
    "BUDGET_80" -> "Budget at risk"
    "MERCHANT_3X" -> "Repeated merchant spending"
    "CATEGORY_5X" -> "Spending spree"
    "HIGHVALUE_3X" -> "High-value spending"
    else -> "Spending alert"
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
        confirmButton = { TextButton(onClick = onAdjust) { Text("Adjust budget") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Later") } }
    )
}

@Composable
fun SummarySectionCards(
    summary: MonthlySummary,
    onIncomeClick: () -> Unit = {},
    onExpenseClick: () -> Unit = {},
    onBalanceClick: () -> Unit = {}
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        SummaryCard(
            title = "Income",
            amount = summary.income,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
            onClick = onIncomeClick
        )
        SummaryCard(
            title = "Expense",
            amount = summary.expense,
            color = MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
            onClick = onExpenseClick
        )
        SummaryCard(
            title = "Balance",
            amount = summary.balance,
            color = if (summary.balance >= 0) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.error,
            modifier = Modifier.weight(1f),
            onClick = onBalanceClick
        )
    }
}

@Composable
fun ActionButtons(
    onAddExpense: () -> Unit,
    onViewExpenses: () -> Unit,
    onViewBudgets: () -> Unit,
    onViewAnalytics: () -> Unit,
    onViewAchievements: () -> Unit,
    onViewLeaderboard: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = onAddExpense
        ) { Text("Add expense") }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = onViewBudgets
            ) { Text("Budgets") }
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = onViewAnalytics
            ) { Text("Analytics") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = onViewAchievements
            ) { Text("Achievements") }
            FilledTonalButton(
                modifier = Modifier.weight(1f),
                onClick = onViewLeaderboard
            ) { Text("Leaderboard") }
        }

        FilledTonalButton(
            modifier = Modifier.fillMaxWidth(),
            onClick = onViewExpenses
        ) { Text("View expenses") }
    }
}

@Composable
fun PendingExpensesSectionScrollable(vm: PendingExpensesViewModel) {
    val expenses by vm.pendingExpenses.collectAsState(initial = emptyList())
    var selectedExpenseId by remember { mutableStateOf<Long?>(null) }

    if (expenses.isEmpty()) return

    SectionCard {
        Text("Pending expenses", style = MaterialTheme.typography.titleMedium)

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            expenses.take(3).forEach { e ->
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(16.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${e.type.name}  ₹${e.amount}",
                                color = MaterialTheme.colorScheme.error
                            )
                            Text(
                                e.merchant ?: "Unknown",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { selectedExpenseId = e.id }) { Text("Confirm") }
                            TextButton(onClick = { vm.ignoreById(e.id) }) { Text("Ignore") }
                        }
                    }
                }

                if (selectedExpenseId == e.id) {
                    CategoryPickerBottomSheet(
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
