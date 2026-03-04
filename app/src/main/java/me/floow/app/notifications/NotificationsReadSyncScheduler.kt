package me.floow.app.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object NotificationsReadSyncScheduler {
	private const val IMMEDIATE_WORK_NAME_PREFIX = "notifications_read_sync_immediate:"
	private const val PERIODIC_WORK_NAME_PREFIX = "notifications_read_sync_periodic:"
	private const val INPUT_CHANNEL = "channel"

	fun ensurePeriodic(context: Context, channel: String) {
		val work = PeriodicWorkRequestBuilder<NotificationsReadSyncWorker>(15, TimeUnit.MINUTES)
			.setInputData(workDataOf(INPUT_CHANNEL to channel))
			.setConstraints(defaultConstraints())
			.build()
		WorkManager
			.getInstance(context)
			.enqueueUniquePeriodicWork(
				PERIODIC_WORK_NAME_PREFIX + channel,
				ExistingPeriodicWorkPolicy.UPDATE,
				work
			)
	}

	fun enqueueNow(context: Context, channel: String) {
		val work = OneTimeWorkRequestBuilder<NotificationsReadSyncWorker>()
			.setInputData(workDataOf(INPUT_CHANNEL to channel))
			.setConstraints(defaultConstraints())
			.setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
			.build()
		WorkManager
			.getInstance(context)
			.enqueueUniqueWork(
				IMMEDIATE_WORK_NAME_PREFIX + channel,
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
