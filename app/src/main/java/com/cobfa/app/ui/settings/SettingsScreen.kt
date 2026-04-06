package com.cobfa.app.ui.settings

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.cobfa.app.ui.dashboard.DashboardViewModel
import com.cobfa.app.utils.GamificationScheduler
import com.cobfa.app.utils.PreferenceManager

@RequiresApi(Build.VERSION_CODES.O)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    navController: NavController,
    dashboardViewModel: DashboardViewModel
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var autoTrackingEnabled by remember { mutableStateOf(false) }
    var smsGranted by remember { mutableStateOf(false) }
    var triggerDebug by remember { mutableStateOf(false) }

    fun refreshState() {
        autoTrackingEnabled = PreferenceManager.isAutoTrackingEnabled(context)
        smsGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    LaunchedEffect(Unit, triggerDebug) {
        refreshState()
        if (triggerDebug) {
            snackbarHostState.showSnackbar("Synthetic history generated")
            triggerDebug = false
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) refreshState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                modifier = Modifier.statusBarsPadding(),
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Settings",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontWeight = FontWeight.ExtraBold,
                                letterSpacing = 0.4.sp
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Privacy, sync and smart controls",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    Surface(
                        onClick = { navController.popBackStack() },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        tonalElevation = 2.dp,
                        shadowElevation = 4.dp,
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                SettingsHeroCard(
                    smsGranted = smsGranted,
                    autoTrackingEnabled = autoTrackingEnabled
                )
            }

            item {
                SettingsSectionCard(
                    title = "Automation",
                    icon = Icons.Default.AutoAwesome
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        headlineContent = {
                            Text(
                                "Automatic expense tracking",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text(
                                if (smsGranted) {
                                    "Scans SMS when you open or refresh the app. No background interception."
                                } else {
                                    "SMS permission not granted. Enable it to import transactions on open or refresh."
                                }
                            )
                        },
                        leadingContent = {
                            SettingIconBubble(
                                icon = Icons.Default.Sms,
                                selected = autoTrackingEnabled
                            )
                        },
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
                }
            }

            item {
                SettingsSectionCard(
                    title = "Privacy & permissions",
                    icon = Icons.Default.Security
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        headlineContent = {
                            Text(
                                "SMS import",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text("Optional. Used to detect financial transactions while ignoring irrelevant messages.")
                        },
                        leadingContent = {
                            SettingIconBubble(
                                icon = Icons.Default.PrivacyTip,
                                selected = smsGranted
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { navController.navigate("sms_permission") }
                            ) {
                                Text("Manage")
                            }
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        headlineContent = {
                            Text(
                                "Privacy note",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text("Insights run on-device, Firebase is used only for backup, and raw SMS bodies are never uploaded.")
                        },
                        leadingContent = {
                            SettingIconBubble(
                                icon = Icons.Default.Security,
                                selected = true
                            )
                        }
                    )
                }
            }

            item {
                SettingsSectionCard(
                    title = "Backup & progress",
                    icon = Icons.Default.Sync
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        headlineContent = {
                            Text(
                                "Sync",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text("Your data can be backed up to Firebase for safer recovery across reinstalls or device changes.")
                        },
                        leadingContent = {
                            SettingIconBubble(
                                icon = Icons.Default.Sync,
                                selected = true
                            )
                        }
                    )

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        headlineContent = {
                            Text(
                                "Recompute achievements now",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text("Runs the scoring worker immediately. Useful during testing and tuning.")
                        },
                        leadingContent = {
                            SettingIconBubble(
                                icon = Icons.Default.AutoAwesome,
                                selected = false
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = { GamificationScheduler.runNow(context) }
                            ) {
                                Text("Run")
                            }
                        }
                    )
                }
            }

            item {
                SettingsSectionCard(
                    title = "Developer tools",
                    icon = Icons.Default.AutoAwesome
                ) {
                    ListItem(
                        colors = ListItemDefaults.colors(
                            containerColor = Color.Transparent
                        ),
                        headlineContent = {
                            Text(
                                "Generate synthetic history",
                                fontWeight = FontWeight.SemiBold
                            )
                        },
                        supportingContent = {
                            Text(
                                "Creates fake expenses for the past few weeks. Useful for testing insights, budgets and analytics."
                            )
                        },
                        leadingContent = {
                            SettingIconBubble(
                                icon = Icons.Default.AutoAwesome,
                                selected = false
                            )
                        },
                        trailingContent = {
                            TextButton(
                                onClick = {
                                    dashboardViewModel.debugGenerateHistory(24)
                                    triggerDebug = true
                                }
                            ) {
                                Text("Generate")
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsHeroCard(
    smsGranted: Boolean,
    autoTrackingEnabled: Boolean
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            MaterialTheme.colorScheme.tertiary.copy(alpha = 0.08f),
                            MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Control your COBFA experience",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "Manage SMS access, sync safety and automation behavior with a cleaner, smarter setup.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    StatusChip(
                        text = if (smsGranted) "SMS enabled" else "SMS locked",
                        highlighted = smsGranted
                    )
                    StatusChip(
                        text = if (autoTrackingEnabled) "Auto tracking on" else "Auto tracking off",
                        highlighted = autoTrackingEnabled
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SettingIconBubble(
                    icon = icon,
                    selected = true
                )

                Column {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Fine-tune this area",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )

            content()
        }
    }
}

@Composable
private fun SettingIconBubble(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                shape = CircleShape
            ),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}

@Composable
private fun StatusChip(
    text: String,
    highlighted: Boolean
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant
        }
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelLarge,
            color = if (highlighted) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            }
        )
    }
}
