package com.example.notifications

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.Person
import androidx.core.app.RemoteInput
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object NotificationHelper {

    const val CHANNEL_MESSAGES = "salim_channel_messages"
    const val CHANNEL_SCHEDULED = "salim_channel_scheduled"

    const val ACTION_DIRECT_REPLY = "com.example.salim.ACTION_DIRECT_REPLY"
    const val ACTION_MARK_READ = "com.example.salim.ACTION_MARK_READ"

    const val KEY_TEXT_REPLY = "key_text_reply"
    const val EXTRA_THREAD_ID = "extra_thread_id"
    const val EXTRA_ADDRESS = "extra_address"
    const val EXTRA_NOTIFICATION_ID = "extra_notif_id"

    fun createNotificationChannels(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager = context.getSystemService(NotificationManager::class.java) ?: return

            val messagesChannel = NotificationChannel(
                CHANNEL_MESSAGES,
                "Messages",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications for incoming SMS and MMS messages"
                enableLights(true)
                enableVibration(true)
            }

            val scheduledChannel = NotificationChannel(
                CHANNEL_SCHEDULED,
                "Scheduled Messages",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Status of scheduled message deliveries"
            }

            notificationManager.createNotificationChannel(messagesChannel)
            notificationManager.createNotificationChannel(scheduledChannel)
        }
    }

    fun showIncomingMessageNotification(
        context: Context,
        senderAddress: String,
        senderName: String?,
        messageBody: String,
        timestamp: Long = System.currentTimeMillis()
    ) {
        val appDatabase = AppDatabase.getInstance(context)

        // Check if muted or blocked asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            val isBlocked = appDatabase.blockedNumberDao().isBlocked(senderAddress)
            // If blocked, discard notification
            val isMuted = appDatabase.conversationMetaDao().getAllMeta()

            val displayName = senderName ?: senderAddress
            val notificationId = senderAddress.hashCode()

            val contentIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra("thread_address", senderAddress)
            }
            val pendingContentIntent = PendingIntent.getActivity(
                context,
                notificationId,
                contentIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Direct Reply action
            val remoteInput = RemoteInput.Builder(KEY_TEXT_REPLY)
                .setLabel("Reply to $displayName")
                .build()

            val replyIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_DIRECT_REPLY
                putExtra(EXTRA_ADDRESS, senderAddress)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val replyPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 1,
                replyIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else PendingIntent.FLAG_UPDATE_CURRENT)
            )

            val replyAction = NotificationCompat.Action.Builder(
                android.R.drawable.ic_menu_send,
                "Reply",
                replyPendingIntent
            ).addRemoteInput(remoteInput).build()

            // Mark as read action
            val markReadIntent = Intent(context, NotificationActionReceiver::class.java).apply {
                action = ACTION_MARK_READ
                putExtra(EXTRA_ADDRESS, senderAddress)
                putExtra(EXTRA_NOTIFICATION_ID, notificationId)
            }
            val markReadPendingIntent = PendingIntent.getBroadcast(
                context,
                notificationId + 2,
                markReadIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val markReadAction = NotificationCompat.Action.Builder(
                android.R.drawable.checkbox_on_background,
                "Mark as read",
                markReadPendingIntent
            ).build()

            val person = Person.Builder()
                .setName(displayName)
                .setKey(senderAddress)
                .build()

            val messagingStyle = NotificationCompat.MessagingStyle(person)
                .addMessage(messageBody, timestamp, person)

            val builder = NotificationCompat.Builder(context, CHANNEL_MESSAGES)
                .setSmallIcon(android.R.drawable.sym_action_chat)
                .setStyle(messagingStyle)
                .setContentTitle(displayName)
                .setContentText(messageBody)
                .setContentIntent(pendingContentIntent)
                .setAutoCancel(true)
                .addAction(replyAction)
                .addAction(markReadAction)
                .setCategory(NotificationCompat.CATEGORY_MESSAGE)
                .setPriority(NotificationCompat.PRIORITY_HIGH)

            try {
                NotificationManagerCompat.from(context).notify(notificationId, builder.build())
            } catch (e: SecurityException) {
                // If POST_NOTIFICATIONS permission not granted
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
