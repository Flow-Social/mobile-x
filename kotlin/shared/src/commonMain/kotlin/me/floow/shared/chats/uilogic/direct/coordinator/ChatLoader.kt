package me.floow.shared.chats.uilogic.direct.coordinator

import kotlinx.coroutines.withTimeout
import me.floow.domain.utils.Logger
import me.floow.shared.chats.model.ChatThreadSnapshot
import me.floow.shared.chats.uilogic.direct.ChatThreadPage
import me.floow.shared.chats.uilogic.direct.ChatThreadRepository
import me.floow.shared.chats.uilogic.direct.DirectChatInitialRequest
import me.floow.shared.chats.uilogic.session.SharedChatSessionCache

class ChatLoader(
	private val repository: ChatThreadRepository,
	private val sessionCache: SharedChatSessionCache? = null,
	private val logger: Logger? = null,
) {
	companion object {
		const val LOAD_TIMEOUT_MS = 15_000L
	}

	fun getInstantCached(request: DirectChatInitialRequest): ChatThreadSnapshot? {
		return sessionCache?.cachedThreadSnapshot(request)
	}

	suspend fun loadCachedInitial(request: DirectChatInitialRequest): ChatThreadSnapshot? {
		return repository.loadCachedInitial(request).getOrNull()
	}

	suspend fun loadInitialRemote(request: DirectChatInitialRequest): Result<ChatThreadSnapshot> {
		return runCatching {
			withTimeout(LOAD_TIMEOUT_MS) {
				repository.loadInitial(request).getOrThrow()
			}
		}.onFailure {
			logger?.d(
				tag = "ChatLoader",
				message = "loadInitialRemote failed conversationId=${request.conversationId} peer=${request.peerUserId} reason=${it.message}"
			)
		}
	}

	suspend fun loadMore(conversationId: Long, beforeMessageId: Long?): Result<ChatThreadPage> {
		return repository.loadMore(conversationId, beforeMessageId)
	}

	suspend fun loadAroundMessage(conversationId: Long, messageId: Long): Result<ChatThreadPage> {
		return repository.loadAroundMessage(conversationId, messageId)
	}
}
