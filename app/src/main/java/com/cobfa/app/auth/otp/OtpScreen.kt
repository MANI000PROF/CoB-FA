package com.cobfa.app.auth.otp

import android.app.Activity
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.cobfa.app.auth.phone.PhoneAuthViewModel

@Composable
fun OtpScreen(
    navController: NavController,
    vm: PhoneAuthViewModel
) {

    val context = LocalContext.current
    val activity = context as Activity

    var otp by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text("Enter OTP")

        OutlinedTextField(
            value = otp,
            onValueChange = { otp = it },
            label = { Text("OTP") }
        )

        Spacer(Modifier.height(16.dp))

        Button(
            modifier = Modifier.fillMaxWidth(),
            enabled = !vm.isLoading,
            onClick = {
                Log.d("OtpScreen", "Verify OTP clicked, otp=$otp")
                vm.verifyOtp(
                    otp = otp,
                    onVerified = {
                        Log.d("OtpScreen", "OTP verified, navigating to profile")
                        navController.navigate("profile") {
                            popUpTo("phone") {
                                inclusive = true
                            }
                        }
                    }
                )
            }
        ) {
            Text("Verify OTP")
        }

        Spacer(Modifier.height(8.dp))

        if (vm.resendCooldown > 0) {
            Text(
                text = "Resend OTP in ${vm.resendCooldown}s",
                style = MaterialTheme.typography.bodyMedium
            )
        } else {
            TextButton(
                onClick = {
                    vm.resendOtp(
                        activity = activity,
                        onCodeSent = {}
                    )
                }
            ) {
                Text("Resend OTP")
            }
        }

        vm.errorMessage?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}
