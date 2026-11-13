package me.floow.app.push

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

object PushAckScheduler {
	private const val WORK_NAME_PREFIX = "push_ack_"
	private const val KEY_NOTIFICATION_ID = "notification_id"
	private const val KEY_CONVERSATION_ID = "conversation_id"
	private const val KEY_MESSAGE_ID = "message_id"
	private const val KEY_DEVICE_TOKEN = "device_token"

	fun enqueue(
		context: Context,
		notificationId: String,
		conversationId: Long,
		messageId: Long,
		deviceToken: String?
	) {
		if (notificationId.isBlank() || conversationId <= 0L || messageId <= 0L) return
		val input = workDataOf(
			KEY_NOTIFICATION_ID to notificationId,
			KEY_CONVERSATION_ID to conversationId,
			KEY_MESSAGE_ID to messageId,
			KEY_DEVICE_TOKEN to deviceToken?.trim().orEmpty()
		)
		val work = OneTimeWorkRequestBuilder<PushAckWorker>()
			.setInputData(input)
			.setConstraints(defaultConstraints())
			.setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
			.setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 10, TimeUnit.SECONDS)
			.build()
		WorkManager.getInstance(context)
			.enqueueUniqueWork(
				WORK_NAME_PREFIX + notificationId,
				ExistingWorkPolicy.KEEP,
				work
			)
	}

	internal fun extractInput(data: androidx.work.Data): PushAckInput? {
		val notificationId = data.getString(KEY_NOTIFICATION_ID).orEmpty().trim()
		val conversationId = data.getLong(KEY_CONVERSATION_ID, 0L)
		val messageId = data.getLong(KEY_MESSAGE_ID, 0L)
		val deviceToken = data.getString(KEY_DEVICE_TOKEN).orEmpty().trim()
		if (notificationId.isBlank() || conversationId <= 0L || messageId <= 0L) return null
		return PushAckInput(
			notificationId = notificationId,
			conversationId = conversationId,
			messageId = messageId,
			deviceToken = deviceToken.ifBlank { null }
		)
	}

	internal data class PushAckInput(
		val notificationId: String,
		val conversationId: Long,
		val messageId: Long,
		val deviceToken: String?
	)

	private fun defaultConstraints(): Constraints {
		return Constraints.Builder()
			.setRequiredNetworkType(NetworkType.CONNECTED)
			.build()
	}
}
