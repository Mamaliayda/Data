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
 * The heart of the app.
 *
 * Runs as a foreground service (ongoing notification, START_STICKY) and every
 * [POLL_MS] checks the mobile-data state. If protection is on and data is off,
 * it immediately tries to turn it back on — so even if the user (or the phone)
 * turns data off from Quick Settings or Settings, it comes back within ~2
 * seconds and the attempt is logged.
 *
 * Extra safety nets:
 *  - NetworkCallback fires an immediate check when the cellular network drops.
 *  - onTaskRemoved restarts the service (swiping the app away won't stop it).
 *  - A partial WakeLock keeps the check loop alive while the screen is off.
 *  - WorkManager (CheckWorker) restarts us if the system kills the process.
 *  - BootReceiver restarts us after a reboot.
 */
class AlwaysDataService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var loopJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastNotificationState: Boolean? = null

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
        // START_STICKY: the system restarts us if it kills the process.
        return START_STICKY
    }

    private suspend fun pollLoop() {
        while (true) {
            try {
                checkOnce()
            } catch (_: Exception) {
            }
            delay(POLL_MS)
        }
    }

    private fun checkOnce() {
        val now = System.currentTimeMillis()
        Prefs.setLastCheck(this, now)

        val protectionOn = Prefs.isProtectionEnabled(this)
        val dataOn = MobileDataController.isMobileDataEnabled(this)

        if (protectionOn && !dataOn) {
            val ok = MobileDataController.setMobileDataEnabled(this, enabled = true)
            Prefs.incrementAutoEnableCount(this)
            Prefs.log(
                this,
                if (ok) "دیتا خاموش بود — روشن شد (تلاش خودکار)"
                else "دیتا خاموش بود — تلاش برای روشن کردن انجام شد"
            )
            // Re-check shortly after; the radio needs a moment to come up.
            scope.launch {
                delay(1500)
                val stillOff = !MobileDataController.isMobileDataEnabled(this@AlwaysDataService)
                if (stillOff && Prefs.isProtectionEnabled(this@AlwaysDataService)) {
                    MobileDataController.setMobileDataEnabled(this@AlwaysDataService, true)
                }
            }
        }

        // Keep the ongoing notification honest about the current state.
        if (lastNotificationState != dataOn) {
            lastNotificationState = dataOn
            val nm = getSystemService(NotificationManager::class.java)
            nm.notify(NOTIFICATION_ID, buildNotification(dataOn))
        }

        StatusWidget.refresh(this)
    }

    // ------------------------------------------------------------- notification

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.service_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply { description = getString(R.string.service_channel_name) }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(dataOn: Boolean?): Notification {
        val text = when (dataOn) {
            true -> getString(R.string.service_notification_text_on)
            false -> getString(R.string.service_notification_text_off)
            null -> getString(R.string.service_notification_text_on)
        }
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
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

    // -------------------------------------------------------------- wake lock

    private fun acquireWakeLock() {
        try {
            val pm = getSystemService(PowerManager::class.java)
            wakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "AlwaysData::Poll"
            ).apply {
                setAndAllowWhileIdle(true)
                acquire()
            }
        } catch (_: Exception) {
        }
    }

    // -------------------------------------------------------- network callback

    private fun registerNetworkCallback() {
        try {
            val cm = getSystemService(ConnectivityManager::class.java)
            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_CELLULAR)
                .build()
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onLost(network: Network) {
                    // Cellular dropped — check right away instead of waiting
                    // for the next poll tick.
                    scope.launch { checkOnce() }
                }
            }
            cm.registerNetworkCallback(request, networkCallback!!)
        } catch (_: Exception) {
        }
    }

    // -------------------------------------------------------------- lifecycle

    override fun onTaskRemoved(rootIntent: Intent?) {
        // User swiped the app away — restart immediately.
        try {
            val restart = Intent(this, AlwaysDataService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(restart)
            } else {
                startService(restart)
            }
        } catch (_: Exception) {
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        try {
            networkCallback?.let {
                getSystemService(ConnectivityManager::class.java).unregisterNetworkCallback(it)
            }
        } catch (_: Exception) {
        }
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (_: Exception) {
        }
        loopJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "always_data_channel"
        private const val NOTIFICATION_ID = 1001
        private const val POLL_MS = 2000L

        fun start(context: Context) {
            val intent = Intent(context, AlwaysDataService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, AlwaysDataService::class.java))
        }
    }
}
