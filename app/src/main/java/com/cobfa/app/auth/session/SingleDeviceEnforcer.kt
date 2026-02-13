package com.cobfa.app.auth.session

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

class SingleDeviceEnforcer(
    uid: String,
    private val localDeviceId: String,
    private val onKicked: () -> Unit
) {
    private val ref = FirebaseDatabase.getInstance().reference
        .child("userSessions")
        .child(uid)
        .child("activeDeviceId")

    private var listener: ValueEventListener? = null

    fun start() {
        if (listener != null) return
        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val active = snapshot.getValue(String::class.java) ?: return
                if (active != localDeviceId) {
                    Log.w("SingleDevice", "Kicked: activeDeviceId=$active local=$localDeviceId")
                    FirebaseAuth.getInstance().signOut()
                    onKicked()
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("SingleDevice", "Listener cancelled", error.toException())
            }
        }
        ref.addValueEventListener(listener!!)
    }

    fun stop() {
        listener?.let { ref.removeEventListener(it) }
        listener = null
    }
}
