package com.cobfa.app.ui.budget

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.local.entity.BudgetEntity
import com.cobfa.app.data.remote.FirestoreService
import com.cobfa.app.data.repository.BudgetRepository
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.data.repository.SyncManager
import com.cobfa.app.domain.model.ExpenseCategory
import kotlinx.coroutines.launch
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen() {
    val context = LocalContext.current

    val db = remember { ExpenseDatabase.getInstance(context) }
    val repo = remember { BudgetRepository(db.budgetDao()) }
    val firestoreService = remember { FirestoreService() }
    val syncManager = remember { SyncManager(db, firestoreService) }

    // Current month start (1st of current month, 00:00:00)
    val currentMonthStart = remember {
        val cal = Calendar.getInstance()
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.timeInMillis
    }

    val budgets by repo.getBudgetsForMonth(currentMonthStart).collectAsState(initial = emptyList())
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedBudgetForDelete by remember { mutableStateOf<BudgetEntity?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monthly Budgets") },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add budget")
                    }
                }
            )
        }
    ) { padding ->
        if (budgets.isEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "No budgets set yet",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Tap + to create your first budget",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(budgets) { budget ->
                    BudgetRow(
                        budget = budget,
                        onDelete = { selectedBudgetForDelete = budget },
                        repo = repo,
                        monthStart = currentMonthStart,
                        db = db,
                        syncManager = syncManager
                    )
                }
            }
        }
    }

    if (showAddDialog) {
        AddBudgetDialog(
            monthStart = currentMonthStart,
            repo = repo,
            syncManager = syncManager,
            onDismiss = { showAddDialog = false }
        )
    }

    selectedBudgetForDelete?.let { budget ->
        DeleteBudgetDialog(
            budget = budget,
            repo = repo,
            onDismiss = { selectedBudgetForDelete = null }
        )
    }
}

@Composable
private fun BudgetRow(
    budget: BudgetEntity,
    onDelete: () -> Unit,
    repo: BudgetRepository,
    monthStart: Long,
    db: ExpenseDatabase,
    syncManager: SyncManager? = null
) {
    var progress by remember { mutableStateOf(0.0) }
    var spentAmount by remember { mutableStateOf(0.0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var showEditDialog by remember { mutableStateOf(false) }  // ✅ NEW

    // Compute real progress from expenses (run every 3 seconds)
    LaunchedEffect(budget.id, monthStart) {
        while (true) {
            try {
                val expenseRepo = ExpenseRepository(db.expenseDao())

                // Month end: last day of month at 23:59:59
                val monthEnd = run {
                    val cal = Calendar.getInstance()
                    cal.timeInMillis = monthStart
                    cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                    cal.set(Calendar.HOUR_OF_DAY, 23)
                    cal.set(Calendar.MINUTE, 59)
                    cal.set(Calendar.SECOND, 59)
                    cal.set(Calendar.MILLISECOND, 999)
                    cal.timeInMillis
                }

                // Get actual spent amount for this category
                val actualSpent = expenseRepo.getSpentAmountByCategory(
                    category = budget.category,
                    start = monthStart,
                    end = monthEnd
                )

                spentAmount = actualSpent
                progress = if (budget.amount > 0) {
                    (actualSpent / budget.amount).coerceAtMost(1.0)
                } else 0.0

                errorMessage = null
                android.util.Log.d("BUDGET_ROW", "Budget ${budget.category}: spent=₹$actualSpent, progress=${progress*100}%")

            } catch (e: Exception) {
                errorMessage = e.message
                android.util.Log.e("BUDGET_ROW", "Error computing progress for ${budget.category}: ${e.message}")
            }

            // Update every 3 seconds
            kotlinx.coroutines.delay(3000)
        }
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (progress > 1.0)
                MaterialTheme.colorScheme.errorContainer
            else
                MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = budget.category.name.replace("_", " ").capitalize(java.util.Locale.ROOT),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "₹${String.format("%.0f", budget.amount)}",
                        style = MaterialTheme.typography.headlineSmall
                    )
                }
                // Edit + Delete buttons
                Row {
                    IconButton(onClick = { showEditDialog = true }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit budget")
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete budget")
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress.toFloat() },
                modifier = Modifier.fillMaxWidth(),
                color = if (progress > 1.0)
                    MaterialTheme.colorScheme.error
                else
                    MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "₹${String.format("%.0f", spentAmount)} spent",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "${String.format("%.1f", progress * 100)}%",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (progress > 1.0)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.primary
                )
            }

            // Debug info (remove in production)
            errorMessage?.let {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Debug: $it",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }
    }
    // ✅ NEW: Edit dialog
    if (showEditDialog) {
        EditBudgetDialog(
            budget = budget,
            repo = repo,
            syncManager = syncManager,
            onDismiss = { showEditDialog = false }
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetDialog(
    monthStart: Long,
    repo: BudgetRepository,
    syncManager: SyncManager?,
    onDismiss: () -> Unit
) {
    var selectedCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var amountText by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                ExposedDropdownMenuBox(
                    expanded = categoryExpanded,
                    onExpandedChange = { categoryExpanded = !categoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name?.replace("_", " ") ?: "Select category",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        modifier = Modifier.menuAnchor(),
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryExpanded) }
                    )
                    ExposedDropdownMenu(
                        expanded = categoryExpanded,
                        onDismissRequest = { categoryExpanded = false }
                    ) {
                        ExpenseCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name.replace("_", " ").capitalize(Locale.ROOT)) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Budget Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("₹") }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (selectedCategory != null && amount != null && amount > 0) {
                        scope.launch {
                            val existing = repo.getBudgetForCategory(selectedCategory!!, monthStart)
                            if (existing != null) {
                                // Budget already exists - pass to parent to show duplicate dialog
                                // For now, just allow update (same as edit)
                                repo.upsertBudget(selectedCategory!!, amount, monthStart, syncManager)
                                onDismiss()
                            } else {
                                // New budget - save normally
                                repo.upsertBudget(selectedCategory!!, amount, monthStart, syncManager)
                                onDismiss()
                            }
                        }
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun DeleteBudgetDialog(
    budget: BudgetEntity,
    repo: BudgetRepository,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Budget") },
        text = {
            Text(
                text = "Delete ${budget.category.name.replace("_", " ")} budget?",
                style = MaterialTheme.typography.bodyLarge
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        repo.deleteBudget(budget.category, budget.monthStart)
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditBudgetDialog(
    budget: BudgetEntity,
    repo: BudgetRepository,
    syncManager: SyncManager?,
    onDismiss: () -> Unit
) {
    var amountText by remember { mutableStateOf(budget.amount.toString()) }
    val scope = rememberCoroutineScope()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Category: ${budget.category.name.replace("_", " ").capitalize(Locale.ROOT)}",
                    style = MaterialTheme.typography.bodyMedium
                )

                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Budget Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("₹") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount != null && amount > 0) {
                        scope.launch {
                            repo.upsertBudget(budget.category, amount, budget.monthStart, syncManager)
                            onDismiss()
                        }
                    }
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

