package com.cobfa.app.ui.budget

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cobfa.app.data.local.entity.BudgetEntity
import com.cobfa.app.domain.model.ExpenseCategory
import java.util.Locale
import kotlin.math.roundToInt

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetScreen(vm: BudgetViewModel) {
    val ui by vm.uiState.collectAsState()
    val isCurrentMonth = ui.isCurrentMonth
    var showAddDialog by remember { mutableStateOf(false) }
    var selectedBudgetForDelete by remember { mutableStateOf<BudgetEntity?>(null) }
    var editBudget by remember { mutableStateOf<BudgetEntity?>(null) }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    LaunchedEffect(Unit) {
        vm.restoreBudgetsFromFirestoreOnce()
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Budgets") },
                actions = {
                    if (ui.isCurrentMonth) {
                        IconButton(onClick = { showAddDialog = true }) {
                            Icon(Icons.Default.Add, contentDescription = "Add budget")
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.prevMonth() }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous month")
                }
                Text(ui.monthLabel, style = MaterialTheme.typography.titleMedium)
                IconButton(onClick = { vm.nextMonth() }, enabled = ui.canGoNextMonth) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next month")
                }
            }

            if (ui.rows.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("No budgets set yet", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Tap + to create your first budget",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ui.rows, key = { it.budget.id }) { row ->
                        BudgetRow(
                            budget = row.budget,
                            spentAmount = row.spent,
                            progress = row.progress,
                            canEdit = isCurrentMonth,
                            canDelete = isCurrentMonth,
                            onEdit = { editBudget = row.budget },
                            onDelete = { selectedBudgetForDelete = row.budget }
                        )
                    }
                }
            }
        }
    }
    if (showAddDialog) {
        AddBudgetDialog(
            onDismiss = { showAddDialog = false },
            onSave = { cat, amt ->
                vm.addOrUpdateBudget(cat, amt)
                showAddDialog = false
            }
        )
    }

    editBudget?.let { b ->
        EditBudgetDialog(
            budget = b,
            onDismiss = { editBudget = null },
            onUpdate = { amt ->
                vm.addOrUpdateBudget(b.category, amt)
                editBudget = null
            }
        )
    }

    selectedBudgetForDelete?.let { budget ->
        DeleteBudgetDialog(
            budget = budget,
            onDismiss = { selectedBudgetForDelete = null },
            onConfirm = {
                vm.deleteBudget(budget.category)
                selectedBudgetForDelete = null
            }
        )
    }
}

@Composable
private fun BudgetRow(
    budget: BudgetEntity,
    spentAmount: Double,
    progress: Double,
    canEdit: Boolean,
    canDelete: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val pct = if (budget.amount > 0) (spentAmount / budget.amount * 100.0) else 0.0
    val isWarn = pct >= 80.0 && pct < 100.0
    val isOver = pct >= 100.0

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (isOver) MaterialTheme.colorScheme.errorContainer
            else MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = budget.category.name.replace("_", " ").lowercase()
                            .replaceFirstChar { it.titlecase(Locale.getDefault()) },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text("₹${budget.amount.roundToInt()}", style = MaterialTheme.typography.titleLarge)
                }
                Row {
                    IconButton(onClick = onEdit, enabled = canEdit) { Icon(Icons.Default.Edit, contentDescription = "Edit budget") }
                    IconButton(onClick = onDelete, enabled = canDelete) { Icon(Icons.Default.Delete, contentDescription = "Delete budget") }
                }
            }

            Spacer(Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress.toFloat().coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth(),
                color = when {
                    isOver -> MaterialTheme.colorScheme.error
                    isWarn -> MaterialTheme.colorScheme.tertiary
                    else -> MaterialTheme.colorScheme.primary
                },
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Spacer(Modifier.height(8.dp))

            val remaining = (budget.amount - spentAmount)
            val remainingText = if (remaining >= 0) "₹${remaining.roundToInt()} left"
            else "₹${(-remaining).roundToInt()} over"

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = remainingText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (remaining < 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "₹${spentAmount.roundToInt()} / ₹${budget.amount.roundToInt()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = when {
                        isOver -> MaterialTheme.colorScheme.error
                        isWarn -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddBudgetDialog(
    onDismiss: () -> Unit,
    onSave: (ExpenseCategory, Double) -> Unit
) {
    var selectedCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var amountText by remember { mutableStateOf("") }
    var categoryExpanded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

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
                        value = selectedCategory?.name?.replace("_", " ")
                            ?.lowercase()?.replaceFirstChar { it.titlecase(Locale.getDefault()) }
                            ?: "Select category",
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
                        ExpenseCategory.entries
                            .sortedBy { it.name }
                            .forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name.replace("_", " ")) },
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
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == '.' }
                        amountText = filtered
                        error = null
                    },
                    label = { Text("Budget amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("₹") },
                    supportingText = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                enabled = selectedCategory != null && amountText.toDoubleOrNull()?.let { it > 0 } == true,
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (selectedCategory == null || amt == null || amt <= 0) {
                        error = "Enter a valid category and amount"
                        return@Button
                    }
                    onSave(selectedCategory!!, amt)
                }
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun EditBudgetDialog(
    budget: BudgetEntity,
    onDismiss: () -> Unit,
    onUpdate: (Double) -> Unit
) {
    var amountText by remember { mutableStateOf(budget.amount.toString()) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Budget") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Category: ${budget.category.name.replace("_", " ")}")
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it; error = null },
                    label = { Text("Budget amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    prefix = { Text("₹") },
                    supportingText = { error?.let { Text(it, color = MaterialTheme.colorScheme.error) } },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                enabled = amountText.toDoubleOrNull()?.let { it > 0 } == true,
                onClick = {
                    val amt = amountText.toDoubleOrNull()
                    if (amt == null || amt <= 0) {
                        error = "Enter a valid amount"
                        return@Button
                    }
                    onUpdate(amt)
                }
            ) { Text("Update") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun DeleteBudgetDialog(
    budget: BudgetEntity,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Budget") },
        text = { Text("Delete ${budget.category.name.replace("_", " ")} budget?") },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) { Text("Delete") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
