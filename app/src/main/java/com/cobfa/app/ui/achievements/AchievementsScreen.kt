package com.cobfa.app.ui.achievements

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.cobfa.app.data.local.entity.AchievementEntity
import com.cobfa.app.data.local.entity.PointsEventEntity

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AchievementsScreen(
    pointsBalance: Int,
    achievements: List<AchievementEntity>,
    recentPoints: List<PointsEventEntity>,
    modifier: Modifier = Modifier
) {
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Achievements") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            SectionCard {
                Text("Points balance", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.padding(top = 4.dp))
                Text(pointsBalance.toString(), style = MaterialTheme.typography.headlineSmall)
            }

            SectionCard {
                Text("Badges unlocked", style = MaterialTheme.typography.titleMedium)

                if (achievements.isEmpty()) {
                    Text(
                        "No badges yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column {
                        achievements.forEachIndexed { index, a ->
                            ListItem(
                                headlineContent = { Text(a.title) },
                                supportingContent = {
                                    Text(a.description, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            if (index != achievements.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }

            SectionCard {
                Text("Recent points", style = MaterialTheme.typography.titleMedium)

                if (recentPoints.isEmpty()) {
                    Text(
                        "No points events yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Column {
                        recentPoints.forEachIndexed { index, e ->
                            val deltaText = "${if (e.delta >= 0) "+" else ""}${e.delta}"
                            val deltaColor =
                                if (e.delta >= 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error

                            ListItem(
                                headlineContent = { Text(e.reason) },
                                supportingContent = {
                                    e.details?.let { d ->
                                        Text(d, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                },
                                trailingContent = {
                                    Text(
                                        deltaText,
                                        style = MaterialTheme.typography.titleSmall,
                                        color = deltaColor
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            if (index != recentPoints.lastIndex) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp, end = 16.dp),
                                    thickness = 1.dp,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionCard(
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            content()
        }
    }
}
