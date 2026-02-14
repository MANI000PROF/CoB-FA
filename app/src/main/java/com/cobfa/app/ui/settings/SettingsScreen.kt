package com.cobfa.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.cobfa.app.utils.GamificationScheduler
import com.cobfa.app.utils.PreferenceManager

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var autoTrackingEnabled by remember { mutableStateOf(false) }
    var smsGranted by remember { mutableStateOf(false) }

    fun refreshState() {
        autoTrackingEnabled = PreferenceManager.isAutoTrackingEnabled(context)
        smsGranted = ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    // Refresh at first composition.
    LaunchedEffect(Unit) { refreshState() }

    // Refresh whenever user returns to this screen (e.g., from permission screen or system settings).
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Settings") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text("Privacy", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "• Insights run on-device.\n• Firebase sync is backup only.\n• Raw SMS bodies are never uploaded.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            ListItem(
                headlineContent = { Text("Automatic expense tracking") },
                supportingContent = {
                    Text(
                        if (smsGranted) "Scans SMS only when you open/refresh (no background interception)."
                        else "SMS permission not granted. Enable to import on open/refresh."
                    )
                },
                leadingContent = { Icon(Icons.Default.Sms, contentDescription = null) },
                trailingContent = {
                    Switch(
                        checked = autoTrackingEnabled,
                        onCheckedChange = { checked ->
                            if (checked && !smsGranted) {
                                PreferenceManager.setPendingAutoTracking(context, true)
                                navController.navigate("sms_permission")
                            } else {
                                PreferenceManager.setPendingAutoTracking(context, false)
                                PreferenceManager.setAutoTrackingEnabled(context, checked)
                                autoTrackingEnabled = checked
                            }
                        }
                    )
                }
            )

            Divider()

            ListItem(
                headlineContent = { Text("SMS import") },
                supportingContent = { Text("Optional. Used to detect transactions; non-financial messages are ignored.") },
                leadingContent = { Icon(Icons.Default.PrivacyTip, contentDescription = null) },
                trailingContent = {
                    TextButton(onClick = { navController.navigate("sms_permission") }) {
                        Text("Manage")
                    }
                }
            )

            Divider()

            ListItem(
                headlineContent = { Text("Sync") },
                supportingContent = { Text("Your data can be backed up to Firebase for recovery.") },
                leadingContent = { Icon(Icons.Default.Sync, contentDescription = null) }
            )

            Divider()

            ListItem(
                headlineContent = { Text("Recompute achievements now") },
                supportingContent = { Text("Runs scoring worker immediately (for testing).") },
                leadingContent = { Icon(Icons.Default.Sync, contentDescription = null) },
                trailingContent = {
                    TextButton(onClick = { GamificationScheduler.runNow(context) }) {
                        Text("Run")
                    }
                }
            )

        }
    }
}
