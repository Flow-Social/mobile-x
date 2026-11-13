package me.floow.app.push

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.floow.domain.api.PushApi
import me.floow.domain.api.models.PushAckRequest
import me.floow.domain.api.models.PushAckResponse
import me.floow.domain.utils.Logger
import org.koin.core.context.GlobalContext

class PushAckWorker(
	appContext: Context,
	workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

	override suspend fun doWork(): Result {
		val input = PushAckScheduler.extractInput(inputData) ?: return Result.success()
		val koin = GlobalContext.getKoinApplicationOrNull()?.koin ?: return Result.retry()
		val logger = koin.get<Logger>()
		val pushApi = koin.get<PushApi>()

		val token = input.deviceToken ?: PushTokenStorage.get(applicationContext)
		if (token.isNullOrBlank()) {
			logger.d(TAG, "ack_failed_missing_token notification_id=${input.notificationId}")
			return Result.retry()
		}

		val request = PushAckRequest(
			notificationId = input.notificationId,
			conversationId = input.conversationId,
			messageId = input.messageId,
			receivedAtMs = System.currentTimeMillis(),
			deviceId = token
		)

		return when (pushApi.ackPush(request)) {
			is PushAckResponse.Success -> {
				logger.d(TAG, "ack_sent notification_id=${input.notificationId}")
				Result.success()
			}
			is PushAckResponse.Error -> {
				logger.d(TAG, "ack_failed notification_id=${input.notificationId}")
				Result.retry()
			}
		}
	}

	private companion object {
		const val TAG = "PushAckWorker"
	}
}
