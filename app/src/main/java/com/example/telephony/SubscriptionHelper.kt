package com.example.telephony

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import androidx.core.content.ContextCompat
import com.example.data.model.SimInfo

object SubscriptionHelper {

    fun getActiveSubscriptions(context: Context): List<SimInfo> {
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

        val subscriptionManager = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE) as? SubscriptionManager
            ?: return emptyList()

        if (!hasPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            return emptyList()
        }

        return try {
            val list = subscriptionManager.activeSubscriptionInfoList ?: emptyList()
            list.map { info: SubscriptionInfo ->
                SimInfo(
                    subId = info.subscriptionId,
                    simSlotIndex = info.simSlotIndex,
                    displayName = info.displayName?.toString() ?: "SIM ${info.simSlotIndex + 1}",
                    carrierName = info.carrierName?.toString() ?: "Carrier",
                    number = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        try {
                            subscriptionManager.getPhoneNumber(info.subscriptionId)
                        } catch (e: Exception) {
                            info.number
                        }
                    } else {
                        @Suppress("DEPRECATION")
                        info.number
                    }
                )
            }
        } catch (e: SecurityException) {
            emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun getDefaultSubscriptionId(): Int {
        return SubscriptionManager.getDefaultSmsSubscriptionId()
    }
}
