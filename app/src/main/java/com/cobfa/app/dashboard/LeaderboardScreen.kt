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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.cobfa.app.dashboard.LeaderboardViewModel
import com.cobfa.app.data.remote.FirestoreService

@Composable
fun LeaderboardScreen(
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
        Text(
            text = if (mode == LeaderboardViewModel.Mode.CITY) "City: $city, $state" else "State: $state",
            style = MaterialTheme.typography.bodyMedium
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(
                onClick = { onModeChange(LeaderboardViewModel.Mode.CITY) },
                label = { Text("City") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (mode == LeaderboardViewModel.Mode.CITY)
                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            )
            AssistChip(
                onClick = { onModeChange(LeaderboardViewModel.Mode.STATE) },
                label = { Text("State") },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = if (mode == LeaderboardViewModel.Mode.STATE)
                        MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                )
            )
        }

        if (loading) Text("Loading…")
        error?.let { Text(it, color = MaterialTheme.colorScheme.error) }

        if (!loading && error == null && rows.isEmpty()) {
            Text("No users yet.")
        }

        LazyColumn(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            itemsIndexed(
                items = rows,
                key = { _, u -> u.uid }   // stable key
            ) { idx, u ->
                Card(Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("#${idx + 1}", modifier = Modifier.padding(end = 12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(u.username, style = MaterialTheme.typography.titleSmall)
                            Text("${u.city}, ${u.state}", style = MaterialTheme.typography.bodySmall)
                        }
                        Text(u.pointsBalance.toString(), style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        if (error?.contains("index", ignoreCase = true) == true) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Leaderboard is initializing. Create the required Firestore index (from Logcat link) and retry.",
                style = MaterialTheme.typography.bodySmall
            )
        }
    }
}
