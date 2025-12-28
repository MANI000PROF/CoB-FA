package com.cobfa.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.cobfa.app.utils.PreferenceManager

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // Respect user choice
        if (!PreferenceManager.isAutoTrackingEnabled(context)) {
            Log.d("SMS_RECEIVER", "Auto-tracking OFF → ignoring SMS")
            return
        }

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        for (msg in messages) {
            val sender = msg.displayOriginatingAddress
            val body = msg.messageBody
            val timestamp = msg.timestampMillis

            Log.d("SMS_RECEIVER", "Received SMS from=$sender")

            SmsProcessor.process(
                context = context,
                sender = sender,
                body = body,
                timestamp = timestamp
            )
        }
    }
}
