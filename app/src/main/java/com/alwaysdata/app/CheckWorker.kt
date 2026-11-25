package com.alwaysdata.app

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Backup safety net: every 15 minutes (WorkManager minimum) make sure the
 * foreground service is alive and mobile data is on. If the system killed the
 * service process, this brings it back.
 */
class CheckWorker(context: Context, params: WorkerParameters) :
    CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        if (!Prefs.isProtectionEnabled(applicationContext)) return Result.success()
        if (!MobileDataController.isMobileDataEnabled(applicationContext)) {
            MobileDataController.setMobileDataEnabled(applicationContext, true)
            Prefs.incrementAutoEnableCount(applicationContext)
            Prefs.log(applicationContext, "دیتا خاموش بود — روشن شد (بررسی دوره‌ای)")
        }
        AlwaysDataService.start(applicationContext)
        return Result.success()
    }

    companion object {
        private const val UNIQUE_NAME = "always_data_check"

        fun enqueue(context: Context) {
            val request = PeriodicWorkRequestBuilder<CheckWorker>(15, TimeUnit.MINUTES)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
