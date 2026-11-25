package com.alwaysdata.app

import android.app.admin.DeviceAdminReceiver
import android.content.Context
import android.content.Intent

/**
 * Device Admin receiver.
 * تنها راه قفل واقعی در اندروید بدون روت همینه:
 *   adb shell dpm set-device-owner com.alwaysdata.app/.MyDeviceAdminReceiver
 * بعد از اون این کلاس خودکار همه‌ی محدودیت‌ها رو اعمال می‌کنه.
 */
class MyDeviceAdminReceiver : DeviceAdminReceiver() {
    override fun onEnabled(context: Context, intent: Intent) {
        super.onEnabled(context, intent)
        val mode = DeviceOwnerHelper.modeName(context)
        Prefs.log(context, "وضعیت $mode فعال شد")
        DeviceOwnerHelper.applyHardening(context)
        AlwaysDataService.start(context)
        CheckWorker.enqueue(context)
    }

    override fun onDisabled(context: Context, intent: Intent) {
        super.onDisabled(context, intent)
        Prefs.log(context, "وضعیت ${DeviceOwnerHelper.modeName(context)} غیرفعال شد")
    }

    override fun onDisableRequested(context: Context, intent: Intent): CharSequence {
        return "اگر این مدیریت غیرفعال شود، قفل همیشه‌روشن برداشته می‌شود"
    }
}
