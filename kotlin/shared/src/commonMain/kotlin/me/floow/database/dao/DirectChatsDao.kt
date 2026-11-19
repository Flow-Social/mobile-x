package me.floow.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import me.floow.database.dbo.DirectChatConversationEntity
import me.floow.database.dbo.DirectChatMessageEntity
import me.floow.database.dbo.DirectChatOutboxCommandEntity
import me.floow.database.dbo.DirectChatReadStateEntity
import me.floow.database.dbo.DirectChatProgressEntity

@Dao
interface DirectChatsDao {
    @Query("SELECT * FROM direct_chat_progress WHERE account_id = :accountId AND conversation_id = :conversationId")
    suspend fun getProgress(accountId: String, conversationId: Long): DirectChatProgressEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgress(row: DirectChatProgressEntity)

    @Query("""
        UPDATE direct_chat_progress
        SET anchor_message_id = :messageId, anchor_offset_px = :offset, bottom_pinned = :bottom,
            oldest_cached_id = :oldest, newest_cached_id = :newest, cache_has_older = :hasOlder, cache_has_newer = :hasNewer
        WHERE account_id = :accountId AND conversation_id = :conversationId
    """)
    suspend fun saveViewport(
        accountId: String, conversationId: Long, messageId: Long?, offset: Int, bottom: Boolean,
        oldest: Long?, newest: Long?, hasOlder: Boolean, hasNewer: Boolean,
    )

    @Query("""
        SELECT * FROM direct_chat_messages
        WHERE conversation_id = :conversationId AND id BETWEEN :oldest AND :newest
        ORDER BY id ASC
    """)
    suspend fun getVerifiedWindow(conversationId: Long, oldest: Long, newest: Long): List<DirectChatMessageEntity>

    @Query("""
        UPDATE direct_chat_progress SET pending_read = MAX(pending_read, :messageId)
        WHERE account_id = :accountId AND conversation_id = :conversationId
    """)
    suspend fun enqueueRead(accountId: String, conversationId: Long, messageId: Long)

    @Query("""
        UPDATE direct_chat_progress SET pending_read = 0
        WHERE account_id = :accountId AND conversation_id = :conversationId AND pending_read <= :messageId
    """)
    suspend fun acknowledgeRead(accountId: String, conversationId: Long, messageId: Long)

    @Query("""
        SELECT * FROM direct_chat_progress WHERE account_id = :accountId AND pending_read > 0
        ORDER BY conversation_id
    """)
    fun observePendingReads(accountId: String): Flow<List<DirectChatProgressEntity>>


	@Query(
		"""
		SELECT *
		FROM direct_chat_conversations
		ORDER BY is_pinned DESC, pinned_at DESC, COALESCE(last_message_created_at, updated_at) DESC, updated_at DESC, id DESC
		"""
	)
	fun observeConversations(): Flow<List<DirectChatConversationEntity>>

	@Query(
		"""
		SELECT *
		FROM direct_chat_conversations
		ORDER BY is_pinned DESC, pinned_at DESC, COALESCE(last_message_created_at, updated_at) DESC, updated_at DESC, id DESC
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
		UPDATE direct_chat_conversations
		SET is_pinned = CASE WHEN :isPinned THEN 1 ELSE 0 END,
		    pinned_at = :pinnedAt
		WHERE id = :conversationId
		"""
	)
	suspend fun setConversationPinned(
		conversationId: Long,
		isPinned: Boolean,
		pinnedAt: Long?
	): Int

	@Query(
		"""
		SELECT *
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		ORDER BY created_at DESC, local_id DESC
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
		  AND (:beforeId <= 0 OR (id > 0 AND id < :beforeId))
		ORDER BY created_at DESC, local_id DESC
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
		SELECT *
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		ORDER BY created_at DESC, local_id DESC
		LIMIT :limit
		"""
	)
	suspend fun getLatestMessages(
		conversationId: Long,
		limit: Int
	): List<DirectChatMessageEntity>

	@Query(
		"""
		SELECT local_id
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND id <= 0
		  AND delivery_status != 'SENT'
		"""
	)
	suspend fun getProtectedPendingLocalIds(
		conversationId: Long
	): List<Long>

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertMessage(item: DirectChatMessageEntity)

	@Insert(onConflict = OnConflictStrategy.REPLACE)
	suspend fun upsertOutgoingCommand(item: DirectChatOutboxCommandEntity)

	@Query("SELECT * FROM direct_chat_outbox_commands WHERE client_message_id = :clientMessageId LIMIT 1")
	suspend fun getOutgoingCommand(clientMessageId: String): DirectChatOutboxCommandEntity?

	@Query(
		"""
		SELECT *
		FROM direct_chat_outbox_commands
		WHERE state IN ('QUEUED', 'FAILED', 'SENDING')
		  AND next_attempt_at <= :now
		ORDER BY next_attempt_at ASC, created_at ASC
		LIMIT :limit
		"""
	)
	suspend fun getDueOutgoingCommands(
		now: Long,
		limit: Int
	): List<DirectChatOutboxCommandEntity>

