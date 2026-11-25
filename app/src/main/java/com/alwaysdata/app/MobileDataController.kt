package com.alwaysdata.app

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.telephony.SubscriptionManager
import android.telephony.TelephonyManager

/**
 * Reads and (best-effort) writes the mobile-data state.
 *
 * Android has no public API to turn mobile data on/off since API 24, so we try
 * every known route, in order:
 *  1. ConnectivityManager.setMobileDataEnabled  (hidden API, reflection)
 *  2. TelephonyManager.setDataEnabled           (hidden API, reflection)
 *  3. IConnectivityService binder               (hidden API, reflection)
 *  4. ITelephony binder                         (hidden API, reflection)
 *  5. Settings.Global "mobile_data"             (needs WRITE_SECURE_SETTINGS;
 *                                               works for Device Owner / system apps)
 *
 * On many OEM builds (Samsung, Xiaomi, …) route 1 or 2 still works. When the
 * system blocks all of them we return false and the service keeps retrying +
 * notifies the user, so the data comes back within seconds of any manual
 * attempt to turn it off.
 */
object MobileDataController {

    /** True when mobile data is currently enabled. */
    fun isMobileDataEnabled(context: Context): Boolean {
        // 1) Public API (API 24+)
        try {
            val tm = context.getSystemService(TelephonyManager::class.java)
            if (tm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                return tm.isDataEnabled
            }
        } catch (_: Exception) {
        }

        // 2) Settings.Global mirror of the toggle
        try {
            val v = Settings.Global.getInt(context.contentResolver, "mobile_data", -1)
            if (v != -1) return v == 1
        } catch (_: Exception) {
        }

        // 3) Active network has a cellular transport?
        try {
            val cm = context.getSystemService(ConnectivityManager::class.java)
            val caps = cm.getNetworkCapabilities(cm.activeNetwork)
            if (caps != null) {
                return caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
            }
        } catch (_: Exception) {
        }

        return false
    }

    /**
     * Tries to set mobile data on/off. Returns true if any route succeeded.
     * Note: success here means the call was accepted; the system may still take
     * a moment to bring the radio up — callers poll [isMobileDataEnabled].
     */
    fun setMobileDataEnabled(context: Context, enabled: Boolean): Boolean {
        if (tryConnectivityManager(context, enabled)) return true
        if (tryTelephonyManager(context, enabled)) return true
        if (tryConnectivityServiceBinder(context, enabled)) return true
        if (tryTelephonyBinder(context, enabled)) return true
        if (trySettingsGlobal(context, enabled)) return true
        return false
    }

    // ------------------------------------------------------------------ routes

    private fun tryConnectivityManager(context: Context, enabled: Boolean): Boolean {
        return try {
            val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
            val m = ConnectivityManager::class.java.getMethod(
                "setMobileDataEnabled",
                Boolean::class.javaPrimitiveType
            )
            m.isAccessible = true
            m.invoke(cm, enabled)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryTelephonyManager(context: Context, enabled: Boolean): Boolean {
        return try {
            val tm = context.getSystemService(TelephonyManager::class.java) ?: return false
            val m = TelephonyManager::class.java.getMethod(
                "setDataEnabled",
                Boolean::class.javaPrimitiveType
            )
            m.isAccessible = true
            m.invoke(tm, enabled)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun tryConnectivityServiceBinder(context: Context, enabled: Boolean): Boolean {
        return try {
            val binder = getServiceBinder("connectivity") ?: return false
            val pkg = context.packageName
            // AOSP signatures seen in the wild:
            //   setMobileDataEnabled(boolean, String)
            //   setMobileDataEnabled(boolean, String, boolean)
            for (m in binder.javaClass.methods) {
                if (m.name != "setMobileDataEnabled") continue
                m.isAccessible = true
                val ok = try {
                    m.invoke(binder, enabled, pkg)
                    true
                } catch (_: Exception) {
                    try {
                        m.invoke(binder, enabled, pkg, true)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                if (ok) return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun tryTelephonyBinder(context: Context, enabled: Boolean): Boolean {
        return try {
            val binder = getServiceBinder("phone") ?: return false
            val subId = try {
                SubscriptionManager.getDefaultDataSubscriptionId()
            } catch (_: Exception) {
                SubscriptionManager.DEFAULT_SUBSCRIPTION_ID
            }
            val pkg = context.packageName
            for (m in binder.javaClass.methods) {
                if (m.name != "setDataEnabled") continue
                m.isAccessible = true
                val ok = try {
                    // setDataEnabled(int subId, boolean enabled, String packageName)
                    m.invoke(binder, subId, enabled, pkg)
                    true
                } catch (_: Exception) {
                    try {
                        // setDataEnabled(boolean enabled, String packageName)
                        m.invoke(binder, enabled, pkg)
                        true
                    } catch (_: Exception) {
                        false
                    }
                }
                if (ok) return true
            }
            false
        } catch (_: Exception) {
            false
        }
    }

    private fun trySettingsGlobal(context: Context, enabled: Boolean): Boolean {
        return try {
            // Only succeeds for system / Device Owner apps with
            // WRITE_SECURE_SETTINGS — harmless to try otherwise.
            Settings.Global.putInt(
                context.contentResolver,
                "mobile_data",
                if (enabled) 1 else 0
            )
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun getServiceBinder(name: String): IBinder? {
        return try {
            val clazz = Class.forName("android.os.ServiceManager")
            val m = clazz.getMethod("getService", String::class.java)
            m.isAccessible = true
            m.invoke(null, name) as? IBinder
        } catch (_: Exception) {
            null
        }
    }

    // ------------------------------------------------------------ extra helpers

    /** Human-readable network info for the UI (operator, type, signal). */
    fun networkInfo(context: Context): NetworkInfo {
        var operator = "—"
        var type = "—"
        var signal = "—"
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
                        TelephonyManager.NETWORK_TYPE_EDGE,
                        TelephonyManager.NETWORK_TYPE_GPRS -> "2G"
                        else -> "—"
                    }
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val level = tm.signalStrength?.level ?: -1
                    if (level in 0..4) signal = "$level از 4"
                }
            }
        } catch (_: Exception) {
        }
        return NetworkInfo(operator = operator, type = type, signal = signal)
    }

    data class NetworkInfo(val operator: String, val type: String, val signal: String)
}
