package com.example.telephony

import android.app.Service
import android.content.Intent
import android.net.Uri
import android.os.IBinder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Service required by Android to be designated as the default SMS application.
 * Handles respond-via-message intents (e.g. quick reject incoming phone calls with SMS).
 */
class HeadlessSmsSendService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == TelephonyManagerConstants.ACTION_RESPOND_VIA_MESSAGE) {
            val uri: Uri? = intent.data
            val recipient = uri?.schemeSpecificPart ?: ""
            val message = intent.getStringExtra(Intent.EXTRA_TEXT) ?: ""

            if (recipient.isNotBlank() && message.isNotBlank()) {
                val smsSender = SmsSender(applicationContext)
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        smsSender.sendSms(recipient, message)
                    } finally {
                        stopSelf(startId)
                    }
                }
                return START_NOT_STICKY
            }
        }

        stopSelf(startId)
        return START_NOT_STICKY
    }
}

object TelephonyManagerConstants {
    const val ACTION_RESPOND_VIA_MESSAGE = "android.intent.action.RESPOND_VIA_MESSAGE"
}
