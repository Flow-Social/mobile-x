package me.floow.database.localstore

import androidx.room.withTransaction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.floow.database.AppDatabase
import me.floow.database.dao.DirectChatsDao
import me.floow.database.dbo.DirectChatConversationEntity
import me.floow.database.dbo.DirectChatMessageEntity
import me.floow.database.dbo.DirectChatReadStateEntity
import me.floow.domain.cache.DirectChatsLocalStore
import me.floow.domain.models.DirectChatAnchoredMessagesWindow
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatMessagesPage
import me.floow.domain.models.DirectChatPeer
import me.floow.domain.models.DirectChatReadState

class DirectChatsLocalStoreImpl(
	private val database: AppDatabase,
	private val dao: DirectChatsDao
) : DirectChatsLocalStore {
	override fun observeConversations(): Flow<List<DirectChatConversation>> {
		return dao.observeConversations().map { entities ->
			entities.map { entity -> entity.toDomain() }
		}
	}

	override suspend fun getConversations(limit: Int, offset: Int): List<DirectChatConversation> {
		return dao.getConversations(limit = limit, offset = offset)
			.map { entity -> entity.toDomain() }
	}

	override suspend fun upsertConversations(items: List<DirectChatConversation>) {
		if (items.isEmpty()) return
		database.withTransaction {
			dao.upsertConversations(items.map { item -> item.toEntity() })
			val readStates = items.map { item ->
				DirectChatReadStateEntity(
					conversationId = item.id,
					lastReadMessageId = item.lastReadMessageId,
					unreadCount = item.unreadCount.coerceAtLeast(0),
					firstUnreadId = null,
					maxMessageId = item.lastMessage?.id ?: 0L,
					readStateVersion = 0L,
					updatedAt = now()
				)
			}
			dao.upsertReadStates(readStates)
		}
	}

	override suspend fun upsertConversation(item: DirectChatConversation) {
		database.withTransaction {
			dao.upsertConversation(item.toEntity())
			dao.upsertReadState(
				DirectChatReadStateEntity(
					conversationId = item.id,
					lastReadMessageId = item.lastReadMessageId,
					unreadCount = item.unreadCount.coerceAtLeast(0),
					firstUnreadId = null,
					maxMessageId = item.lastMessage?.id ?: 0L,
					readStateVersion = 0L,
					updatedAt = now()
				)
			)
		}
	}

	override suspend fun getConversationById(conversationId: Long): DirectChatConversation? {
		return dao.getConversationById(conversationId)?.toDomain()
	}

	override fun observeMessages(
		conversationId: Long,
		limit: Int
	): Flow<DirectChatMessagesPage> {
		val sanitizedLimit = limit.coerceAtLeast(1)
		return dao.observeMessagesDesc(
			conversationId = conversationId,
			limit = sanitizedLimit
		).map { descItems ->
			val ascendingItems = descItems
				.asReversed()
				.map { entity -> entity.toDomain() }
			val nextBeforeId = if (descItems.size == sanitizedLimit) {
				descItems.lastOrNull()?.id
			} else {
				null
			}
			DirectChatMessagesPage(
				items = ascendingItems,
				nextBeforeId = nextBeforeId,
				peerLastReadMessageId = null
			)
		}
	}

	override suspend fun getMessages(
		conversationId: Long,
		limit: Int,
		beforeId: Long?
	): DirectChatMessagesPage {
		val sanitizedLimit = limit.coerceAtLeast(1)
		val boundary = beforeId ?: 0L
		val descItems = dao.getMessagesDesc(
			conversationId = conversationId,
			beforeId = boundary,
			limit = sanitizedLimit
		)
		val ascendingItems = descItems
			.asReversed()
			.map { entity -> entity.toDomain() }
		val nextBeforeId = if (descItems.size == sanitizedLimit) {
			descItems.lastOrNull()?.id
		} else {
			null
		}
		return DirectChatMessagesPage(
			items = ascendingItems,
			nextBeforeId = nextBeforeId,
			peerLastReadMessageId = dao.getConversationById(conversationId)?.peerLastReadMessageId
		)
	}

	override suspend fun getAnchoredMessagesWindow(
		conversationId: Long,
		anchorMessageId: Long,
		olderLimit: Int,
		newerLimit: Int
	): DirectChatAnchoredMessagesWindow? {
		if (conversationId <= 0L || anchorMessageId <= 0L) return null
		val anchorEntity = dao.getMessageById(
			conversationId = conversationId,
			messageId = anchorMessageId
		) ?: return null
		val sanitizedOlderLimit = olderLimit.coerceAtLeast(0)
		val sanitizedNewerLimit = newerLimit.coerceAtLeast(0)
		val olderMessages = dao.getMessagesBeforeAnchorDesc(
			conversationId = conversationId,
			anchorMessageId = anchorMessageId,
			limit = sanitizedOlderLimit
		).asReversed()
		val newerMessages = dao.getMessagesAfterAnchorAsc(
			conversationId = conversationId,
			anchorMessageId = anchorMessageId,
			limit = sanitizedNewerLimit
		)
		val totalOlderCached = dao.countOlderMessages(
			conversationId = conversationId,
			anchorMessageId = anchorMessageId
		).coerceAtLeast(0)
		val newerCachedCount = dao.countNewerMessages(
			conversationId = conversationId,
			anchorMessageId = anchorMessageId
		).coerceAtLeast(0)
		val latestCachedMessageId = dao.getLastMessage(conversationId)?.id ?: anchorMessageId
		val items = buildList {
			addAll(olderMessages.map { entity -> entity.toDomain() })
			add(anchorEntity.toDomain())
			addAll(newerMessages.map { entity -> entity.toDomain() })
		}
		return DirectChatAnchoredMessagesWindow(
			items = items,
			anchorMessageId = anchorMessageId,
			anchorIndex = olderMessages.size,
			hasOlderMessages = totalOlderCached > olderMessages.size,
			newerCachedCount = newerCachedCount,
			latestCachedMessageId = latestCachedMessageId,
			peerLastReadMessageId = dao.getConversationById(conversationId)?.peerLastReadMessageId
		)
	}

	override suspend fun upsertMessages(conversationId: Long, items: List<DirectChatMessage>) {
		if (items.isEmpty()) return
		database.withTransaction {
			val toInsert = mutableListOf<DirectChatMessageEntity>()
			items.forEach { item ->
				var entity = item.toEntity()
				val clientMessageId = entity.clientMessageId?.trim().orEmpty()
				val updatedRows = if (clientMessageId.isNotEmpty()) {
					dao.updateMessageByClientMessageId(
						conversationId = entity.conversationId,
						clientMessageId = clientMessageId,
						id = entity.id,
						senderId = entity.senderId,
						senderUsername = entity.senderUsername,
						senderName = entity.senderName,
						senderAvatarUrl = entity.senderAvatarUrl,
						text = entity.text,
						replyToMessageId = entity.replyToMessageId,
						replyToMessageText = entity.replyToMessageText,
						isPinned = entity.isPinned,
						pinnedAt = entity.pinnedAt,
						pinnedByUserId = entity.pinnedByUserId,
						deliveryStatus = entity.deliveryStatus,
						updatedAt = entity.updatedAt
					)
				} else {
					0
				}
				if (updatedRows <= 0) {
					toInsert.add(entity)
				}
			}
			if (toInsert.isNotEmpty()) {
				dao.upsertMessages(toInsert)
			}
			upsertConversationLastMessage(conversationId = conversationId, items = items)
		}
	}

	override suspend fun replaceLatestMessagesWindow(
		conversationId: Long,
		limit: Int,
		items: List<DirectChatMessage>
	) {
		if (conversationId <= 0L) return
		val sanitizedLimit = limit.coerceAtLeast(1)
		database.withTransaction {
			val currentLatestIds = dao.getLatestMessageIds(
				conversationId = conversationId,
				limit = sanitizedLimit
			)
			val protectedLocalIds = dao.getProtectedLocalMessageIds(conversationId)
			if (items.isNotEmpty()) {
				dao.upsertMessages(items.map { item -> item.toEntity() })
			}
			val incomingIds = items.asSequence()
				.map(DirectChatMessage::id)
				.filter { id -> id > 0L }
				.toSet()
			val staleIds = currentLatestIds
				.filterNot(incomingIds::contains)
				.filterNot(protectedLocalIds::contains)
			if (staleIds.isNotEmpty()) {
				dao.deleteMessagesByIds(
					conversationId = conversationId,
					messageIds = staleIds
				)
			}
			rebuildConversationLastMessage(conversationId)
		}
	}

	override suspend fun upsertMessage(message: DirectChatMessage) {
		database.withTransaction {
			var entity = message.toEntity()
			val clientMessageId = entity.clientMessageId?.trim().orEmpty()
			val updatedRows = if (clientMessageId.isNotEmpty()) {
				val existing = dao.getMessageByClientMessageId(
					conversationId = message.conversationId,
					clientMessageId = clientMessageId
				)
				if (existing != null && existing.id <= 0L) {
					entity = entity.copy(createdAt = existing.createdAt)
				}
				dao.updateMessageByClientMessageId(
					conversationId = message.conversationId,
					clientMessageId = clientMessageId,
					id = entity.id,
					senderId = entity.senderId,
					senderUsername = entity.senderUsername,
					senderName = entity.senderName,
					senderAvatarUrl = entity.senderAvatarUrl,
					text = entity.text,
					replyToMessageId = entity.replyToMessageId,
					replyToMessageText = entity.replyToMessageText,
					isPinned = entity.isPinned,
					pinnedAt = entity.pinnedAt,
					pinnedByUserId = entity.pinnedByUserId,
					deliveryStatus = entity.deliveryStatus,
					updatedAt = entity.updatedAt
				)
			} else {
				0
			}
			if (updatedRows <= 0) {
				dao.upsertMessage(entity)
			}
			upsertConversationLastMessage(conversationId = message.conversationId, items = listOf(message))
		}
	}

	override suspend fun getPinnedMessages(
		conversationId: Long,
		limit: Int
	): List<DirectChatMessage> {
		if (conversationId <= 0L) return emptyList()
		return dao.getPinnedMessages(
			conversationId = conversationId,
			limit = limit.coerceAtLeast(1)
		).map { entity -> entity.toDomain() }
	}

	override fun observePinnedMessages(
		conversationId: Long,
		limit: Int
	): Flow<List<DirectChatMessage>> {
		if (conversationId <= 0L) return kotlinx.coroutines.flow.flowOf(emptyList())
		return dao.observePinnedMessages(
			conversationId = conversationId,
			limit = limit.coerceAtLeast(1)
		).map { list -> list.map { entity -> entity.toDomain() } }
	}

	override suspend fun getMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String
	): DirectChatMessage? {
		if (conversationId <= 0L || clientMessageId.isBlank()) return null
		return dao.getMessageByClientMessageId(
			conversationId = conversationId,
			clientMessageId = clientMessageId
		)?.toDomain()
	}

	override suspend fun setMessagePinned(
		conversationId: Long,
		messageId: Long,
		isPinned: Boolean,
		pinnedAt: Long?,
		pinnedByUserId: String?
	): DirectChatMessage? {
		if (conversationId <= 0L || messageId <= 0L) return null
		return database.withTransaction {
			val updates = dao.setMessagePinned(
				conversationId = conversationId,
				messageId = messageId,
				isPinned = isPinned,
				pinnedAt = if (isPinned) pinnedAt ?: now() else null,
				pinnedByUserId = if (isPinned) pinnedByUserId else null
			)
			if (updates <= 0) return@withTransaction null
			dao.getMessageById(
				conversationId = conversationId,
				messageId = messageId
			)?.toDomain()
		}
	}

	override suspend fun deleteMessage(conversationId: Long, messageId: Long) {
		if (conversationId <= 0L || messageId <= 0L) return
		database.withTransaction {
			dao.deleteMessage(conversationId = conversationId, messageId = messageId)
			rebuildConversationLastMessage(conversationId = conversationId)
		}
	}

	override suspend fun deleteMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String
	) {
		if (conversationId <= 0L) return
		val normalizedClientMessageId = clientMessageId.trim()
		if (normalizedClientMessageId.isEmpty()) return
		database.withTransaction {
			dao.deleteMessageByClientMessageId(
				conversationId = conversationId,
				clientMessageId = normalizedClientMessageId
			)
			rebuildConversationLastMessage(conversationId = conversationId)
		}
	}

	override suspend fun applyReadState(state: DirectChatReadState) {
		database.withTransaction {
			val currentState = dao.getReadState(state.conversationId)
			if (currentState != null && currentState.readStateVersion > state.readStateVersion) {
				return@withTransaction
			}

			val normalizedUnread = state.unreadCount.coerceAtLeast(0)
			dao.upsertReadState(
				DirectChatReadStateEntity(
					conversationId = state.conversationId,
					lastReadMessageId = state.lastReadMessageId,
					unreadCount = normalizedUnread,
					firstUnreadId = state.firstUnreadId,
					maxMessageId = state.maxMessageId,
					readStateVersion = state.readStateVersion,
					updatedAt = now()
				)
			)
			dao.applyConversationReadState(
				conversationId = state.conversationId,
				lastReadMessageId = state.lastReadMessageId,
				unreadCount = normalizedUnread
			)
		}
	}

	override suspend fun applyLocalReadUpTo(
		conversationId: Long,
		messageId: Long
	): DirectChatReadState? {
		if (conversationId <= 0L || messageId <= 0L) return null
		return database.withTransaction {
			val conversation = dao.getConversationById(conversationId) ?: return@withTransaction null
			val currentReadState = dao.getReadState(conversationId)
			val normalizedReadMessageId = maxOf(
				messageId,
				conversation.lastReadMessageId,
				currentReadState?.lastReadMessageId ?: 0L
			)
			val maxIncomingMessageId = dao.getMaxIncomingMessageId(
				conversationId = conversationId,
				peerUserId = conversation.peerId
			) ?: 0L
			val unreadIncomingCount = dao.getUnreadIncomingCount(
				conversationId = conversationId,
				peerUserId = conversation.peerId,
				afterMessageId = normalizedReadMessageId
			).coerceAtLeast(0)
			val firstUnreadIncomingId = dao.getFirstUnreadIncomingMessageId(
				conversationId = conversationId,
				peerUserId = conversation.peerId,
				afterMessageId = normalizedReadMessageId
			)

			val nextVersion = currentReadState?.readStateVersion ?: 0L
			val localState = DirectChatReadState(
				conversationId = conversationId,
				lastReadMessageId = normalizedReadMessageId,
				unreadCount = unreadIncomingCount,
				firstUnreadId = firstUnreadIncomingId,
				maxMessageId = maxIncomingMessageId,
				readStateVersion = nextVersion
			)
			dao.upsertReadState(
				DirectChatReadStateEntity(
					conversationId = localState.conversationId,
					lastReadMessageId = localState.lastReadMessageId,
					unreadCount = localState.unreadCount,
					firstUnreadId = localState.firstUnreadId,
					maxMessageId = localState.maxMessageId,
					readStateVersion = localState.readStateVersion,
					updatedAt = now()
				)
			)
			dao.applyConversationReadState(
				conversationId = localState.conversationId,
				lastReadMessageId = localState.lastReadMessageId,
				unreadCount = localState.unreadCount
			)
			localState
		}
	}

	override suspend fun applyPeerLastReadUpTo(
		conversationId: Long,
		messageId: Long
	) {
		if (conversationId <= 0L || messageId <= 0L) return
		database.withTransaction {
			dao.applyPeerLastReadState(
				conversationId = conversationId,
				messageId = messageId
			)
		}
	}

	override suspend fun updateMessageDeliveryStatus(
		conversationId: Long,
		messageId: Long,
		deliveryStatus: me.floow.domain.models.MessageDeliveryStatus
	) {
		if (conversationId <= 0L || messageId <= 0L) return
		database.withTransaction {
			dao.updateMessageDeliveryStatus(
				conversationId = conversationId,
				messageId = messageId,
				deliveryStatus = deliveryStatus.name
			)
		}
	}

	override suspend fun updateMessageDeliveryStatusByClientMessageId(
		conversationId: Long,
		clientMessageId: String,
		deliveryStatus: me.floow.domain.models.MessageDeliveryStatus
	) {
		if (conversationId <= 0L) return
		val normalizedClientMessageId = clientMessageId.trim()
		if (normalizedClientMessageId.isEmpty()) return
		database.withTransaction {
			dao.updateMessageDeliveryStatusByClientMessageId(
				conversationId = conversationId,
				clientMessageId = normalizedClientMessageId,
				deliveryStatus = deliveryStatus.name
			)
		}
	}

	override suspend fun clear() {
		database.withTransaction {
			dao.clearMessages()
			dao.clearReadState()
			dao.clearConversations()
		}
	}

	private suspend fun upsertConversationLastMessage(
		conversationId: Long,
		items: List<DirectChatMessage>
	) {
		val existing = dao.getConversationById(conversationId) ?: return
		val latest = items.maxByOrNull(DirectChatMessage::id) ?: return
		val existingLastMessageId = existing.lastMessageId ?: 0L
		if (latest.id < existingLastMessageId) return

		val updated = existing.copy(
			lastMessageId = latest.id,
			lastMessageSenderId = latest.sender.id,
			lastMessageSenderUsername = latest.sender.username,
			lastMessageSenderName = latest.sender.name,
			lastMessageSenderAvatarUrl = latest.sender.avatarUrl,
			lastMessageText = latest.text,
			lastMessageCreatedAt = latest.createdAt,
			lastMessageUpdatedAt = latest.updatedAt,
			updatedAt = maxOf(existing.updatedAt, latest.updatedAt)
		)
		dao.upsertConversation(updated)
	}

	private suspend fun rebuildConversationLastMessage(conversationId: Long) {
		val existing = dao.getConversationById(conversationId) ?: return
		val latest = dao.getLastMessage(conversationId)
		if (latest == null) {
			dao.upsertConversation(
				existing.copy(
					lastMessageId = null,
					lastMessageSenderId = null,
					lastMessageSenderUsername = null,
					lastMessageSenderName = null,
					lastMessageSenderAvatarUrl = null,
					lastMessageText = null,
					lastMessageCreatedAt = null,
					lastMessageUpdatedAt = null
				)
			)
			return
		}

		dao.upsertConversation(
			existing.copy(
				lastMessageId = latest.id,
				lastMessageSenderId = latest.senderId,
				lastMessageSenderUsername = latest.senderUsername,
				lastMessageSenderName = latest.senderName,
				lastMessageSenderAvatarUrl = latest.senderAvatarUrl,
				lastMessageText = latest.text,
				lastMessageCreatedAt = latest.createdAt,
				lastMessageUpdatedAt = latest.updatedAt,
				updatedAt = maxOf(existing.updatedAt, latest.updatedAt)
			)
		)
	}

	private fun DirectChatConversation.toEntity(): DirectChatConversationEntity {
		val last = lastMessage
		return DirectChatConversationEntity(
			id = id,
			kind = kind,
			peerId = peer.id,
			peerUsername = peer.username,
			peerName = peer.name,
			peerAvatarUrl = peer.avatarUrl,
			lastMessageId = last?.id,
			lastMessageSenderId = last?.sender?.id,
			lastMessageSenderUsername = last?.sender?.username,
			lastMessageSenderName = last?.sender?.name,
			lastMessageSenderAvatarUrl = last?.sender?.avatarUrl,
			lastMessageText = last?.text,
			lastMessageCreatedAt = last?.createdAt,
			lastMessageUpdatedAt = last?.updatedAt,
			unreadCount = unreadCount.coerceAtLeast(0),
			lastReadMessageId = lastReadMessageId,
			peerLastReadMessageId = peerLastReadMessageId,
			createdAt = createdAt,
			updatedAt = updatedAt
		)
	}

	private fun DirectChatConversationEntity.toDomain(): DirectChatConversation {
		val message = if (lastMessageId != null) {
			DirectChatMessage(
				id = lastMessageId,
				conversationId = id,
				sender = DirectChatPeer(
					id = lastMessageSenderId ?: peerId,
					username = lastMessageSenderUsername,
					name = lastMessageSenderName,
					avatarUrl = lastMessageSenderAvatarUrl
				),
				text = lastMessageText.orEmpty(),
				replyToMessageId = null,
				replyToMessageText = null,
				isPinned = false,
				pinnedAt = null,
				pinnedByUserId = null,
				deliveryStatus = me.floow.domain.models.MessageDeliveryStatus.SENT,
				createdAt = lastMessageCreatedAt ?: updatedAt,
				updatedAt = lastMessageUpdatedAt ?: updatedAt
			)
		} else {
			null
		}

		return DirectChatConversation(
			id = id,
			kind = kind,
			peer = DirectChatPeer(
				id = peerId,
				username = peerUsername,
				name = peerName,
				avatarUrl = peerAvatarUrl
			),
			lastMessage = message,
			unreadCount = unreadCount.coerceAtLeast(0),
			lastReadMessageId = lastReadMessageId,
			peerLastReadMessageId = peerLastReadMessageId,
			createdAt = createdAt,
			updatedAt = updatedAt
		)
	}

	private fun DirectChatMessage.toEntity(): DirectChatMessageEntity {
		return DirectChatMessageEntity(
			id = id,
			conversationId = conversationId,
			senderId = sender.id,
			senderUsername = sender.username,
			senderName = sender.name,
			senderAvatarUrl = sender.avatarUrl,
			text = text,
			clientMessageId = clientMessageId,
			replyToMessageId = replyToMessageId,
			replyToMessageText = replyToMessageText,
			isPinned = isPinned,
			pinnedAt = pinnedAt,
			pinnedByUserId = pinnedByUserId,
			deliveryStatus = deliveryStatus.name,
			createdAt = createdAt,
			updatedAt = updatedAt
		)
	}

	private fun DirectChatMessageEntity.toDomain(): DirectChatMessage {
		return DirectChatMessage(
			id = id,
			conversationId = conversationId,
			sender = DirectChatPeer(
				id = senderId,
				username = senderUsername,
				name = senderName,
				avatarUrl = senderAvatarUrl
			),
			text = text,
			clientMessageId = clientMessageId,
			replyToMessageId = replyToMessageId,
			replyToMessageText = replyToMessageText,
			isPinned = isPinned,
			pinnedAt = pinnedAt,
			pinnedByUserId = pinnedByUserId,
			deliveryStatus = runCatching { me.floow.domain.models.MessageDeliveryStatus.valueOf(deliveryStatus) }
				.getOrDefault(me.floow.domain.models.MessageDeliveryStatus.SENT),
			createdAt = createdAt,
			updatedAt = updatedAt
		)
	}

	private fun now(): Long = System.currentTimeMillis()
}
