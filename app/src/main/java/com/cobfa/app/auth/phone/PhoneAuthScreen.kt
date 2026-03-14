package com.cobfa.app.auth.phone

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cobfa.app.R
import com.cobfa.app.auth.session.DeviceId
import com.cobfa.app.auth.session.SessionWriter
import com.cobfa.app.utils.PhoneNumberFormatter
import com.cobfa.app.utils.PhoneNumberResolver
import com.cobfa.app.utils.SimInfoUtil
import com.google.firebase.auth.FirebaseAuth

private const val ENABLE_SIM_AUTO_DETECT = false

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PhoneAuthScreen(
    navController: NavController,
    vm: PhoneAuthViewModel
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val snackbarHostState = remember { SnackbarHostState() }

    var manualPhone by remember { mutableStateOf("") }
    var simPresent by remember { mutableStateOf(true) }
    var contentVisible by remember { mutableStateOf(false) }

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.phone_verify)
    )
    val animationProgress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = true
    )

    LaunchedEffect(Unit) {
        contentVisible = true
    }

    LaunchedEffect(vm.errorMessage) {
        val msg = vm.errorMessage
        if (!msg.isNullOrBlank()) {
            snackbarHostState.showSnackbar(message = msg)
            vm.clearError()
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
    val canProceed = (!vm.isLoading) && (
            vm.detectedPhoneNumber != null || manualPhone.length == 10
            )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Secure sign in") }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                AnimatedVisibility(
                    visible = contentVisible,
                    enter = fadeIn() + slideInVertically(initialOffsetY = { it / 8 }),
                    exit = fadeOut()
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        LottieAnimation(
                            composition = composition,
                            progress = { animationProgress },
                            modifier = Modifier.size(210.dp)
                        )

                        Spacer(modifier = Modifier.size(8.dp))

                        Text(
                            text = "Welcome to CoB-FA",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold
                        )

                        Spacer(modifier = Modifier.size(6.dp))

                        Text(
                            text = "Verify your mobile number to continue securely",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        Spacer(modifier = Modifier.size(14.dp))

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            AuthInfoChip(
                                icon = Icons.Default.Lock,
                                text = "Secure sign-in"
                            )
                            AuthInfoChip(
                                icon = Icons.Default.PhoneAndroid,
                                text = "OTP verification"
                            )
                            AuthInfoChip(
                                icon = Icons.Default.VerifiedUser,
                                text = "SMS import optional later"
                            )
                        }

                        Spacer(modifier = Modifier.size(20.dp))

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.extraLarge,
                            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Mobile verification",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold
                                )

                                Text(
                                    text = "We’ll use your phone number only for authentication. You can manage tracking permissions later.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )

                                if (vm.detectedPhoneNumber != null) {
                                    AuthDetectedPhoneCard(
                                        phone = vm.detectedPhoneNumber.orEmpty()
                                    )
                                }

                                if (showManualInput) {
                                    if (!simPresent) {
                                        Text(
                                            text = "No SIM detected. Enter your mobile number manually.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    OutlinedTextField(
                                        value = manualPhone,
                                        onValueChange = { raw ->
                                            manualPhone = raw.filter { it.isDigit() }.take(10)
                                        },
                                        label = { Text("Mobile number") },
                                        prefix = { Text("+91 ") },
                                        placeholder = { Text("10-digit number") },
                                        supportingText = {
                                            Text(
                                                if (manualPhone.isBlank()) {
                                                    "Example: 9876543210"
                                                } else {
                                                    "${manualPhone.length}/10 digits"
                                                }
                                            )
                                        },
                                        singleLine = true,
                                        keyboardOptions = KeyboardOptions(
                                            keyboardType = KeyboardType.Number
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                Button(
                                    modifier = Modifier.fillMaxWidth(),
                                    enabled = canProceed,
                                    onClick = {
                                        if (activity == null) {
                                            vm.clearError()
                                            return@Button
                                        }

                                        val fallback = manualPhone
                                            .takeIf { it.length == 10 }
                                            ?.let { "+91$it" }

                                        vm.startVerificationWithFallback(
                                            activity = activity,
                                            fallbackPhone = fallback,
                                            onCodeSent = {
                                                navController.navigate("otp")
                                            },
                                            onVerified = {
                                                val user = FirebaseAuth.getInstance().currentUser
                                                if (user == null) {
                                                    navController.navigate("profile")
                                                    return@startVerificationWithFallback
                                                }

                                                val deviceId = DeviceId.get(context)
                                                SessionWriter.write(user.uid, deviceId) {
                                                    navController.navigate("profile")
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
                                        Spacer(modifier = Modifier.size(10.dp))
                                        Text("Sending OTP…")
                                    } else {
                                        Text("Continue with OTP")
                                    }
                                }

                                Text(
                                    text = "Google account linking will be required in the next step.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AuthInfoChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            androidx.compose.material3.Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun AuthDetectedPhoneCard(
    phone: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = "Detected number",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = phone,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

private fun Context.findActivity(): Activity? {
    var current = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}
