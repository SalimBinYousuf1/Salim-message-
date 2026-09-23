package com.example.telephony

import android.content.BroadcastReceiver
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.data.local.AppDatabase
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch

class SmsDeliverReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_DELIVER_ACTION) return

        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
        if (messages.isNullOrEmpty()) return

        val senderAddress = messages[0].displayOriginatingAddress ?: return
        val fullBody = StringBuilder()
        var timestamp = System.currentTimeMillis()

        for (msg in messages) {
            fullBody.append(msg.displayMessageBody)
            timestamp = msg.timestampMillis
        }

        val messageText = fullBody.toString()

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)

                // Check if sender is blocked
                val isBlocked = db.blockedNumberDao().isBlocked(senderAddress).firstOrNull() ?: false
                if (isBlocked) {
                    return@launch
                }

                // Write into SMS Inbox (default SMS app is authorized and expected to insert)
                try {
                    val values = ContentValues().apply {
                        put(Telephony.Sms.ADDRESS, senderAddress)
                        put(Telephony.Sms.BODY, messageText)
                        put(Telephony.Sms.DATE, timestamp)
                        put(Telephony.Sms.READ, 0)
                        put(Telephony.Sms.SEEN, 0)
                        put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_INBOX)
                    }
                    context.contentResolver.insert(Telephony.Sms.Inbox.CONTENT_URI, values)
                } catch (e: Exception) {
                    // Ignore write error
                }

                // Resolve contact name for notification
                val contactRepo = ContactRepository(context)
                val contactInfo = contactRepo.resolveContact(senderAddress)

                NotificationHelper.showIncomingMessageNotification(
                    context = context,
                    senderAddress = senderAddress,
                    senderName = contactInfo.name,
                    messageBody = messageText,
                    timestamp = timestamp
                )
            } finally {
                pendingResult.finish()
            }
        }
    }
}
