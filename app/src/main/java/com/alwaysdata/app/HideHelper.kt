package com.alwaysdata.app

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.widget.Toast

/**
 * مخفی کردن آیکون از لانچر.
 * روی activity-alias انجام می‌شه نه روی خود MainActivity تا قابل برگشت باشه.
 * برگردوندن: تنظیمات → برنامه‌ها → دیتای همیشه روشن → باز کردن
 */
object HideHelper {

    private fun aliasCN(context: Context) = ComponentName(context, "com.alwaysdata.app.LauncherAlias")
    private fun mainCN(context: Context) = ComponentName(context, MainActivity::class.java)

    fun rawState(context: Context): Int = try {
        context.packageManager.getComponentEnabledSetting(aliasCN(context))
    } catch (_: Exception) { -99 }

    fun isHidden(context: Context): Boolean {
        return try {
            rawState(context) == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (_: Exception) { false }
    }

    fun hide(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            pm.setComponentEnabledSetting(
                aliasCN(context),
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                PackageManager.DONT_KILL_APP
            )
            Prefs.log(context, "برنامه از لانچر مخفی شد (alias)")
            Toast.makeText(context, "مخفی شد ✓ — برای برگرداندن: تنظیمات → برنامه‌ها → دیتای همیشه روشن → باز کردن", Toast.LENGTH_LONG).show()
            true
        } catch (e: Exception) {
            Prefs.log(context, "مخفی‌سازی alias ناموفق: ${e.message} — تلاش روی MainActivity")
            try {
                context.packageManager.setComponentEnabledSetting(
                    mainCN(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP
                )
                Toast.makeText(context, "مخفی شد (حالت دوم) ✓", Toast.LENGTH_SHORT).show()
                true
            } catch (e2: Exception) {
                Prefs.log(context, "مخفی‌سازی کامل ناموفق: ${e2.message}")
                Toast.makeText(context, "مخفی نشد: ${e2.message}", Toast.LENGTH_LONG).show()
                false
            }
        }
    }

    fun show(context: Context): Boolean {
        return try {
            context.packageManager.setComponentEnabledSetting(
                aliasCN(context),
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            Prefs.log(context, "برنامه دوباره نمایان شد")
            true
        } catch (_: Exception) { false }
    }
}
