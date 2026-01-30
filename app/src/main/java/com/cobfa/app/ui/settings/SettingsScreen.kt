package com.cobfa.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.cobfa.app.utils.PreferenceManager
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController
) {
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var autoTrackingEnabled by remember {
        mutableStateOf(PreferenceManager.isAutoTrackingEnabled(context))
    }

    LaunchedEffect(Unit) {
        autoTrackingEnabled = PreferenceManager.isAutoTrackingEnabled(context)
    }

    val smsGranted = ContextCompat.checkSelfPermission(
        context, Manifest.permission.READ_SMS
    ) == PackageManager.PERMISSION_GRANTED

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(PaddingValues(16.dp))
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
        }
    }
}
