package me.floow.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.floow.database.dbo.DirectChatConversationEntity
import me.floow.database.dbo.DirectChatMessageEntity
import me.floow.database.dbo.DirectChatReadStateEntity

@Dao
interface DirectChatsDao {
	@Query(
		"""
		SELECT *
		FROM direct_chat_conversations
		ORDER BY COALESCE(last_message_id, 0) DESC, updated_at DESC, id DESC
		"""
	)
	fun observeConversations(): Flow<List<DirectChatConversationEntity>>

	@Query(
		"""
		SELECT *
		FROM direct_chat_conversations
		ORDER BY COALESCE(last_message_id, 0) DESC, updated_at DESC, id DESC
		LIMIT :limit OFFSET :offset
		"""
	)
	suspend fun getConversations(limit: Int, offset: Int): List<DirectChatConversationEntity>

	@Query("SELECT * FROM direct_chat_conversations WHERE id = :conversationId LIMIT 1")
	suspend fun getConversationById(conversationId: Long): DirectChatConversationEntity?

	@Query("SELECT * FROM direct_chat_conversations WHERE id = :conversationId LIMIT 1")
	fun observeConversationById(conversationId: Long): Flow<DirectChatConversationEntity?>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertConversations(items: List<DirectChatConversationEntity>)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertConversation(item: DirectChatConversationEntity)

	@Query(
		"""
		SELECT *
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		ORDER BY created_at DESC, id DESC
		LIMIT :limit
		"""
	)
	fun observeMessagesDesc(
		conversationId: Long,
		limit: Int
	): Flow<List<DirectChatMessageEntity>>

	@Query(
		"""
		SELECT *
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND (:beforeId <= 0 OR id < :beforeId)
		ORDER BY id DESC
		LIMIT :limit
		"""
	)
	suspend fun getMessagesDesc(
		conversationId: Long,
		beforeId: Long,
		limit: Int
	): List<DirectChatMessageEntity>

	@Query(
		"""
		SELECT *
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND id < :anchorMessageId
		ORDER BY id DESC
		LIMIT :limit
		"""
	)
	suspend fun getMessagesBeforeAnchorDesc(
		conversationId: Long,
		anchorMessageId: Long,
		limit: Int
	): List<DirectChatMessageEntity>

	@Query(
		"""
		SELECT *
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND id > :anchorMessageId
		ORDER BY id ASC
		LIMIT :limit
		"""
	)
	suspend fun getMessagesAfterAnchorAsc(
		conversationId: Long,
		anchorMessageId: Long,
		limit: Int
	): List<DirectChatMessageEntity>

	@Query(
		"""
		SELECT COUNT(*)
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND id > :anchorMessageId
		"""
	)
	suspend fun countNewerMessages(
		conversationId: Long,
		anchorMessageId: Long
	): Int

	@Query(
		"""
		SELECT COUNT(*)
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND id < :anchorMessageId
		"""
	)
	suspend fun countOlderMessages(
		conversationId: Long,
		anchorMessageId: Long
	): Int

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertMessages(items: List<DirectChatMessageEntity>)

	@Query(
		"""
		SELECT id
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		ORDER BY id DESC
		LIMIT :limit
		"""
	)
	suspend fun getLatestMessageIds(
		conversationId: Long,
		limit: Int
	): List<Long>

	@Query(
		"""
		SELECT id
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND id <= 0
		  AND delivery_status != 'SENT'
		"""
	)
	suspend fun getProtectedLocalMessageIds(
		conversationId: Long
	): List<Long>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertMessage(item: DirectChatMessageEntity)

	@Query(
		"""
		SELECT *
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND id = :messageId
		LIMIT 1
		"""
	)
	suspend fun getMessageById(
		conversationId: Long,
		messageId: Long
	): DirectChatMessageEntity?

	@Query(
		"""
		SELECT *
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND is_pinned = 1
		ORDER BY pinned_at DESC, id DESC
		LIMIT :limit
		"""
	)
	suspend fun getPinnedMessages(
		conversationId: Long,
		limit: Int
	): List<DirectChatMessageEntity>

	@Query(
		"""
		SELECT *
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND is_pinned = 1
		ORDER BY pinned_at DESC, id DESC
		LIMIT :limit
		"""
	)
	fun observePinnedMessages(
		conversationId: Long,
		limit: Int
	): Flow<List<DirectChatMessageEntity>>

