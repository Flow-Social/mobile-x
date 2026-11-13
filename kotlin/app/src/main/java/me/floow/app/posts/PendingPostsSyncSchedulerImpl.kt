package me.floow.app.posts

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import me.floow.domain.data.repos.PendingPostsSyncScheduler
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

private const val IMMEDIATE_WORK_NAME = "pending_posts_sync_immediate"
private const val PERIODIC_WORK_NAME = "pending_posts_sync_periodic"

class PendingPostsSyncSchedulerImpl(
	private val context: Context,
) : PendingPostsSyncScheduler {
	override fun enqueueNow() {
		val work = OneTimeWorkRequestBuilder<PendingPostsSyncWorker>()
			.setConstraints(defaultConstraints())
			.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
			.build()
		WorkManager
			.getInstance(context)
			.enqueueUniqueWork(IMMEDIATE_WORK_NAME, ExistingWorkPolicy.REPLACE, work)

		kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
			val koin = org.koin.core.context.GlobalContext.getKoinApplicationOrNull()?.koin ?: return@launch
			PendingPostsSyncRunner.runSync(koin)
		}
	}

	override fun ensurePeriodic() {
		val work = PeriodicWorkRequestBuilder<PendingPostsSyncWorker>(15, TimeUnit.MINUTES)
			.setConstraints(defaultConstraints())
			.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
			.build()
		WorkManager
			.getInstance(context)
			.enqueueUniquePeriodicWork(PERIODIC_WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, work)
	}

	private fun defaultConstraints(): Constraints {
		return Constraints.Builder()
			.setRequiredNetworkType(NetworkType.CONNECTED)
			.build()
	}
}
