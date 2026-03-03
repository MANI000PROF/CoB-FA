package com.cobfa.app.ui.leaderboard

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.cobfa.app.data.remote.FirestoreService

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LeaderboardScreen(
    currentUid: String,
    city: String,
    state: String,
    mode: LeaderboardViewModel.Mode,
    rows: List<FirestoreService.PublicUser>,
    loading: Boolean,
    error: String?,
    onModeChange: (LeaderboardViewModel.Mode) -> Unit,
    onReload: () -> Unit,
    modifier: Modifier = Modifier
) {
    LaunchedEffect(mode, city, state) {
        when (mode) {
            LeaderboardViewModel.Mode.CITY -> if (city.isNotBlank() && state.isNotBlank()) onReload()
            LeaderboardViewModel.Mode.STATE -> if (state.isNotBlank()) onReload()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text("Leaderboard", style = MaterialTheme.typography.titleLarge)

        val scopeText =
            if (mode == LeaderboardViewModel.Mode.CITY) "City • $city, $state" else "State • $state"
        Text(scopeText, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = mode == LeaderboardViewModel.Mode.CITY,
                onClick = { onModeChange(LeaderboardViewModel.Mode.CITY) },
                label = { Text("City") }
            )
            FilterChip(
                selected = mode == LeaderboardViewModel.Mode.STATE,
                onClick = { onModeChange(LeaderboardViewModel.Mode.STATE) },
                label = { Text("State") }
            )
        }

        if (mode == LeaderboardViewModel.Mode.CITY) {
            Text(
                "Tip: Leaderboards use public profile info (nickname + region).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // “You” card (placeholder for now; we’ll wire actual user rank next)
        Card(Modifier.fillMaxWidth()) {
            ListItem(
                headlineContent = { Text("You") },
                supportingContent = { Text("Your rank will appear here once loaded.") },
                trailingContent = { Text("—", style = MaterialTheme.typography.titleMedium) }
            )
        }

        when {
            loading -> {
                // simple skeleton feel
                repeat(5) {
                    Card(Modifier.fillMaxWidth()) {
                        ListItem(
                            headlineContent = { Text("Loading…") },
                            supportingContent = { Text(" ") },
                            trailingContent = { Text(" ") }
                        )
                    }
                }
            }

            error != null -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Couldn’t load leaderboard", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onReload) { Text("Retry") }
                    }
                }

                if (error.contains("index", ignoreCase = true)) {
                    Text(
                        "If this is a Firestore index issue, open the Logcat link to create the index and try again.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            rows.isEmpty() -> {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("No users yet", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Once multiple users start earning points, rankings will show here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            else -> {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    itemsIndexed(items = rows, key = { _, u -> u.uid }) { idx, u ->
                        val rank = idx + 1
                        val isYou = u.uid == currentUid

                        val container = when {
                            isYou -> MaterialTheme.colorScheme.secondaryContainer
                            rank <= 3 -> MaterialTheme.colorScheme.primaryContainer
                            else -> MaterialTheme.colorScheme.surface
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = container)
                        ) {
                            ListItem(
                                leadingContent = {
                                    Text("#$rank", style = MaterialTheme.typography.titleMedium)
                                },
                                headlineContent = {
                                    Text(u.username, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                supportingContent = {
                                    val region =
                                        if (mode == LeaderboardViewModel.Mode.CITY) "${u.city}, ${u.state}" else u.state
                                    Text(region, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                                trailingContent = {
                                    Column(horizontalAlignment = Alignment.End) {
                                        AssistChip(
                                            onClick = { /* no-op */ },
                                            enabled = false,
                                            label = { Text(tier(u.pointsBalance)) }
                                        )
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            "${u.pointsBalance} pts",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (isYou) {
                                            Text(
                                                "You",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun tier(points: Int): String = when {
    points >= 300 -> "Platinum"
    points >= 150 -> "Gold"
    points >= 50 -> "Silver"
    else -> "Bronze"
}

