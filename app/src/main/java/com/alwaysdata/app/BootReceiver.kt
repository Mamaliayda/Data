package com.alwaysdata.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * Brings the protection back after reboot / update / unlock.
 * Only starts the service when the user has enabled protection in the app.
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        val relevant = action in setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_USER_PRESENT,
            "android.intent.action.QUICKBOOT_POWERON",
            "android.intent.action.LOCKED_BOOT_COMPLETED"
        )
        if (!relevant) return
        if (!Prefs.isProtectionEnabled(context)) return
        Prefs.log(context, "راه‌اندازی مجدد پس از $action")
        AlwaysDataService.start(context)
        CheckWorker.enqueue(context)
    }
}