	@Query(
		"""
		SELECT *
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND client_message_id = :clientMessageId
		LIMIT 1
		"""
	)
	suspend fun getMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String
	): DirectChatMessageEntity?

	@Query(
		"""
		UPDATE direct_chat_messages
		SET id = :id,
		    sender_id = :senderId,
		    sender_username = :senderUsername,
		    sender_name = :senderName,
		    sender_avatar_url = :senderAvatarUrl,
		    text = :text,
		    client_message_id = :clientMessageId,
		    reply_to_message_id = :replyToMessageId,
		    reply_to_message_text = :replyToMessageText,
		    is_pinned = CASE WHEN :isPinned THEN 1 ELSE 0 END,
		    pinned_at = :pinnedAt,
		    pinned_by_user_id = :pinnedByUserId,
		    delivery_status = :deliveryStatus,
		    updated_at = :updatedAt
		WHERE conversation_id = :conversationId
		  AND client_message_id = :clientMessageId
		"""
	)
	suspend fun updateMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String,
		id: Long,
		senderId: String,
		senderUsername: String?,
		senderName: String?,
		senderAvatarUrl: String?,
		text: String,
		replyToMessageId: Long?,
		replyToMessageText: String?,
		isPinned: Boolean,
		pinnedAt: Long?,
		pinnedByUserId: String?,
		deliveryStatus: String,
		updatedAt: Long
	): Int

	@Query(
		"""
		UPDATE direct_chat_messages
		SET is_pinned = CASE WHEN :isPinned THEN 1 ELSE 0 END,
		    pinned_at = :pinnedAt,
		    pinned_by_user_id = :pinnedByUserId
		WHERE conversation_id = :conversationId
		  AND id = :messageId
		"""
	)
	suspend fun setMessagePinned(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean,
		pinnedAt: Long?,
		pinnedByUserId: String?
	): Int

	@Query(
		"""
		UPDATE direct_chat_messages
		SET delivery_status = :deliveryStatus
		WHERE conversation_id = :conversationId
		  AND id = :messageId
		"""
	)
	suspend fun updateMessageDeliveryStatus(
		conversationId: Long,
		messageId: Long,
		deliveryStatus: String
	): Int

	@Query(
		"""
		UPDATE direct_chat_messages
		SET delivery_status = :deliveryStatus
		WHERE conversation_id = :conversationId
		  AND client_message_id = :clientMessageId
		"""
	)
	suspend fun updateMessageDeliveryStatusByClientMessageId(
		conversationId: Long,
		clientMessageId: String,
		deliveryStatus: String
	): Int

	@Query(
		"""
		DELETE FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND id = :messageId
		"""
	)
	suspend fun deleteMessage(
		conversationId: Long,
		messageId: Long
	)

	@Query(
		"""
		DELETE FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND client_message_id = :clientMessageId
		"""
	)
	suspend fun deleteMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String
	)

	@Query(
		"""
		DELETE FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND id IN (:messageIds)
		"""
	)
	suspend fun deleteMessagesByIds(
		conversationId: Long,
		messageIds: List<Long>
	)

	@Query(
		"""
		SELECT *
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		ORDER BY id DESC
		LIMIT 1
		"""
	)
	suspend fun getLastMessage(conversationId: Long): DirectChatMessageEntity?

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertReadState(state: DirectChatReadStateEntity)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertReadStates(states: List<DirectChatReadStateEntity>)

	@Query("SELECT * FROM direct_chat_read_state WHERE conversation_id = :conversationId LIMIT 1")
	suspend fun getReadState(conversationId: Long): DirectChatReadStateEntity?

	@Query(
		"""
		SELECT MAX(id)
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND sender_id = :peerUserId
		"""
	)
	suspend fun getMaxIncomingMessageId(
		conversationId: Long,
		peerUserId: String
	): Long?

	@Query(
		"""
		SELECT COUNT(*)
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND sender_id = :peerUserId
		  AND id > :afterMessageId
		"""
	)
	suspend fun getUnreadIncomingCount(
		conversationId: Long,
		peerUserId: String,
		afterMessageId: Long
	): Int

	@Query(
		"""
		SELECT MIN(id)
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND sender_id = :peerUserId
		  AND id > :afterMessageId
		"""
	)
	suspend fun getFirstUnreadIncomingMessageId(
		conversationId: Long,
		peerUserId: String,
		afterMessageId: Long
	): Long?

	@Query(
		"""
		UPDATE direct_chat_conversations
		SET unread_count = :unreadCount,
		    last_read_message_id = CASE
		    	WHEN :lastReadMessageId > last_read_message_id THEN :lastReadMessageId
		    	ELSE last_read_message_id
		    END
		WHERE id = :conversationId
		"""
	)
	suspend fun applyConversationReadState(
		conversationId: Long,
		lastReadMessageId: Long,
		unreadCount: Int
	)

	@Query(
		"""
		UPDATE direct_chat_conversations
		SET peer_last_read_message_id = CASE
			WHEN :messageId > COALESCE(peer_last_read_message_id, 0) THEN :messageId
			ELSE peer_last_read_message_id
		END
		WHERE id = :conversationId
		"""
	)
	suspend fun applyPeerLastReadState(
		conversationId: Long,
		messageId: Long
	)

	@Query("DELETE FROM direct_chat_messages WHERE conversation_id = :conversationId")
	suspend fun clearMessagesForConversation(conversationId: Long)

	@Query("DELETE FROM direct_chat_conversations")
	suspend fun clearConversations()

	@Query("DELETE FROM direct_chat_messages")
	suspend fun clearMessages()

	@Query("DELETE FROM direct_chat_read_state")
	suspend fun clearReadState()
}
