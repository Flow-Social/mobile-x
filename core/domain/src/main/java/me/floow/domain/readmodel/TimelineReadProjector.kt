package me.floow.domain.readmodel

enum class TimelineReadOpenMode {
	FROM_UNREAD,
	FROM_LAST_SEEN,
	FROM_MESSAGE_LINK
}

data class TimelineReadItem(
	val messageId: Long,
	val cursor: Long,
	val isIncoming: Boolean
)

data class TimelineReadProjectorInput(
	val items: List<TimelineReadItem>,
	val serverReadUpToCursor: Long,
	val localReadUpToCursor: Long,
	val firstUnreadCursor: Long?,
	val storedOpenAnchorCursor: Long?,
	val resolvedOpenAnchorCursor: Long?,
	val openMode: TimelineReadOpenMode,
	val messageLinkAnchorCursor: Long?,
	val preferCeilOpenAnchor: Boolean,
	val fallbackToOldestOpenAnchor: Boolean
)

data class TimelineReadProjection(
	val unreadMessageIds: Set<Long>,
	val unreadBoundaryMessageId: Long?,
	val openAnchorMessageId: Long?,
	val openAnchorCursor: Long?,
	val readUpToCursor: Long
)

fun projectTimelineReadModel(input: TimelineReadProjectorInput): TimelineReadProjection {
	val normalizedItems = input.items
		.asSequence()
		.filter { item -> item.messageId > 0L && item.cursor > 0L }
		.distinctBy(TimelineReadItem::messageId)
		.toList()
	val cursorByMessageId = normalizedItems.associate { item -> item.messageId to item.cursor }

	val normalizedServerReadUpTo = input.serverReadUpToCursor.coerceAtLeast(0L)
	val normalizedLocalReadUpTo = input.localReadUpToCursor.coerceAtLeast(0L)
	val effectiveReadUpTo = maxOf(normalizedServerReadUpTo, normalizedLocalReadUpTo)

	val firstUnreadCursor = input.firstUnreadCursor
		?.takeIf { cursor -> cursor > effectiveReadUpTo }
		?: normalizedItems
			.asSequence()
			.filter(TimelineReadItem::isIncoming)
			.map(TimelineReadItem::cursor)
			.filter { cursor -> cursor > effectiveReadUpTo }
			.minOrNull()

	val unreadMessageIds = normalizedItems
		.asSequence()
		.filter(TimelineReadItem::isIncoming)
		.filter { item -> item.cursor > effectiveReadUpTo }
		.mapTo(linkedSetOf(), TimelineReadItem::messageId)

	val preferredOpenAnchorCursor = input.resolvedOpenAnchorCursor?.takeIf { cursor -> cursor > 0L }
		?: resolveOpenAnchorCursor(
			openMode = input.openMode,
			firstUnreadCursor = firstUnreadCursor,
			storedOpenAnchorCursor = input.storedOpenAnchorCursor?.takeIf { cursor -> cursor > 0L },
			serverReadCursor = normalizedServerReadUpTo.takeIf { cursor -> cursor > 0L },
			messageLinkAnchorCursor = input.messageLinkAnchorCursor?.takeIf { cursor -> cursor > 0L }
		)

	val openAnchorMessageId = resolveAnchorMessageIdByCursor(
		targetCursor = preferredOpenAnchorCursor,
		cursorByMessageId = cursorByMessageId,
		preferCeil = input.preferCeilOpenAnchor,
		fallbackToOldest = input.fallbackToOldestOpenAnchor
	)
	val unreadBoundaryMessageId = resolveAnchorMessageIdByCursor(
		targetCursor = firstUnreadCursor,
		cursorByMessageId = cursorByMessageId,
		preferCeil = true,
		fallbackToOldest = false
	)
	val resolvedOpenAnchorCursor = openAnchorMessageId
		?.let(cursorByMessageId::get)
		?: preferredOpenAnchorCursor

	return TimelineReadProjection(
		unreadMessageIds = unreadMessageIds,
		unreadBoundaryMessageId = unreadBoundaryMessageId,
		openAnchorMessageId = openAnchorMessageId,
		openAnchorCursor = resolvedOpenAnchorCursor,
		readUpToCursor = effectiveReadUpTo
	)
}

