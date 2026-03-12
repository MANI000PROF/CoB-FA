package com.cobfa.app.ui.profile

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountInsightsScreen(
    onBack: () -> Unit
) {
    val profileVm: ProfileScreenViewModel = viewModel()
    val uiState by profileVm.uiState.collectAsStateWithLifecycle()
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                profileVm.refreshPreferenceState()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 16.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            InsightSummaryCard(
                title = "Financial snapshot",
                subtitle = "Your account and tracking overview"
            ) {
                InsightTile(
                    title = "Account status",
                    subtitle = if (uiState.isLoggedIn) "Active and synced" else "Not signed in",
                    icon = Icons.Default.VerifiedUser
                )
                InsightTile(
                    title = "Tracking status",
                    subtitle = if (uiState.autoTrackingEnabled) {
                        "Automatic expense tracking is enabled"
                    } else {
                        "Automatic expense tracking is off"
                    },
                    icon = Icons.Default.PhoneAndroid
                )
                InsightTile(
                    title = "Last import activity",
                    subtitle = if (uiState.lastSmsTimestamp > 0L) {
                        DateUtils.getRelativeTimeSpanString(uiState.lastSmsTimestamp).toString()
                    } else {
                        "No import activity yet"
                    },
                    icon = Icons.Default.Badge
                )
            }

            InsightSummaryCard(
                title = "Privacy & data",
                subtitle = "How CoB-FA handles your information"
            ) {
                InsightTile(
                    title = "On-device insights",
                    subtitle = "Behavioral insights run locally on your device",
                    icon = Icons.Default.Psychology
                )
                InsightTile(
                    title = "Cloud backup",
                    subtitle = "Firebase sync is used for recovery and continuity",
                    icon = Icons.Default.Shield
                )
                InsightTile(
                    title = "SMS handling",
                    subtitle = "Raw SMS bodies are not uploaded by the app",
                    icon = Icons.Default.Lock
                )
            }

            InsightSummaryCard(
                title = "Behavioral coaching",
                subtitle = "What makes CoB-FA different"
            ) {
                InsightTile(
                    title = "Smart nudges",
                    subtitle = "The app highlights risky patterns and helps reduce impulse spending",
                    icon = Icons.Default.Info
                )
                InsightTile(
                    title = "Progress mindset",
                    subtitle = "Your budgeting journey is reinforced through goals and rewards",
                    icon = Icons.Default.Star
                )
            }

            InsightSummaryCard(
                title = "App identity",
                subtitle = "Built for financial discipline"
            ) {
                InsightTile(
                    title = "About CoB-FA",
                    subtitle = "Cognitive-Behavioral Financial Advisor focused on smarter spending habits",
                    icon = Icons.Default.Settings
                )
            }
        }
    }
}

@Composable
private fun InsightSummaryCard(
    title: String,
    subtitle: String,
    content: @Composable () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            content()
        }
    }
}

@Composable
private fun InsightTile(
    title: String,
    subtitle: String,
    icon: ImageVector
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 13.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(36.dp)
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
