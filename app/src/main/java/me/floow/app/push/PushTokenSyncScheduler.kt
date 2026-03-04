package me.floow.app.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object PushTokenSyncScheduler {
    private const val IMMEDIATE_WORK_NAME = "push_token_sync_immediate"
    private const val PERIODIC_WORK_NAME = "push_token_sync_periodic"

    fun ensurePeriodic(context: Context) {
        val work = PeriodicWorkRequestBuilder<PushTokenSyncWorker>(6, TimeUnit.HOURS)
            .setConstraints(defaultConstraints())
            .build()
        WorkManager
            .getInstance(context)
            .enqueueUniquePeriodicWork(
                PERIODIC_WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                work
            )
    }

    fun enqueueNow(context: Context) {
        val work = OneTimeWorkRequestBuilder<PushTokenSyncWorker>()
            .setConstraints(defaultConstraints())
            .build()
        WorkManager
            .getInstance(context)
            .enqueueUniqueWork(
                IMMEDIATE_WORK_NAME,
                ExistingWorkPolicy.REPLACE,
                work
            )
    }

    private fun defaultConstraints(): Constraints {
        return Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
    }
}
