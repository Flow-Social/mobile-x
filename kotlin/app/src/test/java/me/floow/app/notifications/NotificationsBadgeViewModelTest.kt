package me.floow.app.notifications

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.StateFlow
import me.floow.domain.data.repos.ChatsRepository
import me.floow.domain.data.repos.NotificationsRealtimeRepository
import me.floow.domain.data.repos.NotificationsReadCursorStore
import me.floow.domain.data.repos.RepliesRealtimeState
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.models.DirectChatAnchoredMessagesWindow
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatConversationsPage
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatMessagesPage
import me.floow.domain.models.DirectChatPeer
import me.floow.domain.models.DirectChatReadState
import me.floow.domain.models.UserNotificationsPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class NotificationsBadgeViewModelTest {
	private val testDispatcher = StandardTestDispatcher()

	@Before
	fun setUp() {
		Dispatchers.setMain(testDispatcher)
	}

	@After
	fun tearDown() {
		Dispatchers.resetMain()
	}

	@Test
	fun `badge prefers local direct unread state over stale conversation unread`() {
		val chatsRepository = BadgeTestChatsRepository()
		val viewModel = NotificationsBadgeViewModel(
			notificationsRealtimeRepository = BadgeTestNotificationsRealtimeRepository(),
			notificationsReadCursorStore = BadgeTestNotificationsReadCursorStore(),
			chatsRepository = chatsRepository,
		)
		chatsRepository.conversations.value = listOf(
			DirectChatConversation(
				id = 7L,
				kind = "direct",
				peer = DirectChatPeer(
					id = "peer-7",
					username = "peer-7",
					name = "Peer 7",
					avatarUrl = null,
				),
				lastMessage = null,
				unreadCount = 5,
				lastReadMessageId = 0L,
				peerLastReadMessageId = null,
				createdAt = 0L,
				updatedAt = 10L,
			)
		)
		chatsRepository.localReadStates.value = mapOf(
			7L to DirectChatReadState(
				conversationId = 7L,
				lastReadMessageId = 99L,
				unreadCount = 0,
				firstUnreadId = null,
				maxMessageId = 99L,
			)
		)

		assertEquals(0, viewModel.state.value.unreadCount)
	}
}

private class BadgeTestNotificationsRealtimeRepository : NotificationsRealtimeRepository {
	override val repliesState: StateFlow<RepliesRealtimeState> = MutableStateFlow(
		RepliesRealtimeState(
			isBootstrapping = false,
			isConnected = true,
			hasError = false,
			page = UserNotificationsPage(items = emptyList(), nextCursor = null),
		)
	)

	override fun start() = Unit

	override fun stop(resetState: Boolean) = Unit

	override suspend fun refresh() = Unit

	override suspend fun applyLocalReadState(lastReadSeq: Long) = Unit
}

private class BadgeTestNotificationsReadCursorStore : NotificationsReadCursorStore {
	override suspend fun getLocalLastReadSeq(channel: String): Long = 0L

	override suspend fun setLocalLastReadSeq(channel: String, readSeq: Long) = Unit

	override suspend fun getOpenAnchorSeq(channel: String): Long = 0L

	override suspend fun setOpenAnchorSeq(channel: String, anchorSeq: Long) = Unit

	override suspend fun enqueueReadUpTo(channel: String, readUpToSeq: Long) = Unit

	override suspend fun getPendingReadUpTo(channel: String): Long = 0L

	override suspend fun markPendingReadUpToApplied(channel: String, appliedReadSeq: Long) = Unit
}

private class BadgeTestChatsRepository : ChatsRepository {
	val conversations = MutableStateFlow<List<DirectChatConversation>>(emptyList())
	val localReadStates = MutableStateFlow<Map<Long, DirectChatReadState>>(emptyMap())

	override fun observeConversations() = conversations

	override fun observeLocalReadStates() = localReadStates

	override fun observeMessages(conversationId: Long, limit: Int) = MutableStateFlow(
		DirectChatMessagesPage(
			items = emptyList(),
			nextBeforeId = null,
			peerLastReadMessageId = null,
		)
	)

	override fun observePinnedMessages(conversationId: Long, limit: Int) = MutableStateFlow<List<DirectChatMessage>>(emptyList())

	override suspend fun getOrCreateDirectConversation(peerUserId: String) = GetDataResponse.Error<DirectChatConversation>(GetDataError.Other)

	override suspend fun getOrCreateSavedMessagesConversation() = GetDataResponse.Error<DirectChatConversation>(GetDataError.Other)

