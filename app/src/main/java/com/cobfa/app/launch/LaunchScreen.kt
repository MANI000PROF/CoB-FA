package com.cobfa.app.launch

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import com.cobfa.app.auth.session.DeviceId
import com.cobfa.app.auth.session.SessionWriter
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import kotlinx.coroutines.tasks.await

@Composable
fun LaunchScreen(
    onNavigate: (String) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser

        if (user == null) {
            onNavigate("auth")
            return@LaunchedEffect
        }

        val uid = user.uid
        val deviceId = DeviceId.get(context)

        // Always refresh session at launch (last-login-wins)
        SessionWriter.write(uid, deviceId) { ok ->
            android.util.Log.d("LaunchScreen", "Session refresh at launch = $ok")
        }

        // 1) Single-device gate: if session exists and activeDeviceId != this device -> kick
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

        // 2) Existing profileCompleted routing
        val completed = runCatching {
            FirebaseDatabase.getInstance().reference
                .child("users")
                .child(uid)
                .child("profileCompleted")
                .get()
                .await()
                .getValue(Boolean::class.java) ?: false
        }.getOrDefault(false)

        if (completed) onNavigate("dashboard") else onNavigate("profile")
    }
}
