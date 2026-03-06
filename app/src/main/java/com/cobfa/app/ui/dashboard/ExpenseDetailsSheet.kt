package com.cobfa.app.ui.dashboard

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cobfa.app.data.local.entity.ExpenseEntity

@Composable
fun ExpenseDetailSheet(
    expenses: List<ExpenseEntity>,
    onViewAll: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("This month’s expenses") },
        text = {
            Column(modifier = Modifier.heightIn(max = 420.dp)) {
                expenses.take(6).forEachIndexed { index, e ->
                    ListItem(
                        headlineContent = { Text(e.merchant ?: "Unknown") },
                        supportingContent = { Text("₹${"%.0f".format(e.amount)}") },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    if (index != minOf(expenses.size, 6) - 1) {
                        HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }

                if (expenses.size > 6) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "+ ${expenses.size - 6} more",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onViewAll) { Text("View all") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}
