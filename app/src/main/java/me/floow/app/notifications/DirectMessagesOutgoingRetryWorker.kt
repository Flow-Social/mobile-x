package me.floow.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.utils.Logger
import org.koin.core.context.GlobalContext

private const val INPUT_CONVERSATION_ID = "conversation_id"

class DirectMessagesOutgoingRetryWorker(
	appContext: Context,
	workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

	override suspend fun doWork(): Result {
		val koin = GlobalContext.getKoinApplicationOrNull()?.koin ?: return Result.retry()
		val logger = koin.get<Logger>()
		val authManager = koin.get<AuthenticationManager>()
		if (!authManager.isSignedIn()) {
			return Result.success()
		}

		val chatsRepository = koin.get<ChatsRepository>()
		val conversationId = inputData.getLong(INPUT_CONVERSATION_ID, 0L)
		if (conversationId <= 0L) return Result.success()

		return try {
			chatsRepository.retryPendingOutgoingMessages(conversationId = conversationId, limit = 80)
			Result.success()
		} catch (t: Throwable) {
			logger.d("DirectMessagesOutgoingRetryWorker.doWork", "Retry failed: ${t.message}")
			Result.retry()
		}
	}
}
