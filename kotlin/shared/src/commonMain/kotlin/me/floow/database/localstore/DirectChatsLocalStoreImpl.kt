package me.floow.database.localstore

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import me.floow.database.AppDatabase
import me.floow.database.withTransaction
import me.floow.domain.utils.currentTimeMillis
import me.floow.database.dao.DirectChatsDao
import me.floow.database.dbo.DirectChatConversationEntity
import me.floow.database.dbo.DirectChatMessageEntity
import me.floow.database.dbo.DirectChatOutboxCommandEntity
import me.floow.database.dbo.DirectChatReadStateEntity
import me.floow.domain.cache.DirectChatsLocalStore
import me.floow.domain.models.DirectChatAnchoredMessagesWindow
import me.floow.domain.models.DirectChatConversation
import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatMessageMedia
import me.floow.domain.models.DirectChatMessagesPage
import me.floow.domain.models.DirectChatOutboxCommand
import me.floow.domain.models.DirectChatOutboxState
import me.floow.domain.models.DirectChatPeer
import me.floow.domain.models.DirectChatReadState
import me.floow.domain.models.DirectChatUploadStage
import me.floow.domain.models.MessageDeliveryStatus

class DirectChatsLocalStoreImpl(
	private val database: AppDatabase,
	private val dao: DirectChatsDao
) : DirectChatsLocalStore {
	override fun observeConversations(): Flow<List<DirectChatConversation>> {
		return dao.observeConversations().map { entities ->
			entities.map { entity -> entity.toDomain() }
		}
	}

	override fun observeLocalReadStates(): Flow<Map<Long, DirectChatReadState>> {
		return dao.observeReadStates().map { entities ->
			entities.associate { entity ->
				entity.conversationId to DirectChatReadState(
					conversationId = entity.conversationId,
					lastReadMessageId = entity.lastReadMessageId,
					unreadCount = entity.unreadCount.coerceAtLeast(0),
					firstUnreadId = entity.firstUnreadId,
					maxMessageId = entity.maxMessageId,
					readStateVersion = entity.readStateVersion,
				)
			}
		}
	}

	override suspend fun getConversations(limit: Int, offset: Int): List<DirectChatConversation> {
		return dao.getConversations(limit = limit, offset = offset)
			.map { entity -> entity.toDomain() }
	}

	override suspend fun upsertConversations(items: List<DirectChatConversation>) {
		if (items.isEmpty()) return
		database.withTransaction {
			items.forEach { item -> upsertMergedConversation(item.toEntity()) }
			val readStates = items.map { item -> toMergedReadStateEntity(item) }
			dao.upsertReadStates(readStates)
		}
	}

	override suspend fun upsertConversation(item: DirectChatConversation) {
		database.withTransaction {
			upsertMergedConversation(item.toEntity())
			dao.upsertReadState(toMergedReadStateEntity(item))
		}
	}

	private suspend fun toMergedReadStateEntity(item: DirectChatConversation): DirectChatReadStateEntity {
		val current = dao.getReadState(item.id)
		val incomingLastReadMessageId = item.lastReadMessageId.coerceAtLeast(0L)
		val currentLastReadMessageId = current?.lastReadMessageId?.coerceAtLeast(0L) ?: 0L
		val shouldKeepLocalRead = currentLastReadMessageId > incomingLastReadMessageId
		return DirectChatReadStateEntity(
			conversationId = item.id,
			lastReadMessageId = if (shouldKeepLocalRead) currentLastReadMessageId else incomingLastReadMessageId,
			unreadCount = if (shouldKeepLocalRead) {
				current?.unreadCount?.coerceAtLeast(0) ?: 0
			} else {
				item.unreadCount.coerceAtLeast(0)
			},
			firstUnreadId = if (shouldKeepLocalRead) current?.firstUnreadId else null,
			maxMessageId = maxOf(current?.maxMessageId ?: 0L, item.lastMessage?.id ?: 0L),
			readStateVersion = current?.readStateVersion ?: 0L,
			updatedAt = now()
		)
	}

	private suspend fun upsertMergedConversation(incoming: DirectChatConversationEntity) {
		val existing = dao.getConversationById(incoming.id)
		if (existing == null) {
			dao.upsertConversation(incoming)
			return
		}
		val existingLastCreatedAt = existing.lastMessageCreatedAt ?: 0L
		val incomingLastCreatedAt = incoming.lastMessageCreatedAt ?: 0L
		val shouldPreserveLocalPreview = existingLastCreatedAt > incomingLastCreatedAt &&
			!existing.lastMessageClientMessageId.isNullOrBlank() &&
			existing.lastMessageDeliveryStatus != me.floow.domain.models.MessageDeliveryStatus.SENT.name
		val merged = if (shouldPreserveLocalPreview) {
			incoming.copy(
				lastMessageId = existing.lastMessageId,
				lastMessageSenderId = existing.lastMessageSenderId,
				lastMessageSenderUsername = existing.lastMessageSenderUsername,
				lastMessageSenderName = existing.lastMessageSenderName,
				lastMessageSenderAvatarUrl = existing.lastMessageSenderAvatarUrl,
				lastMessageText = existing.lastMessageText,
				lastMessageContentType = existing.lastMessageContentType,
				lastMessageMediaUrl = existing.lastMessageMediaUrl,
				lastMessageMediaObjectKey = existing.lastMessageMediaObjectKey,
				lastMessageMediaMimeType = existing.lastMessageMediaMimeType,
				lastMessageMediaSizeBytes = existing.lastMessageMediaSizeBytes,
				lastMessageMediaDurationMs = existing.lastMessageMediaDurationMs,
				lastMessageMediaWidth = existing.lastMessageMediaWidth,
				lastMessageMediaHeight = existing.lastMessageMediaHeight,
				lastMessageMediaThumbnailUrl = existing.lastMessageMediaThumbnailUrl,
				lastMessageClientMessageId = existing.lastMessageClientMessageId,
				lastMessageDeliveryStatus = existing.lastMessageDeliveryStatus,
				lastMessageCreatedAt = existing.lastMessageCreatedAt,
				lastMessageUpdatedAt = existing.lastMessageUpdatedAt,
				isPinned = existing.isPinned,
				pinnedAt = existing.pinnedAt,
				updatedAt = maxOf(incoming.updatedAt, existing.updatedAt)
			)
		} else {
			incoming.copy(
				isPinned = existing.isPinned || incoming.isPinned,
				pinnedAt = existing.pinnedAt ?: incoming.pinnedAt,
				updatedAt = maxOf(incoming.updatedAt, existing.updatedAt),
			)
		}
		dao.upsertConversation(merged)
	}

	override suspend fun getConversationById(conversationId: Long): DirectChatConversation? {
		return dao.getConversationById(conversationId)?.toDomain()
	}

	override suspend fun setConversationPinned(
		conversationId: Long,
		isPinned: Boolean,
		pinnedAt: Long?
	): DirectChatConversation? {
		if (conversationId <= 0L) return null
		val resolvedPinnedAt = if (isPinned) pinnedAt ?: now() else null
		dao.setConversationPinned(
			conversationId = conversationId,
			isPinned = isPinned,
			pinnedAt = resolvedPinnedAt,
		)
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
			items.forEach { item ->
				upsertMergedMessage(item.toEntity())
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
			val currentLatest = dao.getLatestMessages(
				conversationId = conversationId,
				limit = sanitizedLimit
			)
			val protectedLocalIds = dao.getProtectedPendingLocalIds(conversationId).toSet()
			if (items.isNotEmpty()) {
				items.forEach { item -> upsertMergedMessage(item.toEntity()) }
			}
			val incomingIds = items.asSequence()
				.map(DirectChatMessage::id)
				.filter { id -> id > 0L }
				.toSet()
			val staleLocalIds = currentLatest
				.filter { entity -> entity.id > 0L }
				.filterNot { entity -> entity.id in incomingIds }
				.filterNot { entity -> entity.localId in protectedLocalIds }
				.map(DirectChatMessageEntity::localId)
			if (staleLocalIds.isNotEmpty()) {
				dao.deleteMessagesByLocalIds(
					conversationId = conversationId,
					localIds = staleLocalIds
				)
			}
			rebuildConversationLastMessage(conversationId)
		}
	}

	override suspend fun upsertMessage(message: DirectChatMessage) {
		database.withTransaction {
			upsertMergedMessage(message.toEntity())
			upsertConversationLastMessage(conversationId = message.conversationId, items = listOf(message))
		}
	}

	override suspend fun enqueueOutgoingText(message: DirectChatMessage): DirectChatOutboxCommand? {
		val normalizedClientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty) ?: return null
		if (message.conversationId <= 0L || message.text.isBlank()) return null
		val createdAt = message.createdAt.takeIf { it > 0L } ?: now()
		val updatedAt = maxOf(message.updatedAt, createdAt)
		return database.withTransaction {
			val existingCommand = dao.getOutgoingCommand(normalizedClientMessageId)
			val nextCommand = DirectChatOutboxCommandEntity(
				clientMessageId = normalizedClientMessageId,
				conversationId = message.conversationId,
				text = message.text,
				contentType = message.contentType?.trim()?.takeIf(String::isNotEmpty),
				replyToMessageId = message.replyToMessageId?.takeIf { it > 0L },
				state = when (existingCommand?.state) {
					DirectChatOutboxState.SENT.name -> DirectChatOutboxState.SENT.name
					DirectChatOutboxState.CANCELLED.name -> DirectChatOutboxState.CANCELLED.name
					else -> DirectChatOutboxState.QUEUED.name
				},
				attemptCount = existingCommand?.attemptCount ?: 0,
				nextAttemptAt = 0L,
				lastError = null,
				createdAt = existingCommand?.createdAt ?: createdAt,
				updatedAt = updatedAt
			)
			dao.upsertOutgoingCommand(nextCommand)
			if (nextCommand.state != DirectChatOutboxState.CANCELLED.name) {
				upsertMergedMessage(
					message.copy(
						clientMessageId = normalizedClientMessageId,
						deliveryStatus = if (nextCommand.state == DirectChatOutboxState.SENT.name) {
							MessageDeliveryStatus.SENT
						} else {
							MessageDeliveryStatus.SENDING
						},
						createdAt = createdAt,
						updatedAt = updatedAt
					).toEntity()
				)
				rebuildConversationLastMessage(message.conversationId)
			}
			nextCommand.toDomain()
		}
	}

	override suspend fun enqueueOutgoingMedia(
		message: DirectChatMessage,
		localFilePath: String,
		mediaMimeType: String,
		mediaSizeBytes: Long,
		mediaDurationMs: Long?,
		mediaWidth: Int?,
		mediaHeight: Int?,
		mediaWaveform: String?,
		groupId: String?,
		mediaPosition: Int
	): DirectChatOutboxCommand? {
		val normalizedClientMessageId = message.clientMessageId?.trim()?.takeIf(String::isNotEmpty) ?: return null
		if (message.conversationId <= 0L) return null
		val createdAt = message.createdAt.takeIf { it > 0L } ?: now()
		val updatedAt = maxOf(message.updatedAt, createdAt)
		return database.withTransaction {
			val existingCommand = dao.getOutgoingCommand(normalizedClientMessageId)
			val nextCommand = DirectChatOutboxCommandEntity(
				clientMessageId = normalizedClientMessageId,
				conversationId = message.conversationId,
				text = message.text,
				contentType = message.contentType?.trim()?.takeIf(String::isNotEmpty),
				replyToMessageId = message.replyToMessageId?.takeIf { it > 0L },
				state = when (existingCommand?.state) {
					DirectChatOutboxState.SENT.name -> DirectChatOutboxState.SENT.name
					DirectChatOutboxState.CANCELLED.name -> DirectChatOutboxState.CANCELLED.name
					else -> DirectChatOutboxState.QUEUED.name
				},
				attemptCount = existingCommand?.attemptCount ?: 0,
				nextAttemptAt = 0L,
				lastError = null,
				createdAt = existingCommand?.createdAt ?: createdAt,
				updatedAt = updatedAt,
				localFilePath = localFilePath,
				mediaUrl = existingCommand?.mediaUrl ?: message.media?.url?.takeIf { it.isNotBlank() },
				mediaThumbnailUrl = existingCommand?.mediaThumbnailUrl ?: message.media?.thumbnailUrl?.takeIf { it.isNotBlank() },
				mediaMimeType = mediaMimeType,
				mediaSizeBytes = mediaSizeBytes,
				mediaDurationMs = mediaDurationMs ?: 0L,
				mediaWidth = mediaWidth ?: 0,
				mediaHeight = mediaHeight ?: 0,
				mediaWaveform = mediaWaveform,
				groupId = groupId,
				mediaPosition = mediaPosition,
				uploadStage = existingCommand?.uploadStage ?: DirectChatUploadStage.QUEUED.name,
				idempotencyKey = normalizedClientMessageId,
			)
			dao.upsertOutgoingCommand(nextCommand)
			if (nextCommand.state != DirectChatOutboxState.CANCELLED.name) {
				upsertMergedMessage(
					message.copy(
						clientMessageId = normalizedClientMessageId,
						deliveryStatus = if (nextCommand.state == DirectChatOutboxState.SENT.name) {
							MessageDeliveryStatus.SENT
						} else {
							MessageDeliveryStatus.SENDING
						},
						createdAt = createdAt,
						updatedAt = updatedAt
					).toEntity()
				)
				rebuildConversationLastMessage(message.conversationId)
			}
			nextCommand.toDomain()
		}
	}

	override suspend fun getActiveMediaCommands(): List<DirectChatOutboxCommand> {
		return dao.getActiveMediaCommands().map { it.toDomain() }
	}

	override fun observeActiveMediaCommands(): Flow<List<DirectChatOutboxCommand>> {
		return dao.observeActiveMediaCommands().map { list ->
			list.map { it.toDomain() }
		}
	}

	override suspend fun updateOutboxMediaStage(
		clientMessageId: String,
		uploadStage: DirectChatUploadStage,
		mediaUrl: String?,
		mediaThumbnailUrl: String?,
		error: String?
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		val now = now()
		dao.updateOutboxMediaStage(
			clientMessageId = normalizedClientMessageId,
			uploadStage = uploadStage.name,
			mediaUrl = mediaUrl,
			mediaThumbnailUrl = mediaThumbnailUrl,
			error = error,
			now = now
		)
	}

	override fun observePendingOutboxMessages(conversationId: Long): Flow<List<DirectChatMessage>> {
		if (conversationId <= 0L) return kotlinx.coroutines.flow.flowOf(emptyList())
		return dao.observePendingOutboxMessages(conversationId)
			.map { list -> list.map { entity -> entity.toDomain() } }
	}

	override suspend fun getOutgoingCommand(clientMessageId: String): DirectChatOutboxCommand? {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return null
		return dao.getOutgoingCommand(normalizedClientMessageId)?.toDomain()
	}

	override suspend fun getDueOutgoingCommands(
		now: Long,
		limit: Int
	): List<DirectChatOutboxCommand> {
		return dao.getDueOutgoingCommands(
			now = now,
			limit = limit.coerceAtLeast(1)
		).map { entity -> entity.toDomain() }
	}

	override suspend fun markOutgoingSending(
		clientMessageId: String,
		now: Long
	): DirectChatOutboxCommand? {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return null
		return database.withTransaction {
			dao.markOutgoingSending(normalizedClientMessageId, now)
			val command = dao.getOutgoingCommand(normalizedClientMessageId)
			if (command != null && command.state == DirectChatOutboxState.SENDING.name) {
				dao.updateMessageDeliveryStatusByClientMessageId(
					conversationId = command.conversationId,
					clientMessageId = normalizedClientMessageId,
					deliveryStatus = MessageDeliveryStatus.SENDING.name
				)
				rebuildConversationLastMessage(command.conversationId)
			}
			command?.toDomain()
		}
	}

	override suspend fun markOutgoingFailed(
		clientMessageId: String,
		error: String?,
		nextAttemptAt: Long,
		now: Long
	): DirectChatOutboxCommand? {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return null
		return database.withTransaction {
			dao.markOutgoingFailed(
				clientMessageId = normalizedClientMessageId,
				error = error?.take(400),
				nextAttemptAt = nextAttemptAt,
				now = now
			)
			val command = dao.getOutgoingCommand(normalizedClientMessageId)
			if (command != null && command.state == DirectChatOutboxState.FAILED.name) {
				dao.updateMessageDeliveryStatusByClientMessageId(
					conversationId = command.conversationId,
					clientMessageId = normalizedClientMessageId,
					deliveryStatus = MessageDeliveryStatus.FAILED.name
				)
				rebuildConversationLastMessage(command.conversationId)
			}
			command?.toDomain()
		}
	}

	override suspend fun markOutgoingSent(
		clientMessageId: String,
		serverMessage: DirectChatMessage,
		now: Long
	): DirectChatMessage? {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return null
		if (serverMessage.conversationId <= 0L || serverMessage.id <= 0L) return null
		return database.withTransaction {
			val command = dao.getOutgoingCommand(normalizedClientMessageId) ?: return@withTransaction null
			if (command.state == DirectChatOutboxState.CANCELLED.name) {
				dao.deleteMessageByClientMessageId(command.conversationId, normalizedClientMessageId)
				rebuildConversationLastMessage(command.conversationId)
				return@withTransaction null
			}
			val sent = serverMessage.copy(
				clientMessageId = serverMessage.clientMessageId?.trim()?.takeIf(String::isNotEmpty) ?: normalizedClientMessageId,
				deliveryStatus = MessageDeliveryStatus.SENT,
				updatedAt = maxOf(serverMessage.updatedAt, now)
			)
			dao.markOutgoingSent(normalizedClientMessageId, now)
			upsertMergedMessage(sent.toEntity())
			rebuildConversationLastMessage(sent.conversationId)
			dao.getMessageByClientMessageId(
				conversationId = sent.conversationId,
				clientMessageId = normalizedClientMessageId
			)?.toDomain()
				?: dao.getMessageById(
					conversationId = sent.conversationId,
					messageId = sent.id
				)?.toDomain()
				?: sent
		}
	}

	override suspend fun cancelOutgoing(
		conversationId: Long,
		clientMessageId: String
	) {
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty) ?: return
		database.withTransaction {
			dao.cancelOutgoing(
				conversationId = conversationId,
				clientMessageId = normalizedClientMessageId,
				now = now()
			)
			dao.deleteMessageByClientMessageId(
				conversationId = conversationId,
				clientMessageId = normalizedClientMessageId
			)
			if (conversationId > 0L) {
				rebuildConversationLastMessage(conversationId)
			}
		}
	}

	private suspend fun upsertMergedMessage(entity: DirectChatMessageEntity) {
		val clientMessageId = entity.clientMessageId?.trim()?.takeIf(String::isNotEmpty)
		val outboxCommand = clientMessageId?.let { dao.getOutgoingCommand(it) }
		if (outboxCommand?.state == DirectChatOutboxState.CANCELLED.name) {
			dao.deleteMessageByClientMessageId(entity.conversationId, clientMessageId)
			return
		}
		val existingByClient = clientMessageId?.let { id ->
			dao.getMessageByClientMessageId(
				conversationId = entity.conversationId,
				clientMessageId = id
			)
		}
		val existingByServerId = entity.id.takeIf { it > 0L }?.let { id ->
			dao.getMessageById(
				conversationId = entity.conversationId,
				messageId = id
			)
		}
		val target = existingByClient ?: existingByServerId
		if (target == null) {
			dao.upsertMessage(entity.copy(localId = 0L, clientMessageId = clientMessageId))
			return
		}

		if (existingByClient != null && existingByServerId != null && existingByServerId.localId != existingByClient.localId) {
			dao.deleteMessageByLocalId(existingByServerId.localId)
		}

		val shouldPreservePendingCreatedAt = target.id <= 0L && entity.id > 0L
		val next = entity.copy(
			localId = target.localId,
			clientMessageId = clientMessageId ?: target.clientMessageId,
			contentType = entity.contentType ?: target.contentType,
			mediaObjectKey = entity.mediaObjectKey ?: target.mediaObjectKey,
			mediaMimeType = entity.mediaMimeType ?: target.mediaMimeType,
			mediaSizeBytes = entity.mediaSizeBytes ?: target.mediaSizeBytes,
			mediaLocalPath = entity.mediaLocalPath ?: target.mediaLocalPath,
			mediaUrl = entity.mediaUrl ?: target.mediaUrl,
			mediaThumbnailUrl = entity.mediaThumbnailUrl ?: target.mediaThumbnailUrl,
			mediaWidth = entity.mediaWidth ?: target.mediaWidth,
			mediaHeight = entity.mediaHeight ?: target.mediaHeight,
			mediaDurationMs = entity.mediaDurationMs ?: target.mediaDurationMs,
			mediaWaveform = entity.mediaWaveform ?: target.mediaWaveform,
			groupId = entity.groupId ?: target.groupId,
			mediaPosition = if (entity.mediaPosition != 0) entity.mediaPosition else target.mediaPosition,
			createdAt = if (shouldPreservePendingCreatedAt) target.createdAt else entity.createdAt
		)
		dao.updateMessageByLocalId(
			localId = next.localId,
			id = next.id,
			senderId = next.senderId,
			senderUsername = next.senderUsername,
			senderName = next.senderName,
			senderAvatarUrl = next.senderAvatarUrl,
			text = next.text,
			contentType = next.contentType,
			mediaUrl = next.mediaUrl,
			mediaObjectKey = next.mediaObjectKey,
			mediaMimeType = next.mediaMimeType,
			mediaSizeBytes = next.mediaSizeBytes,
			mediaDurationMs = next.mediaDurationMs,
			mediaLocalPath = next.mediaLocalPath,
			mediaWidth = next.mediaWidth,
			mediaHeight = next.mediaHeight,
			mediaThumbnailUrl = next.mediaThumbnailUrl,
			mediaWaveform = next.mediaWaveform,
			groupId = next.groupId,
			mediaPosition = next.mediaPosition,
			clientMessageId = next.clientMessageId,
			replyToMessageId = next.replyToMessageId,
			replyToMessageText = next.replyToMessageText,
			isPinned = next.isPinned,
			pinnedAt = next.pinnedAt,
			pinnedByUserId = next.pinnedByUserId,
			deliveryStatus = next.deliveryStatus,
			createdAt = next.createdAt,
			updatedAt = next.updatedAt
		)
	}

	private suspend fun upsertConversationLastMessage(
		conversationId: Long,
		items: List<DirectChatMessage>
	) {
		val existing = dao.getConversationById(conversationId)
		val latest = items.maxWithOrNull(compareBy<DirectChatMessage> { it.createdAt }.thenBy { it.id }) ?: return
		if (existing == null) {
			val baseline = DirectChatConversationEntity(
				id = conversationId,
				kind = "direct",
				peerId = latest.sender.id,
				peerUsername = latest.sender.username,
				peerName = latest.sender.name,
				peerAvatarUrl = latest.sender.avatarUrl,
				lastMessageId = latest.id,
				lastMessageSenderId = latest.sender.id,
				lastMessageSenderUsername = latest.sender.username,
				lastMessageSenderName = latest.sender.name,
				lastMessageSenderAvatarUrl = latest.sender.avatarUrl,
				lastMessageText = latest.text,
				lastMessageContentType = latest.contentType,
				lastMessageMediaUrl = latest.media?.url,
				lastMessageMediaObjectKey = latest.media?.objectKey,
				lastMessageMediaMimeType = latest.media?.mimeType,
				lastMessageMediaSizeBytes = latest.media?.sizeBytes,
				lastMessageMediaDurationMs = latest.media?.durationMs,
				lastMessageMediaWidth = latest.media?.width,
				lastMessageMediaHeight = latest.media?.height,
				lastMessageMediaThumbnailUrl = latest.media?.thumbnailUrl,
				lastMessageClientMessageId = latest.clientMessageId,
				lastMessageDeliveryStatus = latest.deliveryStatus.name,
				lastMessageCreatedAt = latest.createdAt,
				lastMessageUpdatedAt = latest.updatedAt,
				unreadCount = 0,
				lastReadMessageId = 0L,
				peerLastReadMessageId = 0L,
				isPinned = false,
				pinnedAt = null,
				createdAt = latest.createdAt,
				updatedAt = latest.updatedAt
			)
			dao.upsertConversation(baseline)
			return
		}
		val existingLastCreatedAt = existing.lastMessageCreatedAt ?: 0L
		if (latest.createdAt < existingLastCreatedAt) return

		val updated = existing.copy(
			lastMessageId = latest.id,
			lastMessageSenderId = latest.sender.id,
			lastMessageSenderUsername = latest.sender.username,
			lastMessageSenderName = latest.sender.name,
			lastMessageSenderAvatarUrl = latest.sender.avatarUrl,
			lastMessageText = latest.text,
			lastMessageContentType = latest.contentType,
			lastMessageMediaUrl = latest.media?.url,
			lastMessageMediaObjectKey = latest.media?.objectKey,
			lastMessageMediaMimeType = latest.media?.mimeType,
			lastMessageMediaSizeBytes = latest.media?.sizeBytes,
			lastMessageMediaDurationMs = latest.media?.durationMs,
			lastMessageMediaWidth = latest.media?.width,
			lastMessageMediaHeight = latest.media?.height,
			lastMessageMediaThumbnailUrl = latest.media?.thumbnailUrl,
			lastMessageClientMessageId = latest.clientMessageId,
			lastMessageDeliveryStatus = latest.deliveryStatus.name,
			lastMessageCreatedAt = latest.createdAt,
			lastMessageUpdatedAt = latest.updatedAt,
			updatedAt = maxOf(existing.updatedAt, latest.updatedAt, latest.createdAt)
		)
		dao.upsertConversation(updated)
	}

	private suspend fun rebuildConversationLastMessage(conversationId: Long) {
		val existing = dao.getConversationById(conversationId)
		val latest = dao.getLastMessage(conversationId)
		if (existing == null) {
			if (latest != null) {
				val baseline = DirectChatConversationEntity(
					id = conversationId,
					kind = "direct",
					peerId = latest.senderId,
					peerUsername = latest.senderUsername,
					peerName = latest.senderName,
					peerAvatarUrl = latest.senderAvatarUrl,
					lastMessageId = latest.id,
					lastMessageSenderId = latest.senderId,
					lastMessageSenderUsername = latest.senderUsername,
					lastMessageSenderName = latest.senderName,
					lastMessageSenderAvatarUrl = latest.senderAvatarUrl,
					lastMessageText = latest.text,
					lastMessageContentType = latest.contentType,
					lastMessageMediaUrl = latest.mediaUrl,
					lastMessageMediaObjectKey = latest.mediaObjectKey,
					lastMessageMediaMimeType = latest.mediaMimeType,
					lastMessageMediaSizeBytes = latest.mediaSizeBytes,
					lastMessageMediaDurationMs = latest.mediaDurationMs,
					lastMessageMediaWidth = latest.mediaWidth,
					lastMessageMediaHeight = latest.mediaHeight,
					lastMessageMediaThumbnailUrl = latest.mediaThumbnailUrl,
					lastMessageClientMessageId = latest.clientMessageId,
					lastMessageDeliveryStatus = latest.deliveryStatus,
					lastMessageCreatedAt = latest.createdAt,
					lastMessageUpdatedAt = latest.updatedAt,
					unreadCount = 0,
					lastReadMessageId = 0L,
					peerLastReadMessageId = 0L,
					isPinned = false,
					pinnedAt = null,
					createdAt = latest.createdAt,
					updatedAt = latest.updatedAt
				)
				dao.upsertConversation(baseline)
			}
			return
		}
		if (latest == null) {
			dao.upsertConversation(
				existing.copy(
					lastMessageId = null,
					lastMessageSenderId = null,
					lastMessageSenderUsername = null,
					lastMessageSenderName = null,
					lastMessageSenderAvatarUrl = null,
					lastMessageText = null,
					lastMessageContentType = null,
					lastMessageMediaUrl = null,
					lastMessageMediaObjectKey = null,
					lastMessageMediaMimeType = null,
					lastMessageMediaSizeBytes = null,
					lastMessageMediaDurationMs = null,
					lastMessageMediaWidth = null,
					lastMessageMediaHeight = null,
					lastMessageMediaThumbnailUrl = null,
					lastMessageClientMessageId = null,
					lastMessageDeliveryStatus = null,
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
				lastMessageContentType = latest.contentType,
				lastMessageMediaUrl = latest.mediaUrl,
				lastMessageMediaObjectKey = latest.mediaObjectKey,
				lastMessageMediaMimeType = latest.mediaMimeType,
				lastMessageMediaSizeBytes = latest.mediaSizeBytes,
				lastMessageMediaDurationMs = latest.mediaDurationMs,
				lastMessageMediaWidth = latest.mediaWidth,
				lastMessageMediaHeight = latest.mediaHeight,
				lastMessageMediaThumbnailUrl = latest.mediaThumbnailUrl,
				lastMessageClientMessageId = latest.clientMessageId,
				lastMessageDeliveryStatus = latest.deliveryStatus,
				lastMessageCreatedAt = latest.createdAt,
				lastMessageUpdatedAt = latest.updatedAt,
				updatedAt = maxOf(existing.updatedAt, latest.updatedAt, latest.createdAt)
			)
		)
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

	override suspend fun getPendingOutgoingMessages(limit: Int): List<DirectChatMessage> {
		return dao.getPendingOutgoingMessages(limit.coerceAtLeast(1))
			.map { entity -> entity.toDomain() }
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

	override suspend fun deleteMessages(conversationId: Long, messageIds: List<Long>) {
		val validIds = messageIds.filter { it > 0L }
		if (conversationId <= 0L || validIds.isEmpty()) return
		database.withTransaction {
			dao.deleteMessagesByIds(conversationId = conversationId, messageIds = validIds)
			rebuildConversationLastMessage(conversationId = conversationId)
		}
	}

	override suspend fun deleteMessageByClientMessageId(
		conversationId: Long,
		clientMessageId: String
	) {
		val normalizedClientMessageId = clientMessageId.trim()
		if (normalizedClientMessageId.isEmpty()) return
		cancelOutgoing(
			conversationId = conversationId,
			clientMessageId = normalizedClientMessageId
		)
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
			rebuildConversationLastMessage(conversationId)
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
			rebuildConversationLastMessage(conversationId)
		}
	}

	override suspend fun deleteConversation(conversationId: Long) {
		if (conversationId <= 0L) return
		database.withTransaction {
			dao.clearOutgoingCommandsForConversation(conversationId)
			dao.clearMessagesForConversation(conversationId)
			dao.clearReadStateForConversation(conversationId)
			dao.deleteConversation(conversationId)
		}
	}

	override suspend fun clear() {
		database.withTransaction {
			dao.clearOutgoingCommands()
			dao.clearMessages()
			dao.clearReadState()
			dao.clearConversations()
		}
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
			lastMessageContentType = last?.contentType,
			lastMessageMediaUrl = last?.media?.url,
			lastMessageMediaObjectKey = last?.media?.objectKey,
			lastMessageMediaMimeType = last?.media?.mimeType,
			lastMessageMediaSizeBytes = last?.media?.sizeBytes,
			lastMessageMediaDurationMs = last?.media?.durationMs,
			lastMessageMediaWidth = last?.media?.width,
			lastMessageMediaHeight = last?.media?.height,
			lastMessageMediaThumbnailUrl = last?.media?.thumbnailUrl,
			lastMessageClientMessageId = last?.clientMessageId,
			lastMessageDeliveryStatus = last?.deliveryStatus?.name,
			lastMessageCreatedAt = last?.createdAt,
			lastMessageUpdatedAt = last?.updatedAt,
			unreadCount = unreadCount.coerceAtLeast(0),
			lastReadMessageId = lastReadMessageId,
			peerLastReadMessageId = peerLastReadMessageId,
			isPinned = isPinned,
			pinnedAt = pinnedAt,
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
				contentType = lastMessageContentType,
				media = toMedia(
					url = lastMessageMediaUrl,
					objectKey = lastMessageMediaObjectKey,
					mimeType = lastMessageMediaMimeType,
					sizeBytes = lastMessageMediaSizeBytes,
					durationMs = lastMessageMediaDurationMs,
					localPath = null,
					width = lastMessageMediaWidth,
					height = lastMessageMediaHeight,
					thumbnailUrl = lastMessageMediaThumbnailUrl
				),
				replyToMessageId = null,
				replyToMessageText = null,
				isPinned = false,
				pinnedAt = null,
				pinnedByUserId = null,
				clientMessageId = lastMessageClientMessageId,
				deliveryStatus = runCatching {
					me.floow.domain.models.MessageDeliveryStatus.valueOf(lastMessageDeliveryStatus.orEmpty())
				}.getOrDefault(me.floow.domain.models.MessageDeliveryStatus.SENT),
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
			isPinned = isPinned,
			pinnedAt = pinnedAt,
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
			contentType = contentType,
			mediaUrl = media?.url,
			mediaObjectKey = media?.objectKey,
			mediaMimeType = media?.mimeType,
			mediaSizeBytes = media?.sizeBytes,
			mediaDurationMs = media?.durationMs,
			mediaLocalPath = media?.localPath,
			mediaWidth = media?.width,
			mediaHeight = media?.height,
			mediaThumbnailUrl = media?.thumbnailUrl,
			mediaWaveform = media?.waveform?.takeIf { it.isNotEmpty() }?.joinToString(separator = ","),
			groupId = groupId ?: media?.groupId,
			mediaPosition = if (position > 0) position else (media?.position ?: 0),
			clientMessageId = clientMessageId?.trim()?.takeIf(String::isNotEmpty),
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
			contentType = contentType,
			media = toMedia(
				url = mediaUrl,
				objectKey = mediaObjectKey,
				mimeType = mediaMimeType,
				sizeBytes = mediaSizeBytes,
				durationMs = mediaDurationMs,
				localPath = mediaLocalPath,
				width = mediaWidth,
				height = mediaHeight,
				thumbnailUrl = mediaThumbnailUrl,
				waveform = mediaWaveform?.split(",")?.mapNotNull { it.trim().toIntOrNull() }?.takeIf { it.isNotEmpty() },
				groupId = groupId,
				position = mediaPosition,
			),
			clientMessageId = clientMessageId,
			replyToMessageId = replyToMessageId,
			replyToMessageText = replyToMessageText,
			isPinned = isPinned,
			pinnedAt = pinnedAt,
			pinnedByUserId = pinnedByUserId,
			deliveryStatus = runCatching { me.floow.domain.models.MessageDeliveryStatus.valueOf(deliveryStatus) }
				.getOrDefault(me.floow.domain.models.MessageDeliveryStatus.SENT),
			createdAt = createdAt,
			updatedAt = updatedAt,
			groupId = groupId,
			position = mediaPosition,
		)
	}

	private fun DirectChatOutboxCommandEntity.toDomain(): DirectChatOutboxCommand {
		return DirectChatOutboxCommand(
			clientMessageId = clientMessageId,
			conversationId = conversationId,
			text = text,
			contentType = contentType,
			replyToMessageId = replyToMessageId,
			state = runCatching { DirectChatOutboxState.valueOf(state) }
				.getOrDefault(DirectChatOutboxState.FAILED),
			attemptCount = attemptCount.coerceAtLeast(0),
			nextAttemptAt = nextAttemptAt,
			lastError = lastError,
			createdAt = createdAt,
			updatedAt = updatedAt,
			localFilePath = localFilePath,
			mediaUrl = mediaUrl,
			mediaThumbnailUrl = mediaThumbnailUrl,
			mediaMimeType = mediaMimeType,
			mediaSizeBytes = mediaSizeBytes,
			mediaDurationMs = mediaDurationMs,
			mediaWidth = mediaWidth,
			mediaHeight = mediaHeight,
			mediaWaveform = mediaWaveform,
			groupId = groupId,
			mediaPosition = mediaPosition,
			uploadStage = runCatching { DirectChatUploadStage.valueOf(uploadStage) }
				.getOrDefault(DirectChatUploadStage.QUEUED),
			idempotencyKey = idempotencyKey
		)
	}

	private fun toMedia(
		url: String?,
		objectKey: String?,
		mimeType: String?,
		sizeBytes: Long?,
		durationMs: Long?,
		localPath: String?,
		width: Int?,
		height: Int?,
		thumbnailUrl: String?,
		waveform: List<Int>? = null,
		groupId: String? = null,
		position: Int = 0,
	): DirectChatMessageMedia? {
		val normalizedLocalPath = localPath?.trim()?.takeIf(String::isNotEmpty)
		val normalizedUrl = url?.trim()?.takeIf(String::isNotEmpty)
		val normalizedObjectKey = objectKey?.trim()?.takeIf(String::isNotEmpty)
		val normalizedMimeType = mimeType?.trim()?.takeIf(String::isNotEmpty)
		val normalizedSizeBytes = sizeBytes?.takeIf { it > 0L }
		val normalizedDurationMs = durationMs?.takeIf { it > 0L }
		val normalizedThumbnailUrl = thumbnailUrl?.trim()?.takeIf(String::isNotEmpty)
		if (normalizedLocalPath == null && normalizedUrl == null) {
			return null
		}
		return DirectChatMessageMedia(
			url = normalizedUrl.orEmpty(),
			objectKey = normalizedObjectKey.orEmpty(),
			mimeType = normalizedMimeType ?: "application/octet-stream",
			sizeBytes = normalizedSizeBytes ?: 0L,
			durationMs = normalizedDurationMs ?: 0L,
			localPath = normalizedLocalPath,
			width = width?.takeIf { it > 0 },
			height = height?.takeIf { it > 0 },
			thumbnailUrl = normalizedThumbnailUrl,
			waveform = waveform,
			groupId = groupId,
			position = position,
		)
	}

	private fun now(): Long = currentTimeMillis()
}
