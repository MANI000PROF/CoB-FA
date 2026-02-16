package com.cobfa.app.ui.expense.list

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.domain.model.ExpenseCategory
import com.cobfa.app.domain.model.ExpenseType
import com.cobfa.app.ui.expense.category.CategoryPickerBottomSheet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseListScreen(vm: ExpenseListViewModel) {
    val expenses by vm.expenses.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val categoryFilter by vm.categoryFilter.collectAsState()
    val onlyUncat by vm.onlyUncategorized.collectAsState()
    val sortMode by vm.sortMode.collectAsState()

    var categoryMenuExpanded by remember { mutableStateOf(false) }
    val hasActiveFilters =
        searchQuery.isNotBlank() || categoryFilter != null || onlyUncat || sortMode != ExpenseListViewModel.SortMode.NEWEST

    var selectedExpense by remember { mutableStateOf<ExpenseEntity?>(null) }
    var showDetailsSheet by remember { mutableStateOf(false) }
    var showCategoryPicker by remember { mutableStateOf(false) }
    var showEditSheet by remember { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            Text("Your Expenses", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = searchQuery,
                onValueChange = vm::updateSearchQuery,
                label = { Text("Search merchant or category") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                trailingIcon = {
                    if (searchQuery.isNotBlank()) {
                        IconButton(onClick = { vm.updateSearchQuery("") }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                }
            )

            Spacer(Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = categoryFilter == null,
                    onClick = { vm.updateCategoryFilter(null) },
                    label = { Text("All") }
                )

                Box {
                    FilterChip(
                        selected = categoryFilter != null,
                        onClick = { categoryMenuExpanded = true },
                        label = {
                            Text(
                                categoryFilter?.name
                                    ?.replace("_", " ")
                                    ?.lowercase()
                                    ?.replaceFirstChar { it.uppercase() }
                                    ?: "Category"
                            )
                        }
                    )

                    DropdownMenu(
                        expanded = categoryMenuExpanded,
                        onDismissRequest = { categoryMenuExpanded = false },
                        properties = PopupProperties(focusable = true)
                    ) {
                        DropdownMenuItem(
                            text = { Text("All categories") },
                            onClick = {
                                vm.updateCategoryFilter(null)
                                categoryMenuExpanded = false
                            }
                        )
                        ExpenseCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        cat.name.replace("_", " ")
                                            .lowercase()
                                            .replaceFirstChar { it.uppercase() }
                                    )
                                },
                                onClick = {
                                    vm.updateCategoryFilter(cat)
                                    categoryMenuExpanded = false
                                }
                            )
                        }
                    }
                }

                FilterChip(
                    selected = onlyUncat,
                    onClick = { vm.setOnlyUncategorized(!onlyUncat) },
                    label = { Text("Uncategorized") }
                )

                FilterChip(
                    selected = sortMode == ExpenseListViewModel.SortMode.NEWEST,
                    onClick = { vm.toggleSort() },
                    label = { Text(if (sortMode == ExpenseListViewModel.SortMode.NEWEST) "Newest" else "Oldest") }
                )

                val excludedMode by vm.excludedMode.collectAsState()

                FilterChip(
                    selected = excludedMode == ExpenseListViewModel.ExcludedMode.INCLUDE_EXCLUDED,
                    onClick = {
                        vm.setExcludedMode(
                            if (excludedMode == ExpenseListViewModel.ExcludedMode.INCLUDE_EXCLUDED)
                                ExpenseListViewModel.ExcludedMode.ACTIVE_ONLY
                            else ExpenseListViewModel.ExcludedMode.INCLUDE_EXCLUDED
                        )
                    },
                    label = { Text("Include excluded") }
                )

                FilterChip(
                    selected = excludedMode == ExpenseListViewModel.ExcludedMode.EXCLUDED_ONLY,
                    onClick = {
                        vm.setExcludedMode(
                            if (excludedMode == ExpenseListViewModel.ExcludedMode.EXCLUDED_ONLY)
                                ExpenseListViewModel.ExcludedMode.ACTIVE_ONLY
                            else ExpenseListViewModel.ExcludedMode.EXCLUDED_ONLY
                        )
                    },
                    label = { Text("Excluded only") }
                )

                if (hasActiveFilters) {
                    TextButton(onClick = { vm.clearFilters() }) { Text("Clear") }
                }
            }

            Spacer(Modifier.height(12.dp))

            if (expenses.isEmpty()) {
                if (hasActiveFilters) {
                    Text("No results for your filters.", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { vm.clearFilters() }) { Text("Clear filters") }
                } else {
                    Text("No expenses recorded yet.", style = MaterialTheme.typography.bodyMedium)
                }
            } else {
                val dayFmt = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }
                val grouped = remember(expenses) { expenses.groupBy { dayFmt.format(Date(it.timestamp)) } }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 8.dp)
                ) {
                    grouped.forEach { (day, itemsForDay) ->
                        item(key = "day:$day") {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp, bottom = 2.dp)
                            )
                        }
                        items(itemsForDay, key = { it.id }) { expense ->
                            ExpenseRow(
                                expense = expense,
                                onClick = {
                                    selectedExpense = expense
                                    showDetailsSheet = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Details sheet
    if (showDetailsSheet && selectedExpense != null) {
        ExpenseDetailsBottomSheet(
            expense = selectedExpense!!,
            onDismiss = { showDetailsSheet = false },
            onChangeCategory = { showCategoryPicker = true },
            onEdit = { showEditSheet = true },
            onDelete = {
                val deletedId = selectedExpense!!.id
                vm.softDeleteExpense(deletedId)
                showDetailsSheet = false

                scope.launch {
                    val res = snackbarHostState.showSnackbar(
                        message = "Expense excluded",
                        actionLabel = "Undo",
                        withDismissAction = true
                    )
                    if (res == SnackbarResult.ActionPerformed) {
                        vm.restoreExpense(deletedId)
                    }
                }
            }
        )
    }

    // Category picker
    if (showCategoryPicker && selectedExpense != null) {
        CategoryPickerBottomSheet(
            onCategorySelected = { cat ->
                vm.updateExpenseCategory(selectedExpense!!.id, cat)
                showCategoryPicker = false
                showDetailsSheet = false
            },
            onDismiss = { showCategoryPicker = false }
        )
    }

    // Edit sheet (merchant + amount)
    if (showEditSheet && selectedExpense != null) {
        EditExpenseBottomSheet(
            expense = selectedExpense!!,
            onDismiss = { showEditSheet = false },
            onSave = { newMerchant, newAmount ->
                val e = selectedExpense!!
                vm.editExpense(
                    id = e.id,
                    merchant = newMerchant,
                    amount = newAmount,
                    timestamp = e.timestamp // unchanged in this patch
                )
                showEditSheet = false
                showDetailsSheet = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ExpenseRow(
    expense: ExpenseEntity,
    onClick: () -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    val isCredit = expense.type == ExpenseType.CREDIT
    val amountColor = if (isCredit) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val typeLabel = if (isCredit) "Credit" else "Debit"

    val categoryLabel = (expense.category?.name ?: "Uncategorized").replace("_", " ")
    val srcLabel = expense.source?.toString()?.uppercase(Locale.getDefault()) // adjust if non-null

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        ListItem(
            headlineContent = {
                Text(
                    text = expense.merchant ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            },
            supportingContent = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    // Category as subtle text line (keeps the chip row cleaner)
                    Text(
                        text = categoryLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Tags: wrap nicely, don’t push trailing amount around
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (expense.editedAt != null) SmallBadge("Edited")
                        if (!srcLabel.isNullOrBlank()) SmallBadge(srcLabel)
                        SmallBadge(typeLabel) // you said you want type visible too
                    }

                    Text(
                        text = formatter.format(Date(expense.timestamp)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            trailingContent = {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "₹${String.format("%.0f", expense.amount)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = amountColor
                    )
                }
            }
        )
    }
}

@Composable
private fun SmallBadge(text: String) {
    AssistChip(
        onClick = { },
        enabled = false,
        label = { Text(text, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
        )
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExpenseDetailsBottomSheet(
    expense: ExpenseEntity,
    onDismiss: () -> Unit,
    onChangeCategory: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val fmt = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val isCredit = expense.type == ExpenseType.CREDIT
    val typeLabel = if (isCredit) "Credit" else "Debit"

    var confirmDelete by remember { mutableStateOf(false) }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Exclude this expense?") },
            text = { Text("This will remove it from your lists and analytics. You can undo right after excluding.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) { Text("Exclude") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Cancel") }
            }
        )
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Expense details", style = MaterialTheme.typography.titleMedium)
                if (expense.editedAt != null) {
                    Text(
                        "Edited",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Text("Merchant: ${expense.merchant ?: "Unknown"}")
            Text("Amount: ₹${String.format("%.0f", expense.amount)}")
            Text("Type: $typeLabel")
            Text("Source: ${expense.source}")
            Text("Date: ${fmt.format(Date(expense.timestamp))}")
            Text("Category: ${(expense.category?.name ?: "Uncategorized").replace("_", " ")}")

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = onChangeCategory,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Change category") }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Edit") }

            Spacer(Modifier.height(10.dp))

            OutlinedButton(
                onClick = { confirmDelete = true },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Exclude") }

            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditExpenseBottomSheet(
    expense: ExpenseEntity,
    onDismiss: () -> Unit,
    onSave: (merchant: String?, amount: Double) -> Unit
) {
    var merchant by remember(expense.id) { mutableStateOf(expense.merchant.orEmpty()) }
    var amountText by remember(expense.id) { mutableStateOf(String.format("%.0f", expense.amount)) }
    var amountError by remember { mutableStateOf<String?>(null) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(16.dp)) {
            Text("Edit expense", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            OutlinedTextField(
                value = merchant,
                onValueChange = { merchant = it },
                label = { Text("Merchant") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(Modifier.height(10.dp))

            OutlinedTextField(
                value = amountText,
                onValueChange = {
                    amountText = it
                    amountError = null
                },
                label = { Text("Amount (₹)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                isError = amountError != null,
                supportingText = {
                    amountError?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            )

            Spacer(Modifier.height(16.dp))

            Button(
                onClick = {
                    val amt = amountText.trim().toDoubleOrNull()
                    if (amt == null || amt < 0.0) {
                        amountError = "Enter a valid amount"
                        return@Button
                    }
                    onSave(merchant.trim().ifBlank { null }, amt)
                },
                modifier = Modifier.fillMaxWidth()
            ) { Text("Save") }

            Spacer(Modifier.height(12.dp))
        }
    }
}
