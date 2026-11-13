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

object DirectMessagesReadSyncScheduler {
	private const val INPUT_CONVERSATION_ID = "conversation_id"
	private const val WORK_NAME_PREFIX = "direct_messages_read_sync:"
	private const val PERIODIC_WORK_NAME = "direct_messages_read_sync_periodic"

	fun ensurePeriodic(context: Context) {
		val work = PeriodicWorkRequestBuilder<DirectMessagesReadSyncWorker>(15, TimeUnit.MINUTES)
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

	fun enqueueNow(context: Context, conversationId: Long) {
		if (conversationId <= 0L) return
		val work = OneTimeWorkRequestBuilder<DirectMessagesReadSyncWorker>()
			.setInputData(workDataOf(INPUT_CONVERSATION_ID to conversationId))
			.setConstraints(defaultConstraints())
			.setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
			.build()
		WorkManager
			.getInstance(context)
			.enqueueUniqueWork(
				WORK_NAME_PREFIX + conversationId,
				ExistingWorkPolicy.REPLACE,
				work
			)
	}

	fun enqueueGlobalNow(context: Context) {
		val work = OneTimeWorkRequestBuilder<DirectMessagesReadSyncWorker>()
			.setConstraints(defaultConstraints())
			.setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
			.build()
		WorkManager
			.getInstance(context)
			.enqueueUniqueWork(
				WORK_NAME_PREFIX + "global",
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
