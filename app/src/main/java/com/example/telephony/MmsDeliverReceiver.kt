package com.example.telephony

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * BroadcastReceiver required by Android to be designated as the default SMS/MMS application.
 * Handles incoming MMS WAP push messages.
 */
class MmsDeliverReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Android default MMS receiver stub - keeps default SMS qualification intact
    }
}
