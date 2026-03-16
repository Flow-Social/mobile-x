package me.floow.domain.realtime

import me.floow.domain.models.DirectChatMessage
import me.floow.domain.models.DirectChatRealtimeEvent

enum class DirectChatUpsertKind {
	CREATED,
	UPDATED,
	PINNED_UPDATED
}

sealed interface DirectChatTimelineMutation {
	data object None : DirectChatTimelineMutation
	data class Upsert(val kind: DirectChatUpsertKind, val message: DirectChatMessage) : DirectChatTimelineMutation
	data class Delete(val messageId: Long) : DirectChatTimelineMutation
}

data class DirectChatRealtimeDecision(
	val cursorDecision: TimelineRealtimeEventDecision,
	val mutation: DirectChatTimelineMutation
)

fun reduceDirectChatRealtimeEvent(
	state: RealtimeViewCursorState,
	event: DirectChatRealtimeEvent,
	isLoading: Boolean
): DirectChatRealtimeDecision {
	val kind = when (event) {
		is DirectChatRealtimeEvent.Hello -> TimelineRealtimeEventKind.HELLO
		is DirectChatRealtimeEvent.ReadUpToUpdated -> TimelineRealtimeEventKind.READ_CURSOR_UPDATED
		is DirectChatRealtimeEvent.ResyncRequired -> TimelineRealtimeEventKind.RESYNC_REQUIRED
		is DirectChatRealtimeEvent.MessageCreated -> TimelineRealtimeEventKind.ENTITY_UPSERTED
		is DirectChatRealtimeEvent.MessageUpdated -> TimelineRealtimeEventKind.ENTITY_UPSERTED
		is DirectChatRealtimeEvent.MessagePinnedUpdated -> TimelineRealtimeEventKind.ENTITY_UPSERTED
		is DirectChatRealtimeEvent.MessageDeleted -> TimelineRealtimeEventKind.ENTITY_DELETED
		is DirectChatRealtimeEvent.Typing -> TimelineRealtimeEventKind.TRANSIENT
	}

	val decision = reduceTimelineRealtimeEvent(
		state = state,
		input = TimelineRealtimeEventInput(
			kind = kind,
			incomingReadCursor = event.lastReadMessageId(),
			eventSeq = event.seq(),
			eventMaxCursor = event.maxMessageId(),
			isLoading = isLoading
		)
	)

	val mutation = when (event) {
		is DirectChatRealtimeEvent.MessageCreated -> DirectChatTimelineMutation.Upsert(
			kind = DirectChatUpsertKind.CREATED,
			message = event.message
		)
		is DirectChatRealtimeEvent.MessageUpdated -> DirectChatTimelineMutation.Upsert(
			kind = DirectChatUpsertKind.UPDATED,
			message = event.message
		)
		is DirectChatRealtimeEvent.MessagePinnedUpdated -> DirectChatTimelineMutation.Upsert(
			kind = DirectChatUpsertKind.PINNED_UPDATED,
			message = event.message
		)
		is DirectChatRealtimeEvent.MessageDeleted -> DirectChatTimelineMutation.Delete(event.deletedMessageId)
		else -> DirectChatTimelineMutation.None
	}

	return DirectChatRealtimeDecision(
		cursorDecision = decision,
		mutation = mutation
	)
}

private fun DirectChatRealtimeEvent.seq(): Long {
	return when (this) {
		is DirectChatRealtimeEvent.Hello -> seq
		is DirectChatRealtimeEvent.MessageCreated -> seq
		is DirectChatRealtimeEvent.ReadUpToUpdated -> seq
		is DirectChatRealtimeEvent.MessageDeleted -> seq
		is DirectChatRealtimeEvent.MessageUpdated -> seq
		is DirectChatRealtimeEvent.MessagePinnedUpdated -> seq
		is DirectChatRealtimeEvent.Typing -> seq
		is DirectChatRealtimeEvent.ResyncRequired -> seq
	}
}

private fun DirectChatRealtimeEvent.lastReadMessageId(): Long {
	return when (this) {
		is DirectChatRealtimeEvent.Hello -> lastReadMessageId
		is DirectChatRealtimeEvent.MessageCreated -> lastReadMessageId
		is DirectChatRealtimeEvent.ReadUpToUpdated -> lastReadMessageId
		is DirectChatRealtimeEvent.MessageDeleted -> lastReadMessageId
		is DirectChatRealtimeEvent.MessageUpdated -> lastReadMessageId
		is DirectChatRealtimeEvent.MessagePinnedUpdated -> lastReadMessageId
		is DirectChatRealtimeEvent.Typing -> lastReadMessageId
		is DirectChatRealtimeEvent.ResyncRequired -> lastReadMessageId
	}
}

private fun DirectChatRealtimeEvent.maxMessageId(): Long {
	return when (this) {
		is DirectChatRealtimeEvent.Hello -> maxMessageId
		is DirectChatRealtimeEvent.MessageCreated -> maxMessageId
		is DirectChatRealtimeEvent.ReadUpToUpdated -> maxMessageId
		is DirectChatRealtimeEvent.MessageDeleted -> maxMessageId
		is DirectChatRealtimeEvent.MessageUpdated -> maxMessageId
		is DirectChatRealtimeEvent.MessagePinnedUpdated -> maxMessageId
		is DirectChatRealtimeEvent.Typing -> maxMessageId
		is DirectChatRealtimeEvent.ResyncRequired -> maxMessageId
	}
}
