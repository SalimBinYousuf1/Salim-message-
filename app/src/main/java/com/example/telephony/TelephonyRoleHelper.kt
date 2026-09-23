package com.example.telephony

import android.app.Activity
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts

object TelephonyRoleHelper {

    fun isDefaultSmsApp(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.isRoleHeld(RoleManager.ROLE_SMS) == true
        } else {
            val defaultPackage = Telephony.Sms.getDefaultSmsPackage(context)
            defaultPackage != null && defaultPackage == context.packageName
        }
    }

    fun createRequestDefaultSmsIntent(context: Context): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = context.getSystemService(RoleManager::class.java)
            roleManager?.createRequestRoleIntent(RoleManager.ROLE_SMS)
        } else {
            @Suppress("DEPRECATION")
            Intent(Telephony.Sms.Intents.ACTION_CHANGE_DEFAULT).apply {
                putExtra(Telephony.Sms.Intents.EXTRA_PACKAGE_NAME, context.packageName)
            }
        }
    }

    fun createDefaultSmsLauncher(
        activity: ComponentActivity,
        onResult: (Boolean) -> Unit
    ): ActivityResultLauncher<Intent> {
        return activity.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            onResult(result.resultCode == Activity.RESULT_OK || isDefaultSmsApp(activity))
        }
    }

    fun requestDefaultSmsRole(
        context: Context,
        launcher: ActivityResultLauncher<Intent>
    ) {
        val intent = createRequestDefaultSmsIntent(context)
        if (intent != null) {
            try {
                launcher.launch(intent)
            } catch (e: Exception) {
                // Ignore
            }
        }
    }
}
