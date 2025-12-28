package com.cobfa.app.ui.permission

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import android.Manifest
import com.cobfa.app.utils.PreferenceManager

@Composable
fun SmsPermissionScreen(
    onPermissionGranted: () -> Unit,
    onSkipClick: () -> Unit
) {
    val context = LocalContext.current
    val activity = context as Activity

    val permissionLauncher =
        rememberLauncherForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            PreferenceManager.setSmsPermissionGranted(context, granted)
            if (granted) {
                onPermissionGranted()
            }
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
            text = "CoB-FA can automatically detect transaction messages sent by your bank or UPI apps.\n\n" +
                    "• Only transaction messages are scanned\n" +
                    "• OTPs and personal SMS are ignored\n" +
                    "• All processing stays on your device",
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            onClick = {
                permissionLauncher.launch(Manifest.permission.READ_SMS)
            }
        ) {
            Text("Allow SMS Access")
        }

        Spacer(modifier = Modifier.height(12.dp))

        TextButton(onClick = onSkipClick) {
            Text("Skip for now")
        }
    }
}
