package com.alwaysdata.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast

/**
 * مخفی کردن آیکون از صفحه اصلی.
 * با غیرفعال کردن کامپوننت لانچر، آیکون میره کنار ولی برنامه نصب می‌مونه و سرویس کار می‌کنه.
 * برگردوندن: تنظیمات → برنامه‌ها → دیتای همیشه روشن → باز کردن
 * یا با دستور ADB: adb shell cmd package set-component-enabled com.alwaysdata.app/com.alwaysdata.app.MainActivity enabled
 */
object HideHelper {

    private fun launcherCN(context: Context) = ComponentName(context, MainActivity::class.java)

    fun isHidden(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            pm.getComponentEnabledSetting(launcherCN(context)) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (_: Exception) { false }
    }

    fun hide(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                launcherCN(context),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Prefs.log(context, "برنامه از لانچر مخفی شد")
            Toast.makeText(context, "آیکون مخفی شد — برای برگرداندن: تنظیمات → برنامه‌ها → دیتای همیشه روشن → باز کردن", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            Prefs.log(context, "مخفی‌سازی ناموفق: ${e.message}")
            false
        }
    }

    fun show(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                launcherCN(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Prefs.log(context, "برنامه دوباره نمایان شد")
            true
        } catch (_: Exception) { false }
    }
}
