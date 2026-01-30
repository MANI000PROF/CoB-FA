package com.cobfa.app.auth.otp

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cobfa.app.auth.phone.PhoneAuthViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OtpScreen(
    navController: NavController,
    vm: PhoneAuthViewModel
) {
    val context = LocalContext.current
    val activity = context as Activity

    val snackbarHostState = remember { SnackbarHostState() }
    var otp by remember { mutableStateOf("") }

    LaunchedEffect(vm.errorMessage) {
        val msg = vm.errorMessage
        if (!msg.isNullOrBlank()) snackbarHostState.showSnackbar(msg)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Verify OTP") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Text("Back")
                    }
                }
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
                text = "Enter the 6-digit code",
                style = MaterialTheme.typography.headlineSmall
            )

            Spacer(Modifier.height(16.dp))

            OutlinedTextField(
                value = otp,
                onValueChange = { raw ->
                    vm.clearError()
                    otp = raw.filter { it.isDigit() }.take(6)
                },
                label = { Text("OTP") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                supportingText = {
                    Text(if (otp.length < 6) "OTP must be 6 digits" else " ")
                },
                isError = (otp.isNotEmpty() && otp.length < 6),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(16.dp))

            Button(
                modifier = Modifier.fillMaxWidth(),
                enabled = !vm.isLoading && otp.length == 6,
                onClick = {
                    vm.verifyOtp(
                        otp = otp,
                        onVerified = {
                            navController.navigate("launch") {
                                popUpTo("phone") { inclusive = true }
                            }
                        }
                    )
                }
            ) {
                if (vm.isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(Modifier.width(10.dp))
                    Text("Verifying…")
                } else {
                    Text("Continue")
                }
            }

            Spacer(Modifier.height(12.dp))

            if (vm.resendCooldown > 0) {
                Text(
                    text = "Resend OTP in ${vm.resendCooldown}s",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                TextButton(
                    enabled = !vm.isLoading,
                    onClick = { vm.resendOtp(activity = activity, onCodeSent = {}) }
                ) {
                    Text("Resend OTP")
                }
            }
        }
    }
}
