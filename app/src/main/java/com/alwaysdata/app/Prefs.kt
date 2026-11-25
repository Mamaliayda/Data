package com.alwaysdata.app

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Tiny SharedPreferences wrapper: protection flag, counters and event log. */
object Prefs {
    private const val FILE = "always_data_prefs"
    private const val KEY_ENABLED = "protection_enabled"
    private const val KEY_COUNT = "auto_enable_count"
    private const val KEY_LAST_CHECK = "last_check"
    private const val KEY_LOG = "event_log"
    // قدیمی‌ها می‌مونن برای سازگاری ولی دیگه استفاده نمی‌شن (قفل صفحه حذف شد)
    private const val KEY_LOCK_ON_DISABLE = "lock_on_disable"
    private const val KEY_LOCK_DELAY_MS = "lock_delay_ms"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    fun isProtectionEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_ENABLED, false)

    fun setProtectionEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun getAutoEnableCount(context: Context): Int =
        prefs(context).getInt(KEY_COUNT, 0)

    fun incrementAutoEnableCount(context: Context) {
        val p = prefs(context)
        p.edit().putInt(KEY_COUNT, p.getInt(KEY_COUNT, 0) + 1).apply()
    }

    fun setLastCheck(context: Context, timeMs: Long) {
        prefs(context).edit().putLong(KEY_LAST_CHECK, timeMs).apply()
    }

    fun getLastCheck(context: Context): Long =
        prefs(context).getLong(KEY_LAST_CHECK, 0L)

    fun log(context: Context, message: String) {
        val time = SimpleDateFormat("HH:mm:ss", Locale("fa")).format(Date())
        val p = prefs(context)
        val lines = (p.getString(KEY_LOG, "") ?: "")
            .split("\n")
            .filter { it.isNotBlank() }
            .toMutableList()
        lines.add("[$time] $message")
        while (lines.size > 60) lines.removeAt(0)
        p.edit().putString(KEY_LOG, lines.joinToString("\n")).apply()
    }

    fun getLog(context: Context): List<String> =
        (prefs(context).getString(KEY_LOG, "") ?: "")
            .split("\n")
            .filter { it.isNotBlank() }
            .reversed()

    fun isLockOnDisableEnabled(context: Context): Boolean =
        prefs(context).getBoolean(KEY_LOCK_ON_DISABLE, false)

    fun setLockOnDisableEnabled(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_LOCK_ON_DISABLE, enabled).apply()
    }

    fun getLockDelayMs(context: Context): Long =
        prefs(context).getLong(KEY_LOCK_DELAY_MS, 5000L)

    fun setLockDelayMs(context: Context, ms: Long) {
        prefs(context).edit().putLong(KEY_LOCK_DELAY_MS, ms).apply()
    }
}
