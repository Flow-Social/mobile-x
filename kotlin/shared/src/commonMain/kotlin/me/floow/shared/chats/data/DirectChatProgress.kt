package me.floow.shared.chats.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.floow.database.dao.DirectChatsDao
import me.floow.database.dbo.DirectChatProgressEntity
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.data.repos.DirectChatViewportSnapshot
import me.floow.domain.data.repos.DirectChatWindowBounds
import me.floow.domain.network.NetworkMonitor

/** The viewport lives on this device; pending receipts survive process death. */
internal class DirectChatProgress(
    private val dao: DirectChatsDao,
    private val repository: ChatsRepository,
    private val auth: AuthenticationManager,
    network: NetworkMonitor,
    scope: CoroutineScope,
) {
    init {
        scope.launch {
            combine(auth.authenticationStateFlow, network.isOnline) { _, online ->
                if (online && auth.isSignedIn()) auth.getSelfUserIdOrNull() else null
            }.distinctUntilChanged().collectLatest { accountId ->
                if (accountId == null) return@collectLatest
                var retryDelay = 1_000L
                while (true) {
                    val pending = dao.observePendingReads(accountId).first { it.isNotEmpty() }
                    var shouldRetry = false
                    for (row in pending) {
                        if (auth.getSelfUserIdOrNull() != accountId) return@collectLatest
                        when (val result = repository.markReadUpTo(row.conversationId, row.pendingRead)) {
                            is GetDataResponse.Success -> dao.acknowledgeRead(
                                accountId, row.conversationId, maxOf(row.pendingRead, result.data.lastReadMessageId),
                            )
                            is GetDataResponse.Error -> {
                                if (result.error == GetDataError.NoData) {
                                    dao.acknowledgeRead(accountId, row.conversationId, row.pendingRead)
                                } else {
                                    shouldRetry = true
                                }
                            }
                        }
                    }
                    if (shouldRetry) {
                        delay(retryDelay)
                        retryDelay = (retryDelay * 2).coerceAtMost(30_000)
                    } else {
                        retryDelay = 1_000
                    }
                }
            }
        }
    }

    suspend fun viewport(conversationId: Long): DirectChatViewportSnapshot? {
        val accountId = auth.getSelfUserIdOrNull() ?: return null
        return dao.getProgress(accountId, conversationId)?.let {
            val history = if (it.oldestCachedId != null && it.newestCachedId != null) {
                DirectChatWindowBounds(it.oldestCachedId, it.newestCachedId, it.cacheHasOlder, it.cacheHasNewer)
            } else null
            DirectChatViewportSnapshot(it.anchorMessageId, it.anchorOffsetPx, it.bottomPinned, history)
        }
    }

    suspend fun saveViewport(accountId: String, conversationId: Long, snapshot: DirectChatViewportSnapshot) {
        dao.insertProgress(DirectChatProgressEntity(accountId, conversationId, bottomPinned = false))
        dao.saveViewport(
            accountId, conversationId, snapshot.anchorMessageId, snapshot.anchorOffsetPx, snapshot.isBottomPinned,
            snapshot.history?.oldestId, snapshot.history?.newestId, snapshot.history?.hasOlder == true, snapshot.history?.hasNewer == true,
        )
    }

    suspend fun enqueue(accountId: String, conversationId: Long, messageId: Long) {
        dao.insertProgress(DirectChatProgressEntity(accountId, conversationId, bottomPinned = false))
        dao.enqueueRead(accountId, conversationId, messageId)
    }

    suspend fun pendingRead(conversationId: Long): Long {
        val accountId = auth.getSelfUserIdOrNull() ?: return 0L
        return dao.getProgress(accountId, conversationId)?.pendingRead ?: 0L
    }
}