	@Query(
		"""
		SELECT messages.*
		FROM direct_chat_messages AS messages
		INNER JOIN direct_chat_outbox_commands AS commands
		  ON commands.client_message_id = messages.client_message_id
		 AND commands.conversation_id = messages.conversation_id
		WHERE commands.conversation_id = :conversationId
		  AND commands.state IN ('QUEUED', 'SENDING', 'FAILED')
		ORDER BY messages.created_at ASC, messages.local_id ASC
		"""
	)
	fun observePendingOutboxMessages(conversationId: Long): Flow<List<DirectChatMessageEntity>>

	@Query(
		"""
		UPDATE direct_chat_outbox_commands
		SET state = 'SENDING',
		    updated_at = :now,
		    last_error = NULL
		WHERE client_message_id = :clientMessageId
		  AND state IN ('QUEUED', 'FAILED', 'SENDING')
		"""
	)
	suspend fun markOutgoingSending(
		clientMessageId: String,
		now: Long
	): Int

	@Query(
		"""
		UPDATE direct_chat_outbox_commands
		SET state = 'FAILED',
		    attempt_count = attempt_count + 1,
		    next_attempt_at = :nextAttemptAt,
		    last_error = :error,
		    updated_at = :now
		WHERE client_message_id = :clientMessageId
		  AND state IN ('QUEUED', 'SENDING', 'FAILED')
		"""
	)
	suspend fun markOutgoingFailed(
		clientMessageId: String,
		error: String?,
		nextAttemptAt: Long,
		now: Long
	): Int

	@Query(
		"""
		SELECT *
		FROM direct_chat_outbox_commands
		WHERE (content_type IS NOT NULL AND content_type != 'text')
		  AND upload_stage NOT IN ('SENT', 'CANCELLED')
		  AND state != 'CANCELLED'
		ORDER BY created_at ASC
		"""
	)
	suspend fun getActiveMediaCommands(): List<DirectChatOutboxCommandEntity>

	@Query(
		"""
		SELECT *
		FROM direct_chat_outbox_commands
		WHERE (content_type IS NOT NULL AND content_type != 'text')
		  AND upload_stage NOT IN ('SENT', 'CANCELLED')
		  AND state != 'CANCELLED'
		ORDER BY created_at ASC
		"""
	)
	fun observeActiveMediaCommands(): Flow<List<DirectChatOutboxCommandEntity>>

	@Query(
		"""
		UPDATE direct_chat_outbox_commands
		SET upload_stage = :uploadStage,
		    media_url = COALESCE(:mediaUrl, media_url),
		    media_thumbnail_url = COALESCE(:mediaThumbnailUrl, media_thumbnail_url),
		    last_error = :error,
		    updated_at = :now
		WHERE client_message_id = :clientMessageId
		"""
	)
	suspend fun updateOutboxMediaStage(
		clientMessageId: String,
		uploadStage: String,
		mediaUrl: String?,
		mediaThumbnailUrl: String?,
		error: String?,
		now: Long
	): Int

	@Query(
		"""
		UPDATE direct_chat_outbox_commands
		SET state = 'SENT',
		    upload_stage = 'SENT',
		    last_error = NULL,
		    updated_at = :now
		WHERE client_message_id = :clientMessageId
		  AND state != 'CANCELLED'
		"""
	)
	suspend fun markOutgoingSent(
		clientMessageId: String,
		now: Long
	): Int

