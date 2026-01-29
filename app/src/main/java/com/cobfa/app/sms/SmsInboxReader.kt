package com.cobfa.app.sms

import android.content.Context
import android.provider.Telephony

data class RawSms(
    val address: String,
    val body: String,
    val timestamp: Long
)

object SmsInboxReader {

    fun readRecentSmsSince(
        context: Context,
        sinceMs: Long,
        limit: Int = 200
    ): List<RawSms> {
        val messages = mutableListOf<RawSms>()

        val selection = "${Telephony.Sms.DATE} > ?"
        val selectionArgs = arrayOf(sinceMs.toString())

        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE),
            selection,
            selectionArgs,
            "${Telephony.Sms.DATE} DESC"
        )

        cursor?.use {
            val addressIdx = it.getColumnIndex(Telephony.Sms.ADDRESS)
            val bodyIdx = it.getColumnIndex(Telephony.Sms.BODY)
            val dateIdx = it.getColumnIndex(Telephony.Sms.DATE)

            while (it.moveToNext() && messages.size < limit) {
                val address = it.getString(addressIdx) ?: continue
                val body = it.getString(bodyIdx) ?: continue
                val date = it.getLong(dateIdx)
                messages.add(RawSms(address, body, date))
            }
        }

        return messages
    }
}
