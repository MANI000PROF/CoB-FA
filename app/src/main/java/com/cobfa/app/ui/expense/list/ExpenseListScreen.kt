package com.cobfa.app.ui.expense.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cobfa.app.data.local.entity.ExpenseEntity
import com.cobfa.app.domain.model.ExpenseCategory
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExpenseListScreen(
    vm: ExpenseListViewModel
) {
    val expenses by vm.expenses.collectAsState()
    val searchQuery by vm.searchQuery.collectAsState()
    val categoryFilter by vm.categoryFilter.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {

        Text(
            text = "Your Expenses",
            style = MaterialTheme.typography.headlineSmall
        )

        Spacer(Modifier.height(16.dp))

        // Search and Filter Controls
        OutlinedTextField(
            value = searchQuery,
            onValueChange = vm::updateSearchQuery,
            label = { Text("Search merchant or category") },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            trailingIcon = {
                if (searchQuery.isNotBlank()) {
                    IconButton(onClick = { vm.updateSearchQuery("") }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear search")
                    }
                }
            }
        )

        // Filter chips row
        FlowRow(
            modifier = Modifier.padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            FilterChip(
                selected = categoryFilter == null,
                onClick = { vm.updateCategoryFilter(null) },
                label = { Text("All Categories") }
            )
            ExpenseCategory.entries.forEach { category ->
                FilterChip(
                    selected = categoryFilter == category,
                    onClick = { vm.updateCategoryFilter(category) },
                    label = { Text(category.name.replace("_", " ").capitalize()) }
                )
            }
        }

        // Clear filters button
        if (searchQuery.isNotBlank() || categoryFilter != null) {
            TextButton(
                onClick = { vm.clearFilters() },
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text("Clear Filters")
            }
        }

        if (expenses.isEmpty()) {
            Text(
                text = "No expenses recorded yet",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            LazyColumn {
                items(expenses) { expense: ExpenseEntity ->
                    ExpenseRow(expense)
                }
            }
        }
    }
}

@Composable
private fun ExpenseRow(expense: ExpenseEntity) {
    val formatter = remember {
        SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
    }

    val typeColor =
        if (expense.type == com.cobfa.app.domain.model.ExpenseType.CREDIT)
            MaterialTheme.colorScheme.primary
        else
            MaterialTheme.colorScheme.error


    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = expense.merchant ?: "Unknown",
                    style = MaterialTheme.typography.titleMedium
                )

                expense.category?.let {
                    Text(
                        text = it.name.replace("_", " "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary
                    )
                }

                Text(
                    text = formatter.format(Date(expense.timestamp)),
                    style = MaterialTheme.typography.bodySmall
                )
            }


            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = expense.type.name,
                    color = typeColor,
                    style = MaterialTheme.typography.labelSmall
                )

                Text(
                    text = "₹${expense.amount}",
                    style = MaterialTheme.typography.titleMedium,
                    color = typeColor
                )
            }

        }
    }
}
