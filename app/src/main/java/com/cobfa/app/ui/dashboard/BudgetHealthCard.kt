package com.cobfa.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ButtonDefaults.filledTonalButtonColors
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun BudgetHealthCard(
    health: DashboardViewModel.BudgetHealthUi,
    onViewBudgets: () -> Unit
) {
    if (health.totalBudgets == 0) {
        FilledTonalButton(
            onClick = onViewBudgets,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Create your first budget")
        }
        return
    }

    val successRate = if (health.totalBudgets > 0) {
        (health.withinBudget * 100f / health.totalBudgets).toInt()
    } else 0

    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // Left column takes remaining space
            Column(modifier = Modifier.weight(1f)) {
                Text("Budget health", style = MaterialTheme.typography.titleMedium)
                Text(
                    "${successRate}% on track",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (successRate >= 80) MaterialTheme.colorScheme.primary
                    else if (successRate >= 50) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.error
                )
            }

            // Progress bar with proper padding
            androidx.compose.material3.LinearProgressIndicator(
                progress = { successRate / 100f },
                modifier = Modifier
                    .width(80.dp)  // Slightly wider for balance
                    .height(8.dp),
                color = if (successRate >= 80) MaterialTheme.colorScheme.primary
                else if (successRate >= 50) MaterialTheme.colorScheme.tertiary
                else MaterialTheme.colorScheme.error,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        // Detail breakdown (unchanged)
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${health.withinBudget}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("On track", style = MaterialTheme.typography.labelSmall)
            }

            Text(
                "of",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "${health.totalBudgets}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text("Total", style = MaterialTheme.typography.labelSmall)
            }
        }

        // CTAs (unchanged)
        if (health.overBudget > 0) {
            FilledTonalButton(
                onClick = onViewBudgets,
                colors = filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text("${health.overBudget} over budget")
            }
        } else {
            TextButton(
                onClick = onViewBudgets,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Keep it up → View all")
            }
        }
    }
}

