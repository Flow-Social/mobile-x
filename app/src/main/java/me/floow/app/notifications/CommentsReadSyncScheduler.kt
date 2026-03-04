package me.floow.app.notifications

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object CommentsReadSyncScheduler {
	private const val WORK_NAME_PREFIX = "comments_read_sync:"
	private const val INPUT_POST_ID = "post_id"

	fun enqueueNow(context: Context, postId: String) {
		val normalizedPostId = normalizePostId(postId)
		val work = OneTimeWorkRequestBuilder<CommentsReadSyncWorker>()
			.setInputData(workDataOf(INPUT_POST_ID to normalizedPostId))
			.setConstraints(defaultConstraints())
			.setBackoffCriteria(BackoffPolicy.LINEAR, 10, TimeUnit.SECONDS)
			.build()
		WorkManager
			.getInstance(context)
			.enqueueUniqueWork(
				WORK_NAME_PREFIX + normalizedPostId,
				ExistingWorkPolicy.REPLACE,
				work
			)
	}

	private fun defaultConstraints(): Constraints {
		return Constraints.Builder()
			.setRequiredNetworkType(NetworkType.CONNECTED)
			.build()
	}

	private fun normalizePostId(postId: String): String {
		return postId.trim().ifEmpty { "unknown" }
	}
}
