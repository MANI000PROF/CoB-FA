package com.cobfa.app.auth.phone

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cobfa.app.utils.PhoneNumberFormatter
import com.cobfa.app.utils.PhoneNumberResolver
import com.cobfa.app.utils.SimInfoUtil
import kotlinx.coroutines.launch

private const val ENABLE_SIM_AUTO_DETECT = false

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneAuthScreen(
    navController: NavController,
    vm: PhoneAuthViewModel
) {
    val context = LocalContext.current
    val activity = context as Activity
    val scope = rememberCoroutineScope()

    val snackbarHostState = remember { SnackbarHostState() }

    var manualPhone by remember { mutableStateOf("") }
    var simPresent by remember { mutableStateOf(true) }

    // Show errors as snackbars (and keep inline support where useful)
    LaunchedEffect(vm.errorMessage) {
        val msg = vm.errorMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message = msg)
        }
    }

    LaunchedEffect(Unit) {
        if (!ENABLE_SIM_AUTO_DETECT) return@LaunchedEffect

        simPresent = SimInfoUtil.isSimPresent(context)
        if (!simPresent) return@LaunchedEffect

        val raw = PhoneNumberResolver.getPhoneNumber(context)
        val country = SimInfoUtil.getSimCountryIso(context)
        val formatted = raw?.let { PhoneNumberFormatter.toE164(it, country) }
        formatted?.let { vm.setDetectedPhone(it) }
    }

    val showManualInput = true

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Verify your mobile number",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "We’ll use this only for login. SMS import is optional later.",
                style = MaterialTheme.typography.bodyMedium
            )

            Spacer(Modifier.height(20.dp))

            if (vm.detectedPhoneNumber != null) {
                Text(
                    text = "Detected: ${vm.detectedPhoneNumber}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(16.dp))
            }

            if (showManualInput) {
                if (!simPresent) {
                    Text(
                        text = "No SIM detected. Please enter your mobile number manually.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                }

                OutlinedTextField(
                    value = manualPhone,
                    onValueChange = { raw ->
                        // digits only, max 10
                        manualPhone = raw.filter { it.isDigit() }.take(10)
                    },
                    label = { Text("Mobile number") },
                    prefix = { Text("+91 ") },
                    placeholder = { Text("10-digit number") },
                    supportingText = { Text("Example: 9876543210") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))
            }

            val canProceed = (!vm.isLoading) && (
                    vm.detectedPhoneNumber != null || manualPhone.length == 10
                    )

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = canProceed,
                onClick = {
                    val fallback = manualPhone.takeIf { it.length == 10 }?.let { "+91$it" }

                    vm.startVerificationWithFallback(
                        activity = activity,
                        fallbackPhone = fallback,
                        onCodeSent = { navController.navigate("otp") },
                        onVerified = { navController.navigate("profile") }
                    )
                }
            ) {
                if (vm.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Sending OTP…")
                } else {
                    Text("Send OTP")
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "By continuing, you agree to link your Google account in the next step (required).",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
