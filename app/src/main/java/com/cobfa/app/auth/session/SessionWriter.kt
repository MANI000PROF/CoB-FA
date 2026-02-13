package com.cobfa.app.auth.session

import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ServerValue
import java.util.UUID

object SessionWriter {
    fun write(uid: String, deviceId: String, onDone: (Boolean) -> Unit) {
        val data = mapOf(
            "activeDeviceId" to deviceId,
            "activeSessionId" to UUID.randomUUID().toString(),
            "lastLoginAt" to ServerValue.TIMESTAMP
        )

        FirebaseDatabase.getInstance().reference
            .child("userSessions")
            .child(uid)
            .setValue(data)
            .addOnSuccessListener {
                android.util.Log.d("SessionWriter", "write success uid=$uid")
                onDone(true)
            }
            .addOnFailureListener { e ->
                android.util.Log.e("SessionWriter", "write FAILED uid=$uid", e)
                onDone(false)
            }

    }
}
