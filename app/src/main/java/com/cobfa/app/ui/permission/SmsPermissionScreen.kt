package com.cobfa.app.ui.permission

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.cobfa.app.utils.PreferenceManager

@Composable
fun SmsPermissionScreen(
    onPermissionGranted: () -> Unit,
    onSkipClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity

    var lastRequestDenied by remember { mutableStateOf(false) }
    var permanentlyDenied by remember { mutableStateOf(false) }

    fun isGranted(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) ==
                PackageManager.PERMISSION_GRANTED

    fun updateDenialFlagsAfterResult(granted: Boolean) {
        if (granted) {
            lastRequestDenied = false
            permanentlyDenied = false
            return
        }
        lastRequestDenied = true
        val showRationale = ActivityCompat.shouldShowRequestPermissionRationale(
            activity,
            Manifest.permission.READ_SMS
        )
        permanentlyDenied = !showRationale
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            PreferenceManager.setSmsPermissionGranted(context, granted)
            updateDenialFlagsAfterResult(granted)

            if (granted) {
                val last = PreferenceManager.getLastSmsTimestamp(context)
                if (last == 0L) {
                    val now = System.currentTimeMillis()
                    val bootstrap = now - 90L * 24 * 60 * 60 * 1000
                    PreferenceManager.setLastSmsTimestamp(context, bootstrap)
                }
                onPermissionGranted()
            }
        }

    // If user comes back from Settings, auto-continue.
    LaunchedEffect(Unit) {
        if (isGranted()) onPermissionGranted()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        Text(
            text = "Automatic Expense Tracking",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "CoB-FA can detect transaction SMS sent by your bank or UPI apps.\n\n" +
                    "• We access SMS to identify transaction messages\n" +
                    "• OTPs and personal SMS are ignored during processing\n" +
                    "• Processing happens on your device",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        if (lastRequestDenied) {
            Spacer(modifier = Modifier.height(16.dp))
            val msg = if (permanentlyDenied) {
                "SMS permission is blocked. Please enable it from App Settings to use automatic tracking."
            } else {
                "SMS permission is needed to automatically detect transaction messages. You can still use the app with manual entry."
            }
            Text(text = msg, style = MaterialTheme.typography.bodySmall, textAlign = TextAlign.Center)
        }

        Spacer(modifier = Modifier.height(32.dp))

        val primaryText = when {
            isGranted() -> "Continue"
            permanentlyDenied -> "Open App Settings"
            else -> "Allow SMS Access"
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                when {
                    isGranted() -> onPermissionGranted()
                    permanentlyDenied -> {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null)
                        )
                        context.startActivity(intent)
                    }
                    else -> permissionLauncher.launch(Manifest.permission.READ_SMS)
                }
            }
        ) {
            Text(primaryText)
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onSkipClick) {
            Text("Skip for now")
        }
    }
}
