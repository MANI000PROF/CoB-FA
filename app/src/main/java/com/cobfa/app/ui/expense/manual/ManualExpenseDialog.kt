package com.cobfa.app.ui.expense.manual

import android.util.Log
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.data.repository.SyncManager
import com.cobfa.app.domain.model.ExpenseCategory
import com.cobfa.app.domain.model.ExpenseSource
import com.cobfa.app.domain.model.ExpenseStatus
import com.cobfa.app.domain.model.ExpenseType
import com.cobfa.app.utils.ExpenseLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualExpenseDialog(
    onDismiss: () -> Unit,
    db: ExpenseDatabase,
    syncManager: SyncManager
) {
    var amountText by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(ExpenseType.DEBIT) }
    var selectedCategory by remember { mutableStateOf<ExpenseCategory?>(null) }
    var merchantText by remember { mutableStateOf("") }
    var isCategoryExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Expense") },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text("Amount") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                // Simple DEBIT/CREDIT toggle compatible with Material3 1.3.0
                Column(modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("Type")
                    Row(modifier = Modifier.padding(top = 4.dp)) {
                        FilterChip(
                            selected = selectedType == ExpenseType.DEBIT,
                            onClick = { selectedType = ExpenseType.DEBIT },
                            label = { Text("Debit") }
                        )
                        Spacer(Modifier.padding(horizontal = 4.dp))
                        FilterChip(
                            selected = selectedType == ExpenseType.CREDIT,
                            onClick = { selectedType = ExpenseType.CREDIT },
                            label = { Text("Credit") }
                        )
                    }
                }

                ExposedDropdownMenuBox(
                    expanded = isCategoryExpanded,
                    onExpandedChange = { isCategoryExpanded = !isCategoryExpanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name?.replace("_", " ") ?: "Select category",
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Category") },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isCategoryExpanded)
                        }
                    )
                    ExposedDropdownMenu(
                        expanded = isCategoryExpanded,
                        onDismissRequest = { isCategoryExpanded = false }
                    ) {
                        ExpenseCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat.name.replace("_", " ")) },
                                onClick = {
                                    selectedCategory = cat
                                    isCategoryExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = merchantText,
                    onValueChange = { merchantText = it },
                    label = { Text("Merchant / Notes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        ExpenseLogger.logValidationFailed(
                            "manual_expense",
                            "invalid_amount",
                            "Amount: $amountText"
                        )
                        return@Button
                    }

                    // Fire-and-forget insert + sync
                    CoroutineScope(Dispatchers.IO).launch {
                        try {
                            val repo = ExpenseRepository(db.expenseDao(), syncManager)

                            val expense = ExpenseEntity(
                                amount = amount,
                                type = selectedType,
                                category = selectedCategory,
                                merchant = merchantText.ifBlank { null },
                                timestamp = System.currentTimeMillis(),
                                source = ExpenseSource.MANUAL,
                                status = ExpenseStatus.CONFIRMED,
                                createdAt = System.currentTimeMillis(),
                                smsHash = null
                            )

                            val id = repo.insertExpense(expense)
                            // For confirmed manual expenses, also sync to Firestore
                            syncManager.syncConfirmedExpense(id)
                            Log.d("MANUAL_EXPENSE", "Inserted manual expense id=$id")
                        } catch (e: Exception) {
                            ExpenseLogger.logDatabaseError(
                                "manualExpenseInsert",
                                e.message ?: "Unknown error"
                            )
                        }
                    }

                    onDismiss()
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
