package com.alwaysdata.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.os.UserManager

/**
 * هر کاری که فقط با Device Owner ممکنه اینجاست.
 * با DISALLOW_CONFIG_MOBILE_NETWORKS خود سوییچِ «دیتای موبایل» در تنظیمات
 * خاکستری و غیرقابل لمس می‌شه — یعنی دیگه راحت نمی‌شه خاموشش کرد.
 * بقیه محدودیت‌ها هم دور زدن‌های رایج رو می‌بندن.
 */
object DeviceOwnerHelper {

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    private fun admin(context: Context): ComponentName =
        ComponentName(context, MyDeviceAdminReceiver::class.java)

    fun isDeviceOwner(context: Context): Boolean = try {
        dpm(context).isDeviceOwnerApp(context.packageName)
    } catch (_: Exception) { false }

    /** تمام قفل‌های سخت رو یک‌جا اعمال کن */
    fun applyHardening(context: Context): List<String> {
        val applied = mutableListOf<String>()
        if (!isDeviceOwner(context)) return applied
        val d = dpm(context); val a = admin(context)
        fun add(restriction: String, label: String) {
            try { d.addUserRestriction(a, restriction); applied.add(label) } catch (_: Exception) {}
        }
        // همین یکی مهم‌ترینه: تنظیمات موبایل نتورک کلا قفل می‌شه
        add(UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS, "قفل تنظیمات دیتای موبایل")
        add(UserManager.DISALLOW_NETWORK_RESET, "جلوگیری از ریست تنظیمات شبکه")
        add(UserManager.DISALLOW_SAFE_BOOT, "جلوگیری از حالت امن (Safe Mode)")
        add(UserManager.DISALLOW_ADD_USER, "جلوگیری از ساخت کاربر جدید")
        add(UserManager.DISALLOW_REMOVE_USER, "جلوگیری از حذف کاربر")
        // این دو تا روی خیلی از گوشی‌ها جلوی حذف/کنترل برنامه رو می‌گیرن
        add(UserManager.DISALLOW_APPS_CONTROL, "جلوگیری از کنترل برنامه‌ها")
        add(UserManager.DISALLOW_UNINSTALL_APPS, "جلوگیری از حذف برنامه‌ها")
        add(UserManager.DISALLOW_CONFIG_VPN, "جلوگیری از تغییر VPN")
        add(UserManager.DISALLOW_DEBUGGING_FEATURES, "جلوگیری از فعال‌سازی دیباگ")
        add(UserManager.DISALLOW_FACTORY_RESET, "جلوگیری از ریست کارخانه از داخل تنظیمات")
        add(UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA, "جلوگیری از مانت حافظه جانبی")
        // مخفی کردن نوار وضعیت / کی‌گارد اختیاریه — فعلا فعال نمی‌کنیم چون گوشی رو قفل می‌کنه
        Prefs.log(context, "سخت‌سازی Device Owner اعمال شد: ${applied.joinToString("، ")}")
        return applied
    }

    fun removeHardening(context: Context) {
        if (!isDeviceOwner(context)) return
        val d = dpm(context); val a = admin(context)
        val all = listOf(
            UserManager.DISALLOW_CONFIG_MOBILE_NETWORKS,
            UserManager.DISALLOW_NETWORK_RESET,
            UserManager.DISALLOW_SAFE_BOOT,
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_REMOVE_USER,
            UserManager.DISALLOW_APPS_CONTROL,
            UserManager.DISALLOW_UNINSTALL_APPS,
            UserManager.DISALLOW_CONFIG_VPN,
            UserManager.DISALLOW_DEBUGGING_FEATURES,
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_MOUNT_PHYSICAL_MEDIA,
        )
        for (r in all) try { d.clearUserRestriction(a, r) } catch (_: Exception) {}
        Prefs.log(context, "سخت‌سازی Device Owner برداشته شد")
    }

    /** هر ۲ ثانیه دوباره چک کن که کسی محدودیت رو برنداشته باشه */
    fun reEnforceIfNeeded(context: Context) {
        if (!isDeviceOwner(context)) return
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
