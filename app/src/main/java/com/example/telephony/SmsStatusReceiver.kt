package com.example.telephony

import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsManager
import android.util.Log

class SmsStatusReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val messageId = intent.getLongExtra(SmsSender.EXTRA_MESSAGE_ID, -1L)
        val address = intent.getStringExtra(SmsSender.EXTRA_ADDRESS) ?: ""

        when (action) {
            SmsSender.ACTION_SMS_SENT -> {
                val resultCode = resultCode
                if (resultCode == Activity.RESULT_OK) {
                    Log.d("SmsStatusReceiver", "SMS sent successfully to $address (id=$messageId)")
                } else {
                    val errorCode = when (resultCode) {
                        SmsManager.RESULT_ERROR_GENERIC_FAILURE -> "Generic failure"
                        SmsManager.RESULT_ERROR_NO_SERVICE -> "No service"
                        SmsManager.RESULT_ERROR_NULL_PDU -> "Null PDU"
                        SmsManager.RESULT_ERROR_RADIO_OFF -> "Radio off (Airplane mode)"
                        else -> "Failed ($resultCode)"
                    }
                    Log.e("SmsStatusReceiver", "SMS sending failed to $address: $errorCode")
                }
            }

            SmsSender.ACTION_SMS_DELIVERED -> {
                Log.d("SmsStatusReceiver", "SMS delivered to $address (id=$messageId)")
            }
        }
    }
}
