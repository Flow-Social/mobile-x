package me.floow.domain.cache

import kotlinx.coroutines.flow.Flow
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatAnchoredMessagesWindow
import me.floow.domain.models.DirectChatOutboxCommand
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatMessagesPage
import me.floow.domain.models.DirectChatReadState

interface DirectChatsLocalStore {
	fun observeConversations(): Flow<List<DirectChatConversation>>

	fun observeLocalReadStates(): Flow<Map<Long, DirectChatReadState>>

	suspend fun getConversations(
		limit: Int,
		offset: Int
	): List<DirectChatConversation>

	suspend fun upsertConversations(items: List<DirectChatConversation>)

	suspend fun upsertConversation(item: DirectChatConversation)

	suspend fun getConversationById(conversationId: Long): DirectChatConversation?

	suspend fun setConversationPinned(
		conversationId: Long,
		isPinned: Boolean,
		pinnedAt: Long?
	): DirectChatConversation?

	fun observeMessages(
		conversationId: Long,
		limit: Int
	): Flow<DirectChatMessagesPage>

	suspend fun getMessages(
		conversationId: Long,
		limit: Int,
		beforeId: Long?
	): DirectChatMessagesPage

	suspend fun getAnchoredMessagesWindow(
		conversationId: Long,
		anchorMessageId: Long,
		olderLimit: Int,
		newerLimit: Int
	): DirectChatAnchoredMessagesWindow?

	suspend fun upsertMessages(
		conversationId: Long,
		items: List<DirectChatMessage>
	)

	suspend fun replaceLatestMessagesWindow(
		conversationId: Long,
		limit: Int,
		items: List<DirectChatMessage>
	)

	suspend fun upsertMessage(message: DirectChatMessage)

	suspend fun enqueueOutgoingText(message: DirectChatMessage): DirectChatOutboxCommand?

	suspend fun enqueueOutgoingMedia(
		message: DirectChatMessage,
		localFilePath: String,
		mediaMimeType: String,
		mediaSizeBytes: Long,
		mediaDurationMs: Long? = null,
		mediaWidth: Int? = null,
		mediaHeight: Int? = null,
		mediaWaveform: String? = null,
		groupId: String? = null,
		mediaPosition: Int = 0
	): DirectChatOutboxCommand?

	suspend fun getActiveMediaCommands(): List<DirectChatOutboxCommand>

	fun observeActiveMediaCommands(): Flow<List<DirectChatOutboxCommand>>

	suspend fun updateOutboxMediaStage(
		clientMessageId: String,
		uploadStage: me.floow.domain.models.DirectChatUploadStage,
		mediaUrl: String? = null,
		mediaThumbnailUrl: String? = null,
		error: String? = null
	)

	fun observePendingOutboxMessages(conversationId: Long): Flow<List<DirectChatMessage>>

	suspend fun getOutgoingCommand(clientMessageId: String): DirectChatOutboxCommand?

	suspend fun getDueOutgoingCommands(
		now: Long,
		limit: Int
	): List<DirectChatOutboxCommand>

	suspend fun markOutgoingSending(
		clientMessageId: String,
		now: Long
	): DirectChatOutboxCommand?

	suspend fun markOutgoingFailed(
		clientMessageId: String,
		error: String?,
		nextAttemptAt: Long,
		now: Long
	): DirectChatOutboxCommand?

	suspend fun markOutgoingSent(
		clientMessageId: String,
		serverMessage: DirectChatMessage,
		now: Long
	): DirectChatMessage?

	suspend fun cancelOutgoing(
		conversationId: Long,
		clientMessageId: String
	)

	suspend fun getPinnedMessages(
		conversationId: Long,
		limit: Int
	): List<DirectChatMessage>

	fun observePinnedMessages(
		conversationId: Long,
		limit: Int
	): Flow<List<DirectChatMessage>>

	suspend fun getMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String
	): DirectChatMessage?

	suspend fun getPendingOutgoingMessages(limit: Int): List<DirectChatMessage>

	suspend fun setMessagePinned(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean,
		pinnedAt: Long?,
		pinnedByUserId: String?
	): DirectChatMessage?

	suspend fun deleteMessage(
		conversationId: Long,
		messageId: Long
	)

	suspend fun deleteMessages(
		conversationId: Long,
		messageIds: List<Long>
	)

	suspend fun deleteMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String
	)

	suspend fun applyReadState(state: DirectChatReadState)

	suspend fun applyLocalReadUpTo(
		conversationId: Long,
		messageId: Long
	): DirectChatReadState?

	suspend fun applyPeerLastReadUpTo(
		conversationId: Long,
		messageId: Long
	)

	suspend fun updateMessageDeliveryStatus(
		conversationId: Long,
		messageId: Long,
		deliveryStatus: me.floow.domain.models.MessageDeliveryStatus
	)

	suspend fun updateMessageDeliveryStatusByClientMessageId(
		conversationId: Long,
		clientMessageId: String,
		deliveryStatus: me.floow.domain.models.MessageDeliveryStatus
	)

	suspend fun deleteConversation(conversationId: Long)

	suspend fun clear()
}
