package com.alwaysdata.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager

/**
 * هر کاری که با Device Owner یا Profile Owner ممکنه اینجاست.
 * مهم‌ترینش DISALLOW_CONFIG_MOBILE_NETWORKS هست که سوییچ «دیتای موبایل»
 * رو در تنظیمات خاکستری می‌کنه.
 *
 * نکته‌ی مهم برای تو (چون نمی‌خوای حافظه پاک بشه):
 *  • Device Owner → قوی‌ترینه ولی حتّا با factory reset می‌خواد
 *  • Profile Owner → همون قفل سوییچ رو بدون پاک شدن می‌ده، با یک دستور ADB
 *    ساده:  adb shell dpm set-profile-owner com.alwaysdata.app/.MyDeviceAdminReceiver
 *    اندروید عمدا این اجازه رو بدون ریست می‌ده چون امن‌تره.
 */
object DeviceOwnerHelper {

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    private fun admin(context: Context): ComponentName =
        ComponentName(context, MyDeviceAdminReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean = try {
        dpm(context).isDeviceOwnerApp(context.packageName)
    } catch (_: Exception) { false }

    fun isProfileOwner(context: Context): Boolean = try {
        dpm(context).isProfileOwnerApp(context.packageName)
    } catch (_: Exception) { false }

    fun isManaged(context: Context): Boolean = isDeviceOwner(context) || isProfileOwner(context)

    fun modeName(context: Context): String = when {
        isDeviceOwner(context) -> "Device Owner"
        isProfileOwner(context) -> "Profile Owner"
        else -> "معمولی"
    }

    /** تمام قفل‌های سخت رو یک‌جا اعمال کن — روی هر دو حالت کار می‌کنه */
    fun applyHardening(context: Context): List<String> {
        val applied = mutableListOf<String>()
        if (!isManaged(context)) return applied
        val d = dpm(context); val a = admin(context)
        fun add(restriction: String, label: String) {
            try { d.addUserRestriction(a, restriction); applied.add(label) } catch (_: Exception) {}
        }
        // همین یکی مهم‌ترینه: سوییچ دیتا در تنظیمات قفل می‌شه — روی Profile Owner هم کار می‌کنه!
        add(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS, "قفل تنظیمات دیتای موبایل")
        add(UserManager.DISALLOW_NETWORK_RESET, "جلوگیری از ریست تنظیمات شبکه")
        add(UserManager.DISALLOW_CONFIG_TETHERING, "جلوگیری از تغییر هات‌اسپات")
        add(UserManager.DISALLOW_SAFE_BOOT, "جلوگیری از حالت امن (Safe Mode)")
        add(UserManager.DISALLOW_ADD_USER, "جلوگیری از ساخت کاربر جدید")
        add(UserManager.DISALLOW_ADD_MANAGED_PROFILE, "جلوگیری از پروفایل کاری جدید")
        add(UserManager.DISALLOW_REMOVE_USER, "جلوگیری از حذف کاربر")
        add(UserManager.DISALLOW_APPS_CONTROL, "جلوگیری از کنترل برنامه‌ها")
        add(UserManager.DISALLOW_UNINSTALL_APPS, "جلوگیری از حذف برنامه‌ها")
        add(UserManager.DISALLOW_CONFIG_VPN, "جلوگیری از تغییر VPN")
        add(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, "جلوگیری از مانت حافظه جانبی")
        // این دو تا فقط روی Device Owner جواب می‌دن، روی Profile Owner بی‌خطر فِیل می‌شن
        add(UserManager.DISALLOW_DEBUGGING_FEATURES, "جلوگیری از فعال‌سازی دیباگ")
        add(UserManager.DISALLOW_FACTORY_RESET, "جلوگیری از ریست کارخانه (از داخل تنظیمات)")
        Prefs.log(context, "سخت‌سازی ${modeName(context)} اعمال شد: ${applied.joinToString("، ")}")
        return applied
    }

    fun removeHardening(context: Context) {
        if (!isManaged(context)) return
        val d = dpm(context); val a = admin(context)
        val all = listOf(
            UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
            UserManager.DISALLOW_NETWORK_RESET,
            UserManager.DISALLOW_CONFIG_TETHERING,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_ADD_MANAGED_PROFILE,
            UserManager.DISALLOW_REMOVE_USER,
            UserManager.DISALLOW_APPS_CONTROL,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_CONFIG_VPN,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_FACTORY_RESET,
        )
        for (r in all) try { d.clearUserRestriction(a, r) } catch (_: Exception) {}
        Prefs.log(context, "سخت‌سازی ${modeName(context)} برداشته شد")
    }

    /** هر ۲ ثانیه دوباره چک کن که کسی محدودیت رو برنداشته باشه */
    fun reEnforceIfNeeded(context: Context) {
        if (!isManaged(context)) return
        try {
            val d = dpm(context); val a = admin(context)
            val bundle = d.getUserRestrictions(a)
            if (!bundle.getBoolean(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS, false)) {
                d.addUserRestriction(a, UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS)
                Prefs.log(context, "مجدد قفل شد: تنظیمات دیتا دوباره بسته شد")
            }
        } catch (_: Exception) {}
    }
}
