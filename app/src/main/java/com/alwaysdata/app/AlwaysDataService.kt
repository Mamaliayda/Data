package com.alwaysdata.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * سرویس قلب برنامه.
 * هر POLL_MS چک می‌کنه. اگه حفاظت روشنه و دیتا خاموشه:
 *  1) اگه حالت «قفل با Device Admin» فعاله → صفحه فوری lockNow() می‌شه
 *  2) بعد 5 ثانیه (قابل تنظیم) خودکار MobileDataController روشن می‌کنه
 *  3) دوباره چک می‌کنه تا مطمئن بشه روشن مونده
 */
class AlwaysDataService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNotificationState: Boolean? = null
    private var lastLockTime = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        acquireWakeLock()
        registerNetworkCallback()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, buildNotification(dataOn = null))
        if (loopJob == null || loopJob?.isActive != true) {
            loopJob = scope.launch { pollLoop() }
        }
        return START_STICKY
    }

    private suspend fun pollLoop() {
        while (true) {
            try { checkOnce() } catch (_: Exception) {}
            delay(POLL_MS)
        }
    }

    private fun checkOnce() {
        val now = System.currentTimeMillis()
        Prefs.setLastCheck(this, now)

        val protectionOn = Prefs.isProtectionEnabled(this)
        val dataOn = MobileDataController.isMobileDataEnabled(this)

        if (protectionOn && DeviceOwnerHelper.isManaged(this)) {
            DeviceOwnerHelper.reEnforceIfNeeded(this)
        }

        if (protectionOn && !dataOn) {
            handleDataOff()
        }

        if (lastNotificationState != dataOn) {
            lastNotificationState = dataOn
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(dataOn))
        }
        StatusWidget.refresh(this)
    }

    private fun handleDataOff() {
        val lockEnabled = Prefs.isLockOnDisableEnabled(this)
        val isAdmin = DeviceAdminHelper.isAdminActive(this)
        val now = System.currentTimeMillis()

        // جلوگیری از قفل پشت سر هم (هر 2 ثانیه چک می‌کنیم ولی قفل هر 7 ثانیه حداکثر)
        val shouldLock = lockEnabled && isAdmin && (now - lastLockTime > 7000L)

        if (shouldLock) {
            lastLockTime = now
            Prefs.log(this, "دیتا خاموش شد → صفحه قفل می‌شود")
            DeviceAdminHelper.lockNow(this)
        }

        // 5 ثانیه بعد (یا همون لحظه اگه قفل خاموشه) دیتا رو روشن کن
        val delayMs = if (lockEnabled && isAdmin) Prefs.getLockDelayMs(this) else 800L

        Prefs.incrementAutoEnableCount(this)

        scope.launch {
            delay(delayMs)
            if (!Prefs.isProtectionEnabled(this@AlwaysDataService)) return@launch
            val ok = MobileDataController.setMobileDataEnabled(this@AlwaysDataService, true)
            Prefs.log(
                this@AlwaysDataService,
                if (ok) "دیتا خاموش بود — بعد از ${delayMs/1000} ثانیه روشن شد (خودکار)"
                else "دیتا خاموش بود — تلاش خودکار بعد از قفل انجام شد"
            )
            // اگه باز هم خاموش بود یه بار دیگه
            delay(1500)
            if (!MobileDataController.isMobileDataEnabled(this@AlwaysDataService)
                && Prefs.isProtectionEnabled(this@AlwaysDataService)) {
                MobileDataController.setMobileDataEnabled(this@AlwaysDataService, true)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.service_channel_name) }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    private fun buildNotification(dataOn: Boolean?): Notification {
        val text = when (dataOn) {
            true -> getString(R.string.service_notification_text_on)
            false -> getString(R.string.service_notification_text_off)
            null -> getString(R.string.service_notification_text_on)
        }
        val openApp = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.service_notification_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(openApp)
            .setOngoing(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .build()
    }

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "AlwaysData::Poll").apply { acquire() }
        } catch (_: Exception) {}
    }

    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val request = NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR).build()
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) { scope.launch { checkOnce() } }
            }
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (_: Exception) {}
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        try {
            val restart = Intent(this, AlwaysDataService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(restart) else startService(restart)
        } catch (_: Exception) {}
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try { networkCallback?.let { getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it) } } catch (_: Exception) {}
        try { wakeLock?.takeIf { it.isHeld }?.release() } catch (_: Exception) {}
        loopJob?.cancel(); scope.cancel(); super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "always_data_channel"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_MS = 2000L
        fun start(context: Context) {
            val intent = Intent(context, AlwaysDataService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        }
        fun stop(context: Context) { context.stopService(Intent(context, AlwaysDataService::class.java)) }
    }
}
