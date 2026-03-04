package me.floow.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.NotificationsReadCursorStore
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.NotificationsRepository
import me.floow.domain.utils.Logger
import org.koin.core.context.GlobalContext

private const val INPUT_CHANNEL = "channel"

class NotificationsReadSyncWorker(
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

		val channel = inputData.getString(INPUT_CHANNEL)
			?.trim()
			?.lowercase()
			?.takeIf(String::isNotEmpty)
			?: "replies"

		val cursorStore = koin.get<NotificationsReadCursorStore>()
		val pendingReadUpTo = cursorStore.getPendingReadUpTo(channel)
		if (pendingReadUpTo <= 0L) {
			return Result.success()
		}

		val notificationsRepository = koin.get<NotificationsRepository>()
		val notificationsRealtimeRepository = koin.get<NotificationsRealtimeRepository>()
		return when (val result = notificationsRepository.markNotificationsReadUpTo(pendingReadUpTo, channel)) {
			is GetDataResponse.Success -> {
				val appliedSeq = result.data.coerceAtLeast(0L)
				cursorStore.setLocalLastReadSeq(channel, appliedSeq)
				cursorStore.markPendingReadUpToApplied(channel, appliedSeq)
				notificationsRealtimeRepository.applyLocalReadState(appliedSeq)
				Result.success()
			}
			is GetDataResponse.Error -> {
				logger.d(
					"NotificationsReadSyncWorker.doWork",
					"markNotificationsReadUpTo failed channel=$channel pending=$pendingReadUpTo"
				)
				Result.retry()
			}
		}
	}
}