fun resolveAnchorMessageIdByCursor(
	targetCursor: Long?,
	cursorByMessageId: Map<Long, Long>,
	preferCeil: Boolean,
	fallbackToOldest: Boolean
): Long? {
	if (cursorByMessageId.isEmpty()) return null
	val normalizedTarget = targetCursor?.takeIf { cursor -> cursor > 0L }
	if (normalizedTarget != null) {
		val exactMatch = cursorByMessageId
			.entries
			.firstOrNull { (_, cursor) -> cursor == normalizedTarget }
			?.key
		if (exactMatch != null) return exactMatch

		val floorCursor = cursorByMessageId
			.values
			.filter { cursor -> cursor <= normalizedTarget }
			.maxOrNull()
		val ceilCursor = cursorByMessageId
			.values
			.filter { cursor -> cursor >= normalizedTarget }
			.minOrNull()
		val preferredCursor = if (preferCeil) {
			ceilCursor ?: floorCursor
		} else {
			floorCursor ?: ceilCursor
		}
		if (preferredCursor != null) {
			return cursorByMessageId.entries.firstOrNull { (_, cursor) -> cursor == preferredCursor }?.key
		}
	}

	if (!fallbackToOldest) return null
	val oldestCursor = cursorByMessageId.values.minOrNull() ?: return null
	return cursorByMessageId.entries.firstOrNull { (_, cursor) -> cursor == oldestCursor }?.key
}

fun resolveOpenAnchorCursor(
	openMode: TimelineReadOpenMode,
	firstUnreadCursor: Long?,
	storedOpenAnchorCursor: Long?,
	serverReadCursor: Long?,
	messageLinkAnchorCursor: Long?
): Long? {
	return when (openMode) {
		TimelineReadOpenMode.FROM_UNREAD -> {
			firstUnreadCursor ?: storedOpenAnchorCursor ?: serverReadCursor
		}
		TimelineReadOpenMode.FROM_LAST_SEEN -> {
			storedOpenAnchorCursor ?: firstUnreadCursor ?: serverReadCursor
		}
		TimelineReadOpenMode.FROM_MESSAGE_LINK -> {
			messageLinkAnchorCursor ?: firstUnreadCursor ?: storedOpenAnchorCursor ?: serverReadCursor
		}
	}
}

fun removeReadMessagesUpToCursor(
	messageIds: Set<Long>,
	cursorByMessageId: Map<Long, Long>,
	readUpToCursor: Long
): Set<Long> {
	if (messageIds.isEmpty()) return messageIds
	return messageIds.filterTo(linkedSetOf()) { messageId ->
		val cursor = cursorByMessageId[messageId] ?: Long.MAX_VALUE
		cursor > readUpToCursor
	}
}

fun resolveMaxVisibleUnreadCursor(
	visibleMessageIds: Set<Long>,
	unreadMessageIds: Set<Long>,
	cursorByMessageId: Map<Long, Long>
): Long {
	var maxCursor = 0L
	visibleMessageIds.forEach { messageId ->
		if (!unreadMessageIds.contains(messageId)) return@forEach
		val cursor = cursorByMessageId[messageId] ?: return@forEach
		if (cursor > maxCursor) {
			maxCursor = cursor
		}
	}
	return maxCursor
}

fun shouldApplyVisibleReadCandidate(
	candidateCursor: Long,
	lastAppliedVisibleCursor: Long
): Boolean {
	return candidateCursor > 0L && candidateCursor > lastAppliedVisibleCursor
}

fun shouldEnqueueReadCursor(
	readCursor: Long,
	lastAppliedReadCursor: Long
): Boolean {
	return readCursor > 0L && readCursor > lastAppliedReadCursor
}

fun mergePendingReadCursor(
	currentPendingCursor: Long?,
	newCursor: Long
): Long {
	if (newCursor <= 0L) return currentPendingCursor ?: 0L
	return maxOf(currentPendingCursor ?: 0L, newCursor)
}