	@Query(
		"""
		UPDATE direct_chat_outbox_commands
		SET state = 'CANCELLED',
		    upload_stage = 'CANCELLED',
		    updated_at = :now
		WHERE (:conversationId <= 0 OR conversation_id = :conversationId)
		  AND client_message_id = :clientMessageId
		  AND state != 'SENT'
		"""
	)
	suspend fun cancelOutgoing(
		conversationId: Long,
		clientMessageId: String,
		now: Long
	): Int

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
		WHERE local_id = :localId
		LIMIT 1
		"""
	)
	suspend fun getMessageByLocalId(localId: Long): DirectChatMessageEntity?

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

    @Query("""
        UPDATE direct_chat_messages
        SET is_pinned = 0, pinned_at = NULL, pinned_by_user_id = NULL
        WHERE conversation_id = :conversationId AND is_pinned = 1 AND id NOT IN (:ids)
    """)
    suspend fun clearPinsAbsentFrom(conversationId: Long, ids: List<Long>)

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
		SELECT *
		FROM direct_chat_messages
		WHERE id <= 0
		  AND client_message_id IS NOT NULL
		  AND delivery_status != 'SENT'
		ORDER BY updated_at DESC, created_at DESC
		LIMIT :limit
		"""
	)
	suspend fun getPendingOutgoingMessages(limit: Int): List<DirectChatMessageEntity>

	@Query(
		"""
		UPDATE direct_chat_messages
		SET id = :id,
		    sender_id = :senderId,
		    sender_username = :senderUsername,
		    sender_name = :senderName,
		    sender_avatar_url = :senderAvatarUrl,
		    text = :text,
		    content_type = :contentType,
		    media_url = :mediaUrl,
		    media_object_key = :mediaObjectKey,
		    media_mime_type = :mediaMimeType,
		    media_size_bytes = :mediaSizeBytes,
		    media_duration_ms = :mediaDurationMs,
		    media_local_path = :mediaLocalPath,
		    media_width = :mediaWidth,
		    media_height = :mediaHeight,
		    media_thumbnail_url = :mediaThumbnailUrl,
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
		contentType: String?,
		mediaUrl: String?,
		mediaObjectKey: String?,
		mediaMimeType: String?,
		mediaSizeBytes: Long?,
		mediaDurationMs: Long?,
		mediaLocalPath: String?,
		mediaWidth: Int?,
		mediaHeight: Int?,
		mediaThumbnailUrl: String?,
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
		SET id = :id,
		    sender_id = :senderId,
		    sender_username = :senderUsername,
		    sender_name = :senderName,
		    sender_avatar_url = :senderAvatarUrl,
		    text = :text,
		    content_type = :contentType,
		    media_url = :mediaUrl,
		    media_object_key = :mediaObjectKey,
		    media_mime_type = :mediaMimeType,
		    media_size_bytes = :mediaSizeBytes,
		    media_duration_ms = :mediaDurationMs,
		    media_local_path = :mediaLocalPath,
		    media_width = :mediaWidth,
		    media_height = :mediaHeight,
		    media_thumbnail_url = :mediaThumbnailUrl,
		    media_waveform = :mediaWaveform,
		    group_id = :groupId,
		    media_position = :mediaPosition,
		    client_message_id = :clientMessageId,
		    reply_to_message_id = :replyToMessageId,
		    reply_to_message_text = :replyToMessageText,
		    is_pinned = CASE WHEN :isPinned THEN 1 ELSE 0 END,
		    pinned_at = :pinnedAt,
		    pinned_by_user_id = :pinnedByUserId,
		    delivery_status = :deliveryStatus,
		    created_at = :createdAt,
		    updated_at = :updatedAt
		WHERE local_id = :localId
		"""
	)
	suspend fun updateMessageByLocalId(
		localId: Long,
		id: Long,
		senderId: String,
		senderUsername: String?,
		senderName: String?,
		senderAvatarUrl: String?,
		text: String,
		contentType: String?,
		mediaUrl: String?,
		mediaObjectKey: String?,
		mediaMimeType: String?,
		mediaSizeBytes: Long?,
		mediaDurationMs: Long?,
		mediaLocalPath: String?,
		mediaWidth: Int?,
		mediaHeight: Int?,
		mediaThumbnailUrl: String?,
		mediaWaveform: String?,
		groupId: String?,
		mediaPosition: Int,
		clientMessageId: String?,
		replyToMessageId: Long?,
		replyToMessageText: String?,
		isPinned: Boolean,
		pinnedAt: Long?,
		pinnedByUserId: String?,
		deliveryStatus: String,
		createdAt: Long,
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
		WHERE (:conversationId <= 0 OR conversation_id = :conversationId)
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

    @Query("""
        UPDATE direct_chat_messages SET reply_to_message_text = NULL
        WHERE conversation_id = :conversationId AND reply_to_message_id IN (:ids)
    """)
    suspend fun clearDeletedReplyPreviews(conversationId: Long, ids: List<Long>)

	@Query(
		"""
		DELETE FROM direct_chat_messages
		WHERE local_id = :localId
		"""
	)
	suspend fun deleteMessageByLocalId(localId: Long)

	@Query(
		"""
		DELETE FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		  AND local_id IN (:localIds)
		"""
	)
	suspend fun deleteMessagesByLocalIds(
		conversationId: Long,
		localIds: List<Long>
	)

	@Query(
		"""
		SELECT *
		FROM direct_chat_messages
		WHERE conversation_id = :conversationId
		ORDER BY created_at DESC, local_id DESC
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

	@Query("SELECT * FROM direct_chat_read_state")
	fun observeReadStates(): Flow<List<DirectChatReadStateEntity>>

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

	@Query("DELETE FROM direct_chat_conversations WHERE id = :conversationId")
	suspend fun deleteConversation(conversationId: Long)

	@Query("DELETE FROM direct_chat_messages WHERE conversation_id = :conversationId")
	suspend fun clearMessagesForConversation(conversationId: Long)

	@Query("DELETE FROM direct_chat_read_state WHERE conversation_id = :conversationId")
	suspend fun clearReadStateForConversation(conversationId: Long)

	@Query("DELETE FROM direct_chat_outbox_commands WHERE conversation_id = :conversationId")
	suspend fun clearOutgoingCommandsForConversation(conversationId: Long)

	@Query("DELETE FROM direct_chat_conversations")
	suspend fun clearConversations()

	@Query("DELETE FROM direct_chat_messages")
	suspend fun clearMessages()

	@Query("DELETE FROM direct_chat_outbox_commands")
	suspend fun clearOutgoingCommands()

	@Query("DELETE FROM direct_chat_read_state")
	suspend fun clearReadState()
}
