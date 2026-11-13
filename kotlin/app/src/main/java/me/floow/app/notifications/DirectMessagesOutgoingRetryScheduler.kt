package me.floow.app.notifications

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Constraints
import androidx.work.NetworkType
import me.floow.domain.data.repos.DirectMessagesOutgoingRetryScheduler
import java.util.concurrent.TimeUnit

private const val OUTGOING_RETRY_WORK_PREFIX = "direct_messages_outgoing_retry"

class DirectMessagesOutgoingRetrySchedulerImpl(
	private val context: Context
) : DirectMessagesOutgoingRetryScheduler {
	override fun enqueue(conversationId: Long) {
		val constraints = Constraints.Builder()
			.setRequiredNetworkType(NetworkType.CONNECTED)
			.build()
		val work = OneTimeWorkRequestBuilder<DirectMessagesOutgoingRetryWorker>()
			.setConstraints(constraints)
			.setInitialDelay(2, TimeUnit.SECONDS)
			.build()
		WorkManager.getInstance(context).enqueueUniqueWork(
			OUTGOING_RETRY_WORK_PREFIX,
			ExistingWorkPolicy.REPLACE,
			work
		)
	}
}
