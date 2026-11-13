package me.floow.domain.api

import me.floow.domain.api.models.ChatsGetConversationsResponse
import me.floow.domain.api.models.ChatsGetConversationResponse
import me.floow.domain.api.models.ChatsGetConversationInitialResponse
import me.floow.domain.api.models.ChatsGetMessagesResponse
import me.floow.domain.api.models.ChatsGetMessagesAroundResponse
import me.floow.domain.api.models.ChatsGetMessageByClientMessageIdResponse
import me.floow.domain.api.models.ChatsGetOrCreateDirectResponse
import me.floow.domain.api.models.ChatsDeleteConversationResponse
import me.floow.domain.api.models.ChatsDeleteMessageResponse
import me.floow.domain.api.models.ChatsGetPinnedMessagesResponse
import me.floow.domain.api.models.ChatsMarkReadUpToResponse
import me.floow.domain.api.models.ChatsPinMessageResponse
import me.floow.domain.api.models.ChatsSendMessageResponse
import me.floow.domain.api.models.ChatsTypingResponse
import me.floow.domain.api.models.ChatsUnpinMessageResponse
import me.floow.domain.api.models.ChatsUpdateMessageResponse

interface ChatsApi {
	suspend fun getOrCreateDirectConversation(peerUserId: String): ChatsGetOrCreateDirectResponse

	suspend fun getOrCreateSavedMessagesConversation(): ChatsGetOrCreateDirectResponse

	suspend fun getConversations(
		limit: Int,
		cursor: String?
	): ChatsGetConversationsResponse

	suspend fun getConversation(
		conversationId: Long
	): ChatsGetConversationResponse

	suspend fun deleteConversation(
		conversationId: Long,
		forEveryone: Boolean = false
	): ChatsDeleteConversationResponse

	suspend fun setConversationPinned(
		conversationId: Long,
		isPinned: Boolean
	): ChatsGetConversationResponse

	suspend fun getConversationInitial(
		conversationId: Long,
		messageLimit: Int,
		includePins: Boolean
	): ChatsGetConversationInitialResponse = ChatsGetConversationInitialResponse.Error

	suspend fun getMessages(
		conversationId: Long,
		limit: Int,
		beforeId: Long?
	): ChatsGetMessagesResponse

	suspend fun getMessagesAround(
		conversationId: Long,
		anchorId: Long,
		olderLimit: Int,
		newerLimit: Int
	): ChatsGetMessagesAroundResponse

	suspend fun sendMessage(
		conversationId: Long,
		text: String,
		clientMessageId: String?,
		replyToMessageId: Long?
	): ChatsSendMessageResponse

	suspend fun getMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String
	): ChatsGetMessageByClientMessageIdResponse

	suspend fun sendVideoCircleMessage(
		conversationId: Long,
		clientMessageId: String?,
		mediaUrl: String,
		mediaObjectKey: String,
		mediaSizeBytes: Long,
		mediaDurationMs: Long,
		mediaWidth: Int,
		mediaHeight: Int,
		thumbnailUrl: String?,
		replyToMessageId: Long?
	): ChatsSendMessageResponse

	suspend fun sendVoiceMessage(
		conversationId: Long,
		clientMessageId: String?,
		mediaUrl: String,
		mediaObjectKey: String,
		mediaSizeBytes: Long,
		mediaDurationMs: Long,
		waveform: List<Int>,
		replyToMessageId: Long?
	): ChatsSendMessageResponse

	suspend fun sendImageMessage(
		conversationId: Long,
		clientMessageId: String?,
		mediaUrl: String,
		mediaObjectKey: String,
		mediaSizeBytes: Long,
		mediaWidth: Int,
		mediaHeight: Int,
		caption: String?,
		groupId: String?,
		position: Int,
		replyToMessageId: Long?,
		thumbnailUrl: String? = null
	): ChatsSendMessageResponse

	suspend fun sendAlbumMessage(
		conversationId: Long,
		items: List<me.floow.domain.api.models.ChatAlbumItemPayload>,
		caption: String?,
		replyToMessageId: Long?,
		idempotencyKey: String,
	): me.floow.domain.api.models.ChatsSendAlbumMessageResponse

	suspend fun deleteMessage(
		conversationId: Long,
		messageId: Long
	): ChatsDeleteMessageResponse

	suspend fun deleteMessagesBatch(
		conversationId: Long,
		messageIds: List<Long>
	): me.floow.domain.api.models.ChatsDeleteMessagesBatchResponse

	suspend fun updateMessage(
		conversationId: Long,
		messageId: Long,
		text: String
	): ChatsUpdateMessageResponse

	suspend fun getPinnedMessages(
		conversationId: Long,
		limit: Int
	): ChatsGetPinnedMessagesResponse

	suspend fun pinMessage(
		conversationId: Long,
		messageId: Long
	): ChatsPinMessageResponse

	suspend fun unpinMessage(
		conversationId: Long,
		messageId: Long
	): ChatsUnpinMessageResponse

	suspend fun markReadUpTo(
		conversationId: Long,
		messageId: Long
	): ChatsMarkReadUpToResponse

	suspend fun sendTyping(
		conversationId: Long,
		isTyping: Boolean,
		action: String = "typing"
	): ChatsTypingResponse
}
