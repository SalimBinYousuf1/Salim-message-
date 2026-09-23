package com.example.telephony

import android.Manifest
import android.app.PendingIntent
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Telephony
import android.telephony.SmsManager
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SmsSender(private val context: Context) {

    companion object {
        const val ACTION_SMS_SENT = "com.example.salim.SMS_SENT"
        const val ACTION_SMS_DELIVERED = "com.example.salim.SMS_DELIVERED"
        const val EXTRA_MESSAGE_ID = "extra_message_id"
        const val EXTRA_THREAD_ID = "extra_thread_id"
        const val EXTRA_ADDRESS = "extra_address"
    }

    private fun hasSendSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    suspend fun sendSms(
        destinationAddress: String,
        messageText: String,
        subId: Int = -1
    ): Result<Long> = withContext(Dispatchers.IO) {
        val trimmedAddress = destinationAddress.trim()
        val trimmedText = messageText.trim()

        if (trimmedAddress.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Recipient address cannot be empty"))
        }
        if (trimmedText.isEmpty()) {
            return@withContext Result.failure(IllegalArgumentException("Message content cannot be empty"))
        }
        if (!hasSendSmsPermission()) {
            return@withContext Result.failure(SecurityException("SEND_SMS permission not granted"))
        }

        try {
            val smsManager: SmsManager = when {
                subId > 0 && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    context.getSystemService(SmsManager::class.java).createForSubscriptionId(subId)
                }
                subId > 0 -> {
                    @Suppress("DEPRECATION")
                    SmsManager.getSmsManagerForSubscriptionId(subId)
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    context.getSystemService(SmsManager::class.java)
                }
                else -> {
                    @Suppress("DEPRECATION")
                    SmsManager.getDefault()
                }
            }

            // Write record to Telephony Sent box
            var insertedId = System.currentTimeMillis()
            var threadId = 0L

            try {
                val values = ContentValues().apply {
                    put(Telephony.Sms.ADDRESS, trimmedAddress)
                    put(Telephony.Sms.BODY, trimmedText)
                    put(Telephony.Sms.DATE, System.currentTimeMillis())
                    put(Telephony.Sms.READ, 1)
                    put(Telephony.Sms.SEEN, 1)
                    put(Telephony.Sms.TYPE, Telephony.Sms.MESSAGE_TYPE_SENT)
                    if (subId > 0) {
                        put(Telephony.Sms.SUBSCRIPTION_ID, subId)
                    }
                }
                val insertedUri: Uri? = context.contentResolver.insert(Telephony.Sms.Sent.CONTENT_URI, values)
                if (insertedUri != null) {
                    insertedId = insertedUri.lastPathSegment?.toLongOrNull() ?: insertedId
                }
            } catch (e: Exception) {
                // If not default SMS app, writing to Sent box directly might fail
            }

            val parts = smsManager.divideMessage(trimmedText)

            val sentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val sentIntent = Intent(ACTION_SMS_SENT).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_MESSAGE_ID, insertedId)
                putExtra(EXTRA_THREAD_ID, threadId)
                putExtra(EXTRA_ADDRESS, trimmedAddress)
            }
            val deliveryIntent = Intent(ACTION_SMS_DELIVERED).apply {
                setPackage(context.packageName)
                putExtra(EXTRA_MESSAGE_ID, insertedId)
                putExtra(EXTRA_THREAD_ID, threadId)
                putExtra(EXTRA_ADDRESS, trimmedAddress)
            }

            if (parts.size > 1) {
                val sentPIs = ArrayList<PendingIntent>()
                val deliveredPIs = ArrayList<PendingIntent>()
                for (i in parts.indices) {
                    sentPIs.add(
                        PendingIntent.getBroadcast(
                            context,
                            (insertedId + i).toInt(),
                            sentIntent,
                            sentFlags
                        )
                    )
                    deliveredPIs.add(
                        PendingIntent.getBroadcast(
                            context,
                            (insertedId + i + 1000).toInt(),
                            deliveryIntent,
                            sentFlags
                        )
                    )
                }
                smsManager.sendMultipartTextMessage(
                    trimmedAddress,
                    null,
                    parts,
                    sentPIs,
                    deliveredPIs
                )
            } else {
                val sentPI = PendingIntent.getBroadcast(
                    context,
                    insertedId.toInt(),
                    sentIntent,
                    sentFlags
                )
                val deliveryPI = PendingIntent.getBroadcast(
                    context,
                    (insertedId + 1000).toInt(),
                    deliveryIntent,
                    sentFlags
                )
                smsManager.sendTextMessage(
                    trimmedAddress,
                    null,
                    trimmedText,
                    sentPI,
                    deliveryPI
                )
            }

            Result.success(insertedId)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
