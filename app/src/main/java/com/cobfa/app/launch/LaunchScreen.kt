package com.cobfa.app.launch

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.LottieConstants
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.cobfa.app.R
import com.cobfa.app.auth.session.DeviceId
import com.cobfa.app.auth.session.SessionWriter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.delay
import kotlinx.coroutines.tasks.await

@Composable
fun LaunchScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current

    var contentVisible by remember { mutableStateOf(false) }
    var showProgress by remember { mutableStateOf(false) }
    var statusText by remember { mutableStateOf("Preparing your secure workspace") }

    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.launch_fintech)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = true
    )

    LaunchedEffect(Unit) {
        contentVisible = true

        delay(700)
        showProgress = true

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            onNavigate("auth")
            return@LaunchedEffect
        }

        val uid = user.uid
        val deviceId = DeviceId.get(context)

        statusText = "Securing your session"

        SessionWriter.write(uid, deviceId) { ok ->
            Log.d("LaunchScreen", "Session refresh at launch = $ok")
        }

        statusText = "Checking trusted device"

        val activeDeviceId = runCatching {
            FirebaseDatabase.getInstance().reference
                .child("userSessions")
                .child(uid)
                .child("activeDeviceId")
                .get()
                .await()
                .getValue(String::class.java)
        }.getOrNull()

        if (!activeDeviceId.isNullOrBlank() && activeDeviceId != deviceId) {
            auth.signOut()
            onNavigate("auth")
            return@LaunchedEffect
        }

        statusText = "Loading your financial profile"

        val completed = runCatching {
            FirebaseDatabase.getInstance().reference
                .child("users")
                .child(uid)
                .child("profileCompleted")
                .get()
                .await()
                .getValue(Boolean::class.java) ?: false
        }.getOrDefault(false)

        delay(300)

        if (completed) onNavigate("main") else onNavigate("profile")
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = contentVisible,
                enter = fadeIn() + slideInVertically(initialOffsetY = { it / 6 }),
                exit = fadeOut()
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    LottieAnimation(
                        composition = composition,
                        progress = { progress },
                        modifier = Modifier.size(220.dp)
                    )

                    Spacer(modifier = Modifier.size(8.dp))

                    Text(
                        text = "CoB-FA",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Spacer(modifier = Modifier.size(8.dp))

                    Text(
                        text = "Cognitive-Behavioral Financial Advisor",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.size(24.dp))

                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    AnimatedVisibility(
                        visible = showProgress,
                        enter = fadeIn(),
                        exit = fadeOut()
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Spacer(modifier = Modifier.size(14.dp))
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.2.dp
                            )
                        }
                    }
                }
            }
        }
    }
}
