package com.cobfa.app.sms

import android.content.Context
import android.net.Uri
import android.util.Log

data class RawSms(
    val address: String,
    val body: String,
    val timestamp: Long
)

object SmsInboxReader {

    private const val TAG = "SMS_INBOX"

    fun readRecentSms(
        context: Context,
        limit: Int = 20
    ): List<RawSms> {

        val messages = mutableListOf<RawSms>()
        val uri = Uri.parse("content://sms/inbox")

        val cursor = context.contentResolver.query(
            uri,
            arrayOf("address", "body", "date"),
            null,
            null,
            "date DESC LIMIT $limit"
        )

        cursor?.use {
            val addressIdx = it.getColumnIndex("address")
            val bodyIdx = it.getColumnIndex("body")
            val dateIdx = it.getColumnIndex("date")

            while (it.moveToNext()) {
                val address = it.getString(addressIdx) ?: continue
                val body = it.getString(bodyIdx) ?: continue
                val date = it.getLong(dateIdx)

                Log.d(TAG, "SMS from=$address body=$body")

                messages.add(
                    RawSms(
                        address = address,
                        body = body,
                        timestamp = date
                    )
                )
            }
        }

        return messages
    }
}
