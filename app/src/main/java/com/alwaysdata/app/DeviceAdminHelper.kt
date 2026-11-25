package com.alwaysdata.app

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/**
 * ساده شد: فقط Device Admin برای روشن کردن خودکار دیتا.
 * قفل صفحه کامل حذف شد (همون اروری که گفتی).
 * اگه دیتا خاموش شد → برنامه با Device Admin خودکار روشنش می‌کنه.
 * بدون ریست، بدون پاک شدن حافظه — فقط یکبار تایید.
 */
object DeviceAdminHelper {

    private fun dpm(context: Context): DevicePolicyManager =
        context.getSystemService(DevicePolicyManager::class.java)

    private fun admin(context: Context): ComponentName =
        ComponentName(context, MyDeviceAdminReceiver::class.java)

    fun isAdminActive(context: Context): Boolean = try {
        dpm(context).isAdminActive(admin(context))
    } catch (_: Exception) { false }

    fun requestEnableIntent(context: Context): Intent {
        return Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
            putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(context))
            putExtra(
                DevicePolicyManager.EXTRA_ADD_EXPLANATION,
                "برای اینکه اگه دیتای موبایل خاموش شد خودکار روشن بشه، این دسترسی لازمه. بدون ریست و بدون پاک شدن. فقط دیتا رو روشن می‌کنه، صفحه رو قفل نمی‌کنه."
            )
        }
    }
}
