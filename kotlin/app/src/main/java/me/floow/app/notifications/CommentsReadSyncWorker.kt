package me.floow.app.notifications

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.CommentsReadCursorStore
import me.floow.domain.data.repos.CommentsRepository
import me.floow.domain.utils.Logger
import org.koin.core.context.GlobalContext

private const val INPUT_POST_ID = "post_id"

class CommentsReadSyncWorker(
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

		val postId = inputData.getString(INPUT_POST_ID)
			?.trim()
			?.takeIf(String::isNotEmpty)
			?: return Result.success()

		val cursorStore = koin.get<CommentsReadCursorStore>()
		val pendingReadUpTo = cursorStore.getPendingReadUpTo(postId)
		if (pendingReadUpTo <= 0L) {
			return Result.success()
		}

		val commentsRepository = koin.get<CommentsRepository>()
		return when (val result = commentsRepository.markCommentsReadUpTo(postId = postId, readUpToSeq = pendingReadUpTo)) {
			is GetDataResponse.Success -> {
				val appliedSeq = result.data.coerceAtLeast(0L)
				cursorStore.setLocalLastReadSeq(postId, appliedSeq)
				cursorStore.markPendingReadUpToApplied(postId, appliedSeq)
				Result.success()
			}
			is GetDataResponse.Error -> {
				logger.d(
					"CommentsReadSyncWorker.doWork",
					"markCommentsReadUpTo failed postId=$postId pending=$pendingReadUpTo"
				)
				Result.retry()
			}
		}
	}
}