	override suspend fun getConversations(limit: Int, cursor: String?) = GetDataResponse.Error<DirectChatConversationsPage>(GetDataError.Other)

	override suspend fun getConversation(conversationId: Long) = GetDataResponse.Error<DirectChatConversation>(GetDataError.Other)

	override suspend fun setConversationPinned(conversationId: Long, isPinned: Boolean) =
		GetDataResponse.Error<DirectChatConversation>(GetDataError.Other)

	override suspend fun getMessages(conversationId: Long, limit: Int, beforeId: Long?) =
		GetDataResponse.Error<DirectChatMessagesPage>(GetDataError.Other)

	override suspend fun getMessagesAround(conversationId: Long, anchorId: Long, olderLimit: Int, newerLimit: Int) =
		GetDataResponse.Error<DirectChatAnchoredMessagesWindow>(GetDataError.Other)

	override suspend fun getAnchoredMessagesWindow(conversationId: Long, anchorMessageId: Long, olderLimit: Int, newerLimit: Int) =
		GetDataResponse.Error<DirectChatAnchoredMessagesWindow>(GetDataError.Other)

	override suspend fun refreshLatestMessagesWindow(conversationId: Long, limit: Int) =
		GetDataResponse.Error<DirectChatMessagesPage>(GetDataError.Other)

	override suspend fun sendMessage(conversationId: Long, text: String, clientMessageId: String?, replyToMessageId: Long?) =
		GetDataResponse.Error<DirectChatMessage>(GetDataError.Other)

	override suspend fun retryPendingOutgoingMessages(conversationId: Long, limit: Int) = Unit

	override suspend fun flushPendingOutgoingMessages(limit: Int) = Unit

	override suspend fun retryOutgoingMessage(conversationId: Long, clientMessageId: String) = Unit

	override suspend fun deleteMessage(conversationId: Long, messageId: Long) = UpdateDataResponse.Success()
	override suspend fun deleteMessagesBatch(conversationId: Long, messageIds: List<Long>) = UpdateDataResponse.Success()

	override suspend fun updateMessage(conversationId: Long, messageId: Long, text: String) =
		GetDataResponse.Error<DirectChatMessage>(GetDataError.Other)

	override suspend fun getPinnedMessages(conversationId: Long, limit: Int) =
		GetDataResponse.Error<List<DirectChatMessage>>(GetDataError.Other)

	override suspend fun setMessagePinned(conversationId: Long, messageId: Long, isPinned: Boolean) =
		GetDataResponse.Error<DirectChatMessage>(GetDataError.Other)

	override suspend fun storeOutgoingOptimisticMessage(message: DirectChatMessage) = Unit

	override suspend fun deleteLocalMessage(conversationId: Long, messageId: Long) = Unit

	override suspend fun deleteLocalMessageByClientMessageId(conversationId: Long, clientMessageId: String) = Unit

	override suspend fun applyLocalPinnedState(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean,
		pinnedAt: Long?,
		pinnedByUserId: String?,
	): DirectChatMessage? = null

	override suspend fun updateLocalMessageDeliveryStatus(
		conversationId: Long,
		messageId: Long,
		status: me.floow.domain.models.MessageDeliveryStatus,
	) = Unit

	override suspend fun updateLocalMessageDeliveryStatusByClientMessageId(
		conversationId: Long,
		clientMessageId: String,
		status: me.floow.domain.models.MessageDeliveryStatus,
	) = Unit

	override suspend fun markReadUpTo(conversationId: Long, messageId: Long) =
		GetDataResponse.Error<DirectChatReadState>(GetDataError.Other)

	override suspend fun applyLocalReadUpTo(conversationId: Long, messageId: Long): DirectChatReadState? = null

	override suspend fun applyPeerLastReadUpTo(conversationId: Long, messageId: Long) = Unit

	override suspend fun sendTyping(conversationId: Long, isTyping: Boolean, action: String) = UpdateDataResponse.Success()

	override suspend fun deleteConversation(conversationId: Long, forEveryone: Boolean): UpdateDataResponse = UpdateDataResponse.Success()

	override fun subscribeConversation(conversationId: Long, afterSeq: Long, replayLimit: Int) =
		emptyFlow<me.floow.domain.models.DirectChatRealtimeEvent>()

	override fun subscribeAllConversations(afterSeq: Long, replayLimit: Int) =
		emptyFlow<me.floow.domain.models.DirectChatRealtimeEvent>()
}
