package me.floow.domain.realtime

import me.floow.domain.models.Comment
import me.floow.domain.models.CommentRealtimeEvent

sealed interface CommentTimelineMutation {
	data object None : CommentTimelineMutation
	data class Upsert(val comment: Comment) : CommentTimelineMutation
	data class Delete(val commentId: String) : CommentTimelineMutation
}

data class CommentRealtimeDecision(
	val cursorDecision: TimelineRealtimeEventDecision,
	val mutation: CommentTimelineMutation
)

fun reduceCommentRealtimeEvent(
	state: RealtimeViewCursorState,
	event: CommentRealtimeEvent,
	isLoading: Boolean
): CommentRealtimeDecision {
	val (kind, incomingReadCursor) = when (event) {
		is CommentRealtimeEvent.Hello -> TimelineRealtimeEventKind.HELLO to event.lastReadSeq
		is CommentRealtimeEvent.ReadUpToUpdated -> TimelineRealtimeEventKind.READ_CURSOR_UPDATED to event.readUpToSeq
		is CommentRealtimeEvent.ResyncRequired -> TimelineRealtimeEventKind.RESYNC_REQUIRED to null
		is CommentRealtimeEvent.CommentCreated -> TimelineRealtimeEventKind.ENTITY_UPSERTED to null
		is CommentRealtimeEvent.CommentUpdated -> TimelineRealtimeEventKind.ENTITY_UPSERTED to null
		is CommentRealtimeEvent.CommentDeleted -> TimelineRealtimeEventKind.ENTITY_DELETED to null
	}

	val decision = reduceTimelineRealtimeEvent(
		state = state,
		input = TimelineRealtimeEventInput(
			kind = kind,
			incomingReadCursor = incomingReadCursor,
			eventSeq = event.seq,
			eventMaxCursor = event.maxSeq,
			isLoading = isLoading
		)
	)

	val mutation = when (event) {
		is CommentRealtimeEvent.CommentCreated -> CommentTimelineMutation.Upsert(event.comment)
		is CommentRealtimeEvent.CommentUpdated -> CommentTimelineMutation.Upsert(event.comment)
		is CommentRealtimeEvent.CommentDeleted -> CommentTimelineMutation.Delete(event.deletedCommentId)
		else -> CommentTimelineMutation.None
	}

	return CommentRealtimeDecision(
		cursorDecision = decision,
		mutation = mutation
	)
}
