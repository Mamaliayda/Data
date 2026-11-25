package com.alwaysdata.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

object MobileDataController {

    fun isMobileDataEnabled(context: Context): Boolean {
        try {
            val tm = context.getSystemService(TelephonyManager::class.java)
            if (tm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return tm.isDataEnabled
            }
        } catch (_: Exception) {}
        try {
            val v = Settings.Global.getInt(context.contentResolver, "mobile_data", -1)
            if (v != -1) return v == 1
        } catch (_: Exception) {}
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            if (caps != null) return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
        } catch (_: Exception) {}
        return false
    }

    data class AttemptResult(val method: String, val success: Boolean, val error: String?)

    var lastAttempts: List<AttemptResult> = emptyList()
        private set
    var lastErrorDetail: String = ""
        private set

    fun debugStatus(context: Context): String {
        val sb = StringBuilder()
        // 1. TelephonyManager.isDataEnabled
        try {
            val tm = context.getSystemService(TelephonyManager::class.java)
            sb.append("TelephonyManager.isDataEnabled = ")
            if (tm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sb.append(tm.isDataEnabled).append(" (API ${Build.VERSION.SDK_INT})\n")
            } else sb.append("نامشخص\n")
        } catch (e: Exception) { sb.append("خطا: ${e.message}\n") }

        // 2. Settings.Global
        try {
            val v = Settings.Global.getInt(context.contentResolver, "mobile_data", -99)
            sb.append("Settings.Global mobile_data = $v\n")
        } catch (e: Exception) { sb.append("Settings.Global خطا: ${e.message}\n") }

        // 3. Active network caps
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            sb.append("activeNetwork has CELLULAR = ${caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)}\n")
            sb.append("activeNetwork = ${cm.activeNetwork}\n")
        } catch (e: Exception) { sb.append("NetworkCapabilities خطا: ${e.message}\n") }

        sb.append("Android ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT}) - ${Build.MANUFACTURER} ${Build.MODEL}\n")
        sb.append("Device Admin = ${DeviceAdminHelper.isAdminActive(context)}\n")
        sb.append("Device/Profile Owner = ${DeviceOwnerHelper.modeName(context)} (managed=${DeviceOwnerHelper.isManaged(context)})\n")
        sb.append("Battery exempt = ${isBatteryExempt(context)}\n")
        sb.append("HideHelper isHidden = ${HideHelper.isHidden(context)} (state=${HideHelper.rawState(context)})\n")
        if (lastAttempts.isNotEmpty()) {
            sb.append("--- آخرین تلاش‌ها ---\n")
            for (a in lastAttempts) {
                sb.append("${a.method}: ${if (a.success) "✓ موفق" else "✗ شکست"}")
                if (a.error != null) sb.append(" → ${a.error}")
                sb.append("\n")
            }
            sb.append("آخرین جزئیات: $lastErrorDetail\n")
        }
        return sb.toString()
    }

    private fun isBatteryExempt(context: Context): Boolean = try {
        val pm = context.getSystemService(android.os.PowerManager::class.java)
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) { false }

    fun setMobileDataEnabled(context: Context, enabled: Boolean): Boolean {
        val attempts = mutableListOf<AttemptResult>()
        fun tryOne(name: String, block: () -> Boolean): Boolean {
            return try {
                val ok = block()
                attempts.add(AttemptResult(name, ok, if (ok) null else "false برگشت"))
                ok
            } catch (e: Exception) {
                val msg = "${e.javaClass.simpleName}: ${e.message}"
                attempts.add(AttemptResult(name, false, msg))
                false
            }
        }

        val result = tryOne("ConnectivityManager.setMobileDataEnabled") { tryConnectivityManager(context, enabled) } ||
                tryOne("TelephonyManager.setDataEnabled") { tryTelephonyManager(context, enabled) } ||
                tryOne("IConnectivityService binder") { tryConnectivityServiceBinder(context, enabled) } ||
                tryOne("ITelephony binder") { tryTelephonyBinder(context, enabled) } ||
                tryOne("Settings.Global mobile_data") { trySettingsGlobal(context, enabled) }

        lastAttempts = attempts
        lastErrorDetail = if (result) "حداقل یک روش جواب داد ولی سیستم ممکنه دوباره چک کنه"
        else "همه ۵ روش شکست خوردن — روی اندروید 10+ بدون Profile/Device Owner عادیه"

        // لاگ دقیق برای دیباگ
        Prefs.log(context, "دیباگ: ${attempts.joinToString(" | ") { "${it.method}=${if (it.success) "OK" else "FAIL:${it.error}"}" }}")

        return result
    }

    // ------------------------------------------------------------------ routes (throw on fail so caller logs)

    private fun tryConnectivityManager(context: Context, enabled: Boolean): Boolean {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: throw Exception("ConnectivityManager null")
        val m = ConnectivityManager::class.java.getMethod("setMobileDataEnabled", Boolean::class.javaPrimitiveType)
        m.isAccessible = true
        m.invoke(cm, enabled)
        return true
    }

    private fun tryTelephonyManager(context: Context, enabled: Boolean): Boolean {
        val tm = context.getSystemService(TelephonyManager::class.java) ?: throw Exception("TelephonyManager null")
        val m = TelephonyManager::class.java.getMethod("setDataEnabled", Boolean::class.javaPrimitiveType)
        m.isAccessible = true
        m.invoke(tm, enabled)
        return true
    }

    private fun tryConnectivityServiceBinder(context: Context, enabled: Boolean): Boolean {
        val binder = getServiceBinder("connectivity") ?: throw Exception("binder connectivity null")
        val pkg = context.packageName
        for (m in binder.javaClass.methods) {
            if (m.name != "setMobileDataEnabled") continue
            m.isAccessible = true
            try { m.invoke(binder, enabled, pkg); return true } catch (_: Exception) {
                try { m.invoke(binder, enabled, pkg, true); return true } catch (_: Exception) {}
            }
        }
        throw Exception("setMobileDataEnabled not found on binder")
    }

    private fun tryTelephonyBinder(context: Context, enabled: Boolean): Boolean {
        val binder = getServiceBinder("phone") ?: throw Exception("binder phone null")
        val subId = try { SubscriptionManager.getDefaultDataSubscriptionId() } catch (_: Exception) { SubscriptionManager.DEFAULT_SUBSCRIPTION_ID }
        val pkg = context.packageName
        for (m in binder.javaClass.methods) {
            if (m.name != "setDataEnabled") continue
            m.isAccessible = true
            try { m.invoke(binder, subId, enabled, pkg); return true } catch (_: Exception) {
                try { m.invoke(binder, enabled, pkg); return true } catch (_: Exception) {}
            }
        }
        throw Exception("setDataEnabled not found on binder")
    }

    private fun trySettingsGlobal(context: Context, enabled: Boolean): Boolean {
        Settings.Global.putInt(context.contentResolver, "mobile_data", if (enabled) 1 else 0)
        return true
    }

    private fun getServiceBinder(name: String): IBinder? {
        return try {
            val clazz = Class.forName("android.os.ServiceManager")
            val m = clazz.getMethod("getService", String::class.java)
            m.isAccessible = true
            m.invoke(null, name) as? IBinder
        } catch (_: Exception) { null }
    }

    data class NetworkInfo(val operator: String, val type: String, val signal: String)

    fun networkInfo(context: Context): NetworkInfo {
        var operator = "—"; var type = "—"; var signal = "—"
        try {
            val tm = context.getSystemService(TelephonyManager::class.java)
            if (tm != null) {
                operator = tm.networkOperatorName?.takeIf { it.isNotBlank() } ?: "—"
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    type = when (tm.dataNetworkType) {
                        TelephonyManager.NETWORK_TYPE_NR -> "5G"
                        TelephonyManager.NETWORK_TYPE_LTE -> "4G"
                        TelephonyManager.NETWORK_TYPE_UMTS,
                        TelephonyManager.NETWORK_TYPE_HSDPA,
                        TelephonyManager.NETWORK_TYPE_HSUPA,
                        TelephonyManager.NETWORK_TYPE_HSPA -> "3G"
                        TelephonyManager.NETWORK_TYPE_EDGE, TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                        else -> "—"
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val level = tm.signalStrength?.level ?: -1
                    if (level in 0..4) signal = "$level از 4"
                }
            }
        } catch (_: Exception) {}
        return NetworkInfo(operator, type, signal)
    }
}
