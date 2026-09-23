package com.example.telephony

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ScheduledMessageReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val messageId = intent.getLongExtra("extra_scheduled_id", -1L)
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val db = AppDatabase.getInstance(context)
                val scheduledDao = db.scheduledMessageDao()
                val smsSender = SmsSender(context)

                val message = if (messageId > 0) {
                    scheduledDao.getById(messageId)
                } else {
                    val due = scheduledDao.getDueMessages(System.currentTimeMillis())
                    due.firstOrNull()
                }

                if (message != null && message.status == "PENDING") {
                    val result = smsSender.sendSms(
                        destinationAddress = message.recipient,
                        messageText = message.message,
                        subId = message.subId
                    )

                    if (result.isSuccess) {
                        scheduledDao.updateStatus(message.id, "SENT", null)
                    } else {
                        scheduledDao.updateStatus(message.id, "FAILED", result.exceptionOrNull()?.message)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}

object ScheduledMessageScheduler {

    fun schedule(context: Context, scheduledId: Long, triggerAtMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduledMessageReceiver::class.java).apply {
            putExtra("extra_scheduled_id", scheduledId)
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduledId.toInt(),
            intent,
            flags
        )

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            } else {
                alarmManager.setExact(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtMillis,
                    pendingIntent
                )
            }
        } catch (e: SecurityException) {
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtMillis,
                pendingIntent
            )
        }
    }

    fun cancel(context: Context, scheduledId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val intent = Intent(context, ScheduledMessageReceiver::class.java)
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            scheduledId.toInt(),
            intent,
            flags
        )
        alarmManager.cancel(pendingIntent)
    }
}
