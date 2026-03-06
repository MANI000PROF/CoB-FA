package com.cobfa.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.cobfa.app.domain.model.MonthlySummary

@Composable
fun BalanceDetailSheet(
    summary: MonthlySummary,
    steps: List<DashboardViewModel.BalanceStep>,
    loading: Boolean,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Balance breakdown") },
        text = {
            if (loading) {
                Text("Loading breakdown…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    KeyValueRow("Income", "₹${"%.0f".format(summary.income)}")
                    KeyValueRow("Total expense", "₹${"%.0f".format(summary.expense)}")

                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    Text("How your money was used:", style = MaterialTheme.typography.labelMedium)

                    steps.forEachIndexed { index, step ->
                        KeyValueRow(step.label, "₹${"%.0f".format(step.remainingAfter)}")
                        if (index != steps.lastIndex) {
                            HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

                    val finalColor =
                        if (summary.balance >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                    KeyValueRow(
                        "Final balance",
                        "₹${"%.0f".format(summary.balance)}",
                        valueColor = finalColor
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } }
    )
}

@Composable
private fun KeyValueRow(
    label: String,
    value: String,
    valueColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.padding(start = 12.dp))
        Text(value, color = valueColor)
    }
}