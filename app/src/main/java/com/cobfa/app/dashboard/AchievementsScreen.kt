package com.cobfa.app.ui.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cobfa.app.data.local.entity.AchievementEntity
import com.cobfa.app.data.local.entity.PointsEventEntity
import kotlin.math.roundToInt

@Composable
fun AchievementsScreen(
    pointsBalance: Int,
    achievements: List<AchievementEntity>,
    recentPoints: List<PointsEventEntity>,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Achievements", style = MaterialTheme.typography.titleLarge)

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp)) {
                Text("Points balance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(4.dp))
                Text(pointsBalance.toString(), style = MaterialTheme.typography.headlineSmall)
            }
        }

        Text("Badges unlocked", style = MaterialTheme.typography.titleMedium)
        if (achievements.isEmpty()) {
            Text("No badges yet.")
        } else {
            achievements.forEach {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(it.title, style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(it.description, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }

        Text("Recent points", style = MaterialTheme.typography.titleMedium)
        if (recentPoints.isEmpty()) {
            Text("No points events yet.")
        } else {
            recentPoints.forEach {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${if (it.delta >= 0) "+" else ""}${it.delta}  •  ${it.reason}")
                        it.details?.let { d -> Text(d, style = MaterialTheme.typography.bodySmall) }
                    }
                }
            }
        }
    }
}
