package me.floow.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.data.repos.DirectMessagesReadCursorStore
import me.floow.domain.utils.Logger
import org.koin.core.context.GlobalContext

private const val INPUT_CONVERSATION_ID = "conversation_id"

class DirectMessagesReadSyncWorker(
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

		val cursorStore = koin.get<DirectMessagesReadCursorStore>()
		val chatsRepository = koin.get<ChatsRepository>()

		val conversationId = inputData.getLong(INPUT_CONVERSATION_ID, 0L)
		val conversationIds = if (conversationId > 0L) {
			listOf(conversationId)
		} else {
			cursorStore.getPendingConversationIds(limit = 300)
		}

		if (conversationIds.isEmpty()) {
			return Result.success()
		}

		var hasFailure = false
		conversationIds.forEach { pendingConversationId ->
			val pendingReadUpTo = cursorStore.getPendingReadUpTo(pendingConversationId)
			if (pendingReadUpTo <= 0L) return@forEach
			val pendingEnqueuedAtMs = cursorStore.getPendingEnqueuedAtMillis(pendingConversationId)

			when (val result = chatsRepository.markReadUpTo(pendingConversationId, pendingReadUpTo)) {
				is GetDataResponse.Success -> {
					val appliedMessageId = result.data.lastReadMessageId.coerceAtLeast(0L)
					cursorStore.setLocalLastReadMessageId(pendingConversationId, appliedMessageId)
					cursorStore.markPendingReadUpToApplied(pendingConversationId, appliedMessageId)
					cursorStore.resetPendingRetryCount(pendingConversationId)
					if (pendingEnqueuedAtMs > 0L) {
						val lagMs = (System.currentTimeMillis() - pendingEnqueuedAtMs).coerceAtLeast(0L)
						logger.d(DM_METRIC_READ_SYNC_LAG_MS, lagMs.toString())
					}
				}
				is GetDataResponse.Error -> {
					hasFailure = true
					val retryCount = cursorStore.incrementPendingRetryCount(pendingConversationId)
					logger.d(DM_METRIC_PENDING_FLUSH_RETRY_COUNT, retryCount.toString())
					logger.d(
						"DirectMessagesReadSyncWorker.doWork",
						"markReadUpTo failed conversationId=$pendingConversationId pending=$pendingReadUpTo"
					)
				}
			}
		}

		return if (hasFailure) Result.retry() else Result.success()
	}

	private companion object {
		const val DM_METRIC_READ_SYNC_LAG_MS = "DirectMessagesMetrics.read_sync_lag_ms"
		const val DM_METRIC_PENDING_FLUSH_RETRY_COUNT = "DirectMessagesMetrics.pending_flush_retry_count"
	}
}
