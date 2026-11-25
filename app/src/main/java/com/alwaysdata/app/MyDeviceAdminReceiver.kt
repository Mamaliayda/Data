package com.alwaysdata.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device Admin receiver. Its only job is to let the app be granted
 * Device Owner status via ADB:
 *
 *   adb shell dpm set-device-owner com.alwaysdata.app/.MyDeviceAdminReceiver
 *
 * As Device Owner the app cannot be uninstalled without first removing its
 * owner status, and it can write the Settings.Global "mobile_data" flag —
 * the strongest lock Android allows without root.
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        Prefs.log(context, "وضعیت Device Admin فعال شد")
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Prefs.log(context, "وضعیت Device Admin غیرفعال شد")
    }
}
