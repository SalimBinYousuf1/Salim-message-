package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.RemoteInput
import com.example.data.local.AppDatabase
import com.example.telephony.ContactRepository
import com.example.telephony.SmsRepository
import com.example.telephony.SmsSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class NotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val address = intent.getStringExtra(NotificationHelper.EXTRA_ADDRESS) ?: return
        val notificationId = intent.getIntExtra(NotificationHelper.EXTRA_NOTIFICATION_ID, address.hashCode())

        val appDatabase = AppDatabase.getInstance(context)
        val contactRepo = ContactRepository(context)
        val smsRepo = SmsRepository(context, appDatabase.conversationMetaDao(), contactRepo)
        val smsSender = SmsSender(context)

        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                when (action) {
                    NotificationHelper.ACTION_DIRECT_REPLY -> {
                        val remoteInputResults = RemoteInput.getResultsFromIntent(intent)
                        val replyText = remoteInputResults?.getCharSequence(NotificationHelper.KEY_TEXT_REPLY)?.toString()

                        if (!replyText.isNullOrBlank()) {
                            // Send real SMS
                            smsSender.sendSms(address, replyText)
                            // Dismiss or update notification
                            NotificationManagerCompat.from(context).cancel(notificationId)
                        }
                    }

                    NotificationHelper.ACTION_MARK_READ -> {
                        smsRepo.markConversationAsRead(0L, address)
                        NotificationManagerCompat.from(context).cancel(notificationId)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
