package com.cobfa.app.ui.expense.list

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cobfa.app.data.local.entity.ExpenseEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ExpenseListScreen(
    vm: ExpenseListViewModel
) {
    val expenses by vm.expenses.collectAsState()

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
