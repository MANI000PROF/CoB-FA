package com.cobfa.app.ui.leaderboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
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

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()
    val youIndex = rows.indexOfFirst { it.uid == currentUid }
    val youRank = if (youIndex >= 0) youIndex + 1 else null
    val youRow = if (youIndex >= 0) rows[youIndex] else null

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Leaderboard") },
                scrollBehavior = scrollBehavior
            )
        }
    ) { padding ->

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            val scopeText =
                if (mode == LeaderboardViewModel.Mode.CITY) "City • $city, $state" else "State • $state"
            Text(
                scopeText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

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

            SectionCard {
                Text("You", style = MaterialTheme.typography.titleMedium)

                if (youRow == null) {
                    Text(
                        "Your rank will appear here once loaded.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    ListItem(
                        leadingContent = {
                            val medal = medalFor(youRank!!)
                            if (medal != null) {
                                Icon(medal, null, tint = medalTint(youRank), modifier = Modifier.padding(top = 2.dp))
                            } else {
                                Text("#$youRank", style = MaterialTheme.typography.titleMedium)
                            }
                        },
                        headlineContent = { Text(youRow.username, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        supportingContent = {
                            val region =
                                if (mode == LeaderboardViewModel.Mode.CITY) "${youRow.city}, ${youRow.state}" else youRow.state
                            Text(region, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                        trailingContent = {
                            Text("₹", color = Color.Transparent) // keeps trailing slot stable; optional
                            Column(horizontalAlignment = Alignment.End) {
                                AssistChip(onClick = { }, enabled = false, label = { Text(tier(youRow.pointsBalance)) })
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "${youRow.pointsBalance} pts",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }

            when {
                loading -> {
                    SectionCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(strokeWidth = 3.dp)
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text("Loading leaderboard…", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Fetching public ranks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                error != null -> {
                    SectionCard {
                        Text("Couldn’t load leaderboard", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                        Spacer(Modifier.height(12.dp))
                        Button(onClick = onReload) { Text("Retry") }
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
                    SectionCard {
                        Text("No users yet", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Once multiple users start earning points, rankings will show here.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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
                                else -> MaterialTheme.colorScheme.surfaceVariant
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
                                                onClick = { },
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
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
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

private fun tier(points: Int): String = when {
    points >= 300 -> "Platinum"
    points >= 150 -> "Gold"
    points >= 50 -> "Silver"
    else -> "Bronze"
}

private fun medalFor(rank: Int): ImageVector? = when (rank) {
    1, 2, 3 -> Icons.Default.EmojiEvents
    else -> null
}

@Composable
private fun medalTint(rank: Int) = when (rank) {
    1 -> MaterialTheme.colorScheme.tertiary      // gold-ish
    2 -> MaterialTheme.colorScheme.secondary     // silver-ish
    3 -> MaterialTheme.colorScheme.primary       // bronze-ish (theme-based)
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

