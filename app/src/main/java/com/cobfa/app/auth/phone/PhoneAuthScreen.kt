package com.cobfa.app.auth.phone

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.cobfa.app.utils.PhoneNumberFormatter
import com.cobfa.app.utils.PhoneNumberResolver
import com.cobfa.app.utils.SimInfoUtil

@Composable
fun PhoneAuthScreen(
    navController: NavController,
    vm: PhoneAuthViewModel
) {

    val context = LocalContext.current
    val activity = context as Activity

    LaunchedEffect(Unit) {
        if (!SimInfoUtil.isSimPresent(context)) {
            Log.d("PHONE_FLOW", "No SIM present")
            return@LaunchedEffect
        }

        val raw = PhoneNumberResolver.getPhoneNumber(context)
        val country = SimInfoUtil.getSimCountryIso(context)

        val formatted = raw?.let {
            PhoneNumberFormatter.toE164(it, country)
        }

        Log.d("PHONE_FLOW", "Detected phone=$formatted")

        formatted?.let {
            vm.setDetectedPhone(it)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text("Verifying your mobile number")

        Spacer(Modifier.height(16.dp))

        Text(
            text = vm.detectedPhoneNumber ?: "Using SIM verification",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

        var manualPhone by remember { mutableStateOf("") }
        val showManualInput = vm.detectedPhoneNumber == null

        if (showManualInput) {
            OutlinedTextField(
                value = manualPhone,
                onValueChange = { manualPhone = it },
                label = { Text("Mobile Number") },
                placeholder = { Text("10-digit number") },
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))
        }

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !vm.isLoading && (vm.detectedPhoneNumber != null || manualPhone.length >= 10),
            onClick = {
                val fallback = manualPhone.takeIf { it.length >= 10 }?.let {
                    "+91$it"
                }

                vm.startVerificationWithFallback(
                    activity = activity,
                    fallbackPhone = fallback,
                    onCodeSent = {
                        navController.navigate("otp")
                    },
                    onVerified = {
                        navController.navigate("profile")
                    }
                )
            }
        ) {
            Text("Verify Mobile Number")
        }

    }
}
