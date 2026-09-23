package com.example.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.example.notifications.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class SmsReceiveReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // If the app is already the default SMS app, SmsDeliverReceiver handled it
        if (TelephonyRoleHelper.isDefaultSmsApp(context)) {
            return
        }

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
