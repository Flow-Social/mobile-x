package me.floow.comments.uilogic

internal data class CommentOrderState(
	val orderById: Map<String, Long> = emptyMap(),
	val nextOrder: Long = 0L
)

internal fun createInitialCommentOrder(commentIds: List<String>): CommentOrderState {
	if (commentIds.isEmpty()) return CommentOrderState()
	var next = 0L
	val orderById = LinkedHashMap<String, Long>(commentIds.size)
	for (commentId in commentIds) {
		if (orderById.containsKey(commentId)) continue
		orderById[commentId] = next
		next++
	}
	return CommentOrderState(orderById = orderById, nextOrder = next)
}

internal fun allocateOrderForComment(state: CommentOrderState, commentId: String): CommentOrderState {
	if (state.orderById.containsKey(commentId)) return state
	return state.copy(
		orderById = state.orderById + (commentId to state.nextOrder),
		nextOrder = state.nextOrder + 1
	)
}

internal fun removeOrderForComment(state: CommentOrderState, commentId: String): CommentOrderState {
	if (!state.orderById.containsKey(commentId)) return state
	return state.copy(orderById = state.orderById - commentId)
}

internal fun transferOrderBetweenComments(
	state: CommentOrderState,
	fromCommentId: String,
	toCommentId: String
): CommentOrderState {
	if (fromCommentId == toCommentId) return state
	val fromOrder = state.orderById[fromCommentId]
	val withoutFrom = state.orderById - fromCommentId
	if (fromOrder == null) {
		return allocateOrderForComment(state.copy(orderById = withoutFrom), toCommentId)
	}
	return state.copy(orderById = withoutFrom + (toCommentId to fromOrder))
}

internal fun appendMissingOrderForComments(
	state: CommentOrderState,
	commentIds: List<String>
): CommentOrderState {
	var current = state
	for (commentId in commentIds) {
		current = allocateOrderForComment(current, commentId)
	}
	return current
}

internal fun <T> sortCommentsByOrder(
	comments: List<T>,
	orderById: Map<String, Long>,
	idSelector: (T) -> String,
	createdAtSelector: (T) -> Long
): List<T> {
	return comments
		.sortedWith(
			compareBy<T> { orderById[idSelector(it)] ?: Long.MAX_VALUE }
				.thenBy { createdAtSelector(it) }
				.thenBy { idSelector(it).toLongOrNull() ?: Long.MAX_VALUE }
		)
}
