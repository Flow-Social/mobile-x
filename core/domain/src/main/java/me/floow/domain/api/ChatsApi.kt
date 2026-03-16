package me.floow.domain.api

import me.floow.domain.api.models.ChatsGetConversationsResponse
import me.floow.domain.api.models.ChatsGetConversationResponse
import me.floow.domain.api.models.ChatsGetMessagesResponse
import me.floow.domain.api.models.ChatsGetMessagesAroundResponse
import me.floow.domain.api.models.ChatsGetOrCreateDirectResponse
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

	suspend fun deleteMessage(
		conversationId: Long,
		messageId: Long
	): ChatsDeleteMessageResponse

	suspend fun updateMessage(
		conversationId: Long,
		messageId: Long,
		text: String
	): ChatsUpdateMessageResponse

	suspend fun getPinnedMessages(
		conversationId: Long,
		limit: Int = 20
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
		isTyping: Boolean
	): ChatsTypingResponse
}
