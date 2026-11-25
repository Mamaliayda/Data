package com.alwaysdata.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.provider.Settings

/**
 * حالت ساده‌ی مورد علاقه‌ی کاربر: با Device Admin (بدون نیاز به factory reset)
 * اگه دیتا خاموش شد → صفحه فوری قفل می‌شه → ۵ ثانیه بعد خودکار دیتا روشن می‌شه.
 * این «تنبیه» باعث می‌شه کسی هی خاموش/روشن بازی درنیاره.
 */
object DeviceAdminHelper {

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    private fun admin(context: Context): ComponentName =
        ComponentName(context, MyDeviceAdminReceiver::class.java)

    fun isAdminActive(context: Context): Boolean = try {
        dpm(context).isAdminActive(admin(context))
    } catch (_: Exception) { false }

    fun lockNow(context: Context): Boolean = try {
        // باید از سرویس/اکتیویتی باشه نه از receiver مستقیم
        dpm(context).lockNow()
        Prefs.log(context, "صفحه قفل شد (دیتا خاموش شده بود)")
        true
    } catch (e: Exception) {
        Prefs.log(context, "قفل صفحه ناموفق: ${e.message}")
        false
    }

    fun requestEnableIntent(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "برای اینکه اگه دیتا خاموش شد صفحه قفل بشه و ۵ ثانیه بعد خودکار روشن بشه، این دسترسی لازمه. بدون ریست و بدون پاک شدن حافظه."
            )
        }
    }

    fun openSecuritySettings(context: Context) {
        try { context.startActivity(Intent(Settings.ACTION_SECURITY_SETTINGS)) } catch (_: Exception) {}
    }
}
