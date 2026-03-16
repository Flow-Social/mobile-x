package me.floow.chats.uilogic.shared

import me.floow.domain.readmodel.mergePendingReadCursor as mergePendingReadCursorDomain
import me.floow.domain.readmodel.removeReadMessagesUpToCursor as removeReadMessagesUpToCursorDomain
import me.floow.domain.readmodel.resolveAnchorMessageIdByCursor as resolveAnchorMessageIdByCursorDomain
import me.floow.domain.readmodel.resolveMaxVisibleUnreadCursor as resolveMaxVisibleUnreadCursorDomain
import me.floow.domain.readmodel.resolveOpenAnchorCursor as resolveOpenAnchorCursorDomain
import me.floow.domain.readmodel.shouldApplyVisibleReadCandidate as shouldApplyVisibleReadCandidateDomain
import me.floow.domain.readmodel.shouldEnqueueReadCursor as shouldEnqueueReadCursorDomain
import me.floow.domain.readmodel.TimelineReadOpenMode

internal fun resolveAnchorMessageIdByCursor(
	targetCursor: Long?,
	cursorByMessageId: Map<Long, Long>,
	preferCeil: Boolean,
	fallbackToOldest: Boolean
): Long? {
	return resolveAnchorMessageIdByCursorDomain(
		targetCursor = targetCursor,
		cursorByMessageId = cursorByMessageId,
		preferCeil = preferCeil,
		fallbackToOldest = fallbackToOldest
	)
}

internal fun resolveOpenAnchorCursor(
	openMode: UnifiedOpenMode,
	firstUnreadCursor: Long?,
	storedOpenAnchorCursor: Long?,
	serverReadCursor: Long?,
	messageLinkAnchorCursor: Long?
): Long? {
	return resolveOpenAnchorCursorDomain(
		openMode = openMode.toTimelineReadOpenMode(),
		firstUnreadCursor = firstUnreadCursor,
		storedOpenAnchorCursor = storedOpenAnchorCursor,
		serverReadCursor = serverReadCursor,
		messageLinkAnchorCursor = messageLinkAnchorCursor
	)
}

internal fun removeReadMessagesUpToCursor(
	messageIds: Set<Long>,
	cursorByMessageId: Map<Long, Long>,
	readUpToCursor: Long
): Set<Long> {
	return removeReadMessagesUpToCursorDomain(
		messageIds = messageIds,
		cursorByMessageId = cursorByMessageId,
		readUpToCursor = readUpToCursor
	)
}

internal fun resolveMaxVisibleUnreadCursor(
	visibleMessageIds: Set<Long>,
	unreadMessageIds: Set<Long>,
	cursorByMessageId: Map<Long, Long>
): Long {
	return resolveMaxVisibleUnreadCursorDomain(
		visibleMessageIds = visibleMessageIds,
		unreadMessageIds = unreadMessageIds,
		cursorByMessageId = cursorByMessageId
	)
}

internal fun shouldApplyVisibleReadCandidate(
	candidateCursor: Long,
	lastAppliedVisibleCursor: Long
): Boolean {
	return shouldApplyVisibleReadCandidateDomain(candidateCursor, lastAppliedVisibleCursor)
}

internal fun shouldEnqueueReadCursor(
	readCursor: Long,
	lastAppliedReadCursor: Long
): Boolean {
	return shouldEnqueueReadCursorDomain(readCursor, lastAppliedReadCursor)
}

internal fun mergePendingReadCursor(
	currentPendingCursor: Long?,
	newCursor: Long
): Long {
	return mergePendingReadCursorDomain(currentPendingCursor, newCursor)
}

enum class UnifiedOpenMode {
	FROM_UNREAD,
	FROM_LAST_SEEN,
	FROM_MESSAGE_LINK
}

internal fun UnifiedOpenMode.toTimelineReadOpenMode(): TimelineReadOpenMode {
	return when (this) {
		UnifiedOpenMode.FROM_UNREAD -> TimelineReadOpenMode.FROM_UNREAD
		UnifiedOpenMode.FROM_LAST_SEEN -> TimelineReadOpenMode.FROM_LAST_SEEN
		UnifiedOpenMode.FROM_MESSAGE_LINK -> TimelineReadOpenMode.FROM_MESSAGE_LINK
	}
}
