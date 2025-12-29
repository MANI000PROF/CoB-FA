package com.cobfa.app.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import android.util.Log
import com.cobfa.app.data.local.db.ExpenseDatabase
import com.cobfa.app.data.repository.ExpenseRepository
import com.cobfa.app.utils.PreferenceManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        // Step 1: Verify this is an SMS received intent
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) {
            return
        }

        // Step 2: Respect user auto-tracking preference
        if (!PreferenceManager.isAutoTrackingEnabled(context)) {
            Log.d("SMS_RECEIVER", "Auto-tracking OFF → ignoring incoming SMS")
            return
        }

        // Step 3: Extract SMS messages from the intent
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)

        if (messages.isEmpty()) {
            Log.d("SMS_RECEIVER", "No SMS messages in intent")
            return
        }

        Log.d("SMS_RECEIVER", "Received ${messages.size} SMS message(s)")

        // Step 4: Process each SMS asynchronously
        // Use CoroutineScope(Dispatchers.IO) because BroadcastReceiver.onReceive()
        // must return quickly and not block the UI thread
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Create single repository instance for this batch of SMS
                val db = ExpenseDatabase.getInstance(context)
                val repo = ExpenseRepository(db.expenseDao())

                var processedCount = 0
                var skippedCount = 0

                for (msg in messages) {
                    val sender = msg.displayOriginatingAddress
                    val body = msg.messageBody
                    val timestamp = msg.timestampMillis

                    Log.d("SMS_RECEIVER", "Processing SMS from=$sender")

                    // Pre-filter obvious non-transactions early
                    if (SmsFilters.isBlocked(body)) {
                        Log.d("SMS_RECEIVER", "SMS filtered by SmsFilters")
                        skippedCount++
                        continue
                    }

                    // Process with dedup check
                    val inserted = SmsProcessor.processWithDedup(
                        sender = sender,
                        body = body,
                        timestamp = timestamp,
                        repo = repo
                    )

                    if (inserted) {
                        processedCount++
                        Log.d("SMS_RECEIVER", "✓ SMS processed and saved")
                    } else {
                        skippedCount++
                        Log.d("SMS_RECEIVER", "⊗ SMS skipped (duplicate or invalid)")
                    }
                }

                Log.d("SMS_RECEIVER", "Batch complete: processed=$processedCount, skipped=$skippedCount")

            } catch (e: Exception) {
                Log.e("SMS_RECEIVER", "Error processing SMS: ${e.message}", e)
            }
        }
    }
}
