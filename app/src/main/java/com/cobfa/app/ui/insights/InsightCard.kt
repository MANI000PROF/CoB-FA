package com.cobfa.app.ui.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cobfa.app.domain.model.InsightSeverity
import com.cobfa.app.domain.model.PersonalizedInsight

@Composable
fun InsightCard(
    insights: List<PersonalizedInsight>,
    modifier: Modifier = Modifier
) {
//    if (insights.isEmpty()) return
    // Always show the card (so you can verify wiring)

    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (insights.isEmpty()) {
                Text("No insights yet. Confirm a few expenses first.", style = MaterialTheme.typography.bodySmall)
            }
            else {
                Text("Personalized insights", style = MaterialTheme.typography.titleMedium)

                insights.take(3).forEach { ins ->
                    Row(modifier = Modifier.fillMaxWidth()) {
                        val tag = when (ins.severity) {
                            InsightSeverity.INFO -> "INFO"
                            InsightSeverity.WARN -> "WARN"
                            InsightSeverity.RISK -> "RISK"
                        }
                        Text(tag, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 8.dp))
                        Column {
                            Text(ins.title, style = MaterialTheme.typography.bodyMedium)
                            Text(ins.message, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
