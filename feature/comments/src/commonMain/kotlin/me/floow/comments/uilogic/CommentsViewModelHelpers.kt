package me.floow.comments.uilogic

import me.floow.domain.models.Comment
import me.floow.domain.models.CommentAuthor
import me.floow.domain.models.CommentId
import me.floow.domain.models.CommentReply
import me.floow.domain.models.PostContent
import me.floow.domain.models.resolvedImageVariants
import me.floow.domain.models.resolveCommentTargetCandidates as resolveTargetCandidates
import me.floow.domain.realtime.shouldHandleRealtimeResync
import me.floow.domain.readmodel.TimelineReadItem
import me.floow.domain.readmodel.TimelineReadOpenMode
import me.floow.domain.readmodel.TimelineReadProjectorInput
import me.floow.domain.readmodel.TimelineReadProjection
import me.floow.domain.readmodel.projectTimelineReadModel
import me.floow.domain.utils.currentTimeMillis
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatPostImageVariant
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.ReplyOutMessage

private const val COMMENTS_ANCHOR_WINDOW_BEFORE = 20
private const val COMMENTS_ANCHOR_WINDOW_AFTER = 40
private const val INITIAL_TARGET_AUTOLOAD_MAX_PAGES = 200

internal data class AnchorWindowParams(
	val before: Int?,
	val after: Int?
)

internal data class InitialTargetSearchState(
	val candidates: List<Long> = emptyList(),
	val activeIndex: Int = 0,
	val loadAttempts: Int = 0,
	val isResolved: Boolean = true
) {
	val activeTarget: Long?
		get() = candidates.getOrNull(activeIndex)

	val hasFallback: Boolean
		get() = activeIndex < candidates.lastIndex
}

internal data class InitialTargetSearchSnapshot(
	val canLoadMore: Boolean,
	val isLoadingMore: Boolean,
	val isTargetPresent: Boolean
)

internal sealed interface InitialTargetSearchAction {
	data object NoOp : InitialTargetSearchAction
	data object LoadMore : InitialTargetSearchAction
	data class ReloadWithAnchor(val anchorCommentId: Long) : InitialTargetSearchAction
}

internal fun <T> mergeByStableId(
	existing: List<T>,
	incoming: List<T>,
	idSelector: (T) -> String
): List<T> {
	val mergedById = LinkedHashMap<String, T>(existing.size + incoming.size)
	existing.forEach { item ->
		mergedById[idSelector(item)] = item
	}
	incoming.forEach { item ->
		mergedById[idSelector(item)] = item
	}
	return mergedById.values.toList()
}

internal fun mergeCommentsById(existing: List<Comment>, incoming: List<Comment>): List<Comment> {
	return mergeByStableId(existing = existing, incoming = incoming, idSelector = { it.id })
}

internal fun buildInitialTargetCandidates(
	primaryTargetCommentId: CommentId?,
	fallbackTargetCommentId: CommentId?
): List<Long> {
	return resolveTargetCandidates(primaryTargetCommentId, fallbackTargetCommentId)
		.map(CommentId::value)
}

internal fun advanceInitialTargetSearch(
	state: InitialTargetSearchState,
	snapshot: InitialTargetSearchSnapshot
): Pair<InitialTargetSearchState, InitialTargetSearchAction> {
	if (state.isResolved) return state to InitialTargetSearchAction.NoOp
	if (snapshot.isTargetPresent) {
		return state.copy(isResolved = true) to InitialTargetSearchAction.NoOp
	}

	if (state.activeTarget == null) {
		return state.copy(isResolved = true) to InitialTargetSearchAction.NoOp
	}
	val canAutoPage = snapshot.canLoadMore && !snapshot.isLoadingMore
	if (canAutoPage && state.loadAttempts < INITIAL_TARGET_AUTOLOAD_MAX_PAGES) {
		return state.copy(loadAttempts = state.loadAttempts + 1) to InitialTargetSearchAction.LoadMore
	}

	if (!snapshot.canLoadMore && state.hasFallback) {
		val nextIndex = state.activeIndex + 1
		val nextTarget = state.candidates.getOrNull(nextIndex)
		if (nextTarget == null) {
			return state.copy(isResolved = true) to InitialTargetSearchAction.NoOp
		}
		return state.copy(
			activeIndex = nextIndex,
			loadAttempts = 0,
			isResolved = false
		) to InitialTargetSearchAction.ReloadWithAnchor(nextTarget)
	}

	if (!snapshot.canLoadMore || state.loadAttempts >= INITIAL_TARGET_AUTOLOAD_MAX_PAGES) {
		return state.copy(isResolved = true) to InitialTargetSearchAction.NoOp
	}

	return state to InitialTargetSearchAction.NoOp
}

internal fun resolveAnchorWindow(anchorCommentId: CommentId?): AnchorWindowParams {
	if (anchorCommentId == null) {
		return AnchorWindowParams(before = null, after = null)
	}
	return AnchorWindowParams(
		before = COMMENTS_ANCHOR_WINDOW_BEFORE,
		after = COMMENTS_ANCHOR_WINDOW_AFTER
	)
}

internal fun buildOptimisticComment(
	state: CommentsVmState,
	selfUserId: String?,
	temporaryCommentId: String,
	text: String,
	replyToId: Long?,
	createdAt: Long
): Comment {
	val reply = replyToId?.let { replyId ->
		state.comments.firstOrNull { comment -> comment.id == replyId.toString() }?.let { source ->
			CommentReply(
				id = source.id,
				text = source.text,
				authorId = source.author.id,
				authorName = source.author.name?.value
			)
		}
	}
	return Comment(
		id = temporaryCommentId,
		postId = state.postId,
		author = CommentAuthor(
			id = normalizeCommentSelfUserId(selfUserId).orEmpty(),
			name = null,
			username = null,
			avatarUrl = null
		),
		text = text,
		createdAt = createdAt,
		updatedAt = createdAt,
		replyTo = reply
	)
}

internal fun normalizeCommentSelfUserId(selfUserId: String?): String? {
	return selfUserId
		?.trim()
		?.takeIf(String::isNotEmpty)
}

internal fun Comment.isOwnedBy(
	selfUserId: String?,
	locallyOwnedCommentIds: Set<String> = emptySet()
): Boolean {
	if (locallyOwnedCommentIds.contains(id)) return true
	val normalizedSelfUserId = normalizeCommentSelfUserId(selfUserId) ?: return false
	val normalizedAuthorId = author.id.trim().takeIf(String::isNotEmpty) ?: return false
	return normalizedAuthorId == normalizedSelfUserId
}

internal fun Comment.resolveForeignAuthorId(
	selfUserId: String?,
	locallyOwnedCommentIds: Set<String> = emptySet()
): String? {
	val normalizedAuthorId = author.id.trim().takeIf(String::isNotEmpty) ?: return null
	return normalizedAuthorId.takeUnless { isOwnedBy(selfUserId, locallyOwnedCommentIds) }
}

internal fun CommentsVmState.buildPostPreviewMessage(): PostPreviewMessage? {
	val description = postDescription.orEmpty()
	val variants = PostContent(
		imageUrls = postImageUrls,
		description = postDescription,
		imageVariants = postImageVariants
	).resolvedImageVariants()
	if (description.isBlank() && variants.isEmpty()) return null
	val createdAtMillis = if (postCreatedAt > 0) postCreatedAt else currentTimeMillis()
	val messageId = ("post:" + postId).hashCode().toLong() * -1L
	return PostPreviewMessage(
		id = messageId,
		messageText = description,
		createdAtMillis = createdAtMillis,
		imageVariants = variants.map { variant ->
			ChatPostImageVariant(
				lqUrl = variant.lqUrl,
				previewUrl = variant.previewUrl,
				fullUrl = variant.fullUrl,
				width = variant.width,
				height = variant.height
			)
		},
		likesCount = postLikesCount,
		authorAvatarUrl = postAuthorAvatarUrl?.toString(),
		authorName = postAuthorName,
		authorUsername = postAuthorUsername
	)
}

internal fun Comment.toChatMessage(
	selfUserId: String?,
	messageIdByCommentId: Map<Long, Long>,
	locallyOwnedCommentIds: Set<String> = emptySet()
): ChatMessage {
	val isMine = isOwnedBy(
		selfUserId = selfUserId,
		locallyOwnedCommentIds = locallyOwnedCommentIds
	)
	val authorName = author.name?.value
	val authorUsername = author.username?.value
	val reply = replyTo
	val messageId = id.toLongOrNull() ?: createdAt
	return if (reply != null) {
		val replyId = reply.id.toLongOrNull()
			?.let { messageIdByCommentId[it] ?: it }
			?: reply.id.hashCode().toLong()
		if (isMine) {
			ReplyOutMessage(
				id = messageId,
				messageText = text,
				createdAtMillis = createdAt,
				replyMessageId = replyId,
				replyMessageText = reply.text,
				authorName = authorName,
				authorUsername = authorUsername,
				authorAvatarUrl = author.avatarUrl
			)
		} else {
			ReplyInMessage(
				id = messageId,
				replyMessageId = replyId,
				replyMessageText = reply.text,
				messageText = text,
				createdAtMillis = createdAt,
				authorName = authorName,
				authorUsername = authorUsername,
				authorAvatarUrl = author.avatarUrl
			)
		}
	} else {
		if (isMine) {
			PrimaryOutMessage(
				id = messageId,
				messageText = text,
				createdAtMillis = createdAt,
				authorName = authorName,
				authorUsername = authorUsername,
				authorAvatarUrl = author.avatarUrl
			)
		} else {
			PrimaryInMessage(
				id = messageId,
				messageText = text,
				createdAtMillis = createdAt,
				authorName = authorName,
				authorUsername = authorUsername,
				authorAvatarUrl = author.avatarUrl
			)
		}
	}
}

internal fun rebuildCommentMessageMaps(
	comments: List<Comment>
): Pair<Map<Long, Long>, Map<Long, Long>> {
	val commentIdByMessageId = LinkedHashMap<Long, Long>(comments.size)
	val messageIdByCommentId = LinkedHashMap<Long, Long>(comments.size)
	for (comment in comments) {
		val messageId = commentToMessageId(comment)
		val commentId = comment.id.toLongOrNull()
		if (commentId != null) {
			commentIdByMessageId[messageId] = commentId
			messageIdByCommentId[commentId] = messageId
		}
	}
	return commentIdByMessageId to messageIdByCommentId
}

internal fun commentToMessageId(comment: Comment): Long {
	return comment.id.toLongOrNull() ?: comment.createdAt
}

internal data class CommentsReadProjection(
	val projection: TimelineReadProjection,
	val cursorByMessageId: Map<Long, Long>
)

internal fun projectCommentsReadModel(
	comments: List<Comment>,
	selfUserId: String?,
	locallyOwnedCommentIds: Set<String> = emptySet(),
	serverReadUpToSeq: Long,
	localReadUpToSeq: Long
): CommentsReadProjection {
	val readItems = comments.map { comment ->
		TimelineReadItem(
			messageId = commentToMessageId(comment),
			cursor = comment.seq,
			isIncoming = !comment.isOwnedBy(
				selfUserId = selfUserId,
				locallyOwnedCommentIds = locallyOwnedCommentIds
			)
		)
	}
	val projection = projectTimelineReadModel(
		input = TimelineReadProjectorInput(
			items = readItems,
			serverReadUpToCursor = serverReadUpToSeq,
			localReadUpToCursor = localReadUpToSeq,
			firstUnreadCursor = null,
			storedOpenAnchorCursor = null,
			resolvedOpenAnchorCursor = null,
			openMode = TimelineReadOpenMode.FROM_UNREAD,
			messageLinkAnchorCursor = null,
			preferCeilOpenAnchor = true,
			fallbackToOldestOpenAnchor = false
		)
	)
	val cursorByMessageId = readItems
		.asSequence()
		.filter { item -> item.cursor > 0L }
		.associate { item -> item.messageId to item.cursor }
	return CommentsReadProjection(
		projection = projection,
		cursorByMessageId = cursorByMessageId
	)
}

internal fun applyRealtimeCommentUpsert(
	state: CommentsVmState,
	comment: Comment
): CommentsVmState {
	val mergedComments = mergeCommentsById(
		existing = state.comments,
		incoming = listOf(comment)
	)
	val orderState = appendMissingOrderForComments(
		state = CommentOrderState(
			orderById = state.commentOrderById,
			nextOrder = state.nextCommentOrder
		),
		commentIds = mergedComments.map(Comment::id)
	)
	val orderedComments = sortCommentsByOrder(
		comments = mergedComments,
		orderById = orderState.orderById,
		idSelector = Comment::id,
		createdAtSelector = Comment::createdAt
	)
	val (commentIdByMessageId, messageIdByCommentId) = rebuildCommentMessageMaps(orderedComments)
	return state.copy(
		comments = orderedComments,
		commentOrderById = orderState.orderById,
		nextCommentOrder = orderState.nextOrder,
		commentIdByMessageId = commentIdByMessageId,
		messageIdByCommentId = messageIdByCommentId
	)
}

internal fun applyRealtimeCommentDelete(
	state: CommentsVmState,
	deletedCommentId: String
): CommentsVmState {
	val filtered = state.comments.filterNot { comment -> comment.id == deletedCommentId }
	val orderState = removeOrderForComment(
		state = CommentOrderState(
			orderById = state.commentOrderById,
			nextOrder = state.nextCommentOrder
		),
		commentId = deletedCommentId
	)
	val orderedComments = sortCommentsByOrder(
		comments = filtered,
		orderById = orderState.orderById,
		idSelector = Comment::id,
		createdAtSelector = Comment::createdAt
	)
	val (commentIdByMessageId, messageIdByCommentId) = rebuildCommentMessageMaps(orderedComments)
	return state.copy(
		comments = orderedComments,
		commentOrderById = orderState.orderById,
		nextCommentOrder = orderState.nextOrder,
		commentIdByMessageId = commentIdByMessageId,
		messageIdByCommentId = messageIdByCommentId
	)
}

internal fun applyRealtimeReadUpTo(
	state: CommentsVmState,
	selfUserId: String?,
	locallyOwnedCommentIds: Set<String> = emptySet(),
	readUpToSeq: Long
): CommentsVmState {
	if (readUpToSeq <= 0L) return state
	val readProjection = projectCommentsReadModel(
		comments = state.comments,
		selfUserId = selfUserId,
		locallyOwnedCommentIds = locallyOwnedCommentIds,
		serverReadUpToSeq = readUpToSeq,
		localReadUpToSeq = readUpToSeq
	)
	val unreadMessageIds = readProjection.projection.unreadMessageIds
	val updatedComments = state.comments.map { comment ->
		val messageId = commentToMessageId(comment)
		val shouldBeRead = comment.isOwnedBy(
			selfUserId = selfUserId,
			locallyOwnedCommentIds = locallyOwnedCommentIds
		) || !unreadMessageIds.contains(messageId)
		if (comment.isRead == shouldBeRead) {
			comment
		} else {
			comment.copy(isRead = shouldBeRead)
		}
	}
	return state.copy(comments = updatedComments)
}

internal fun shouldReloadCommentsOnResync(
	isLoading: Boolean,
	eventSeq: Long,
	eventMaxSeq: Long,
	lastHandledResyncSeq: Long
): Boolean {
	return shouldHandleRealtimeResync(
		isLoading = isLoading,
		eventSeq = eventSeq,
		eventMaxCursor = eventMaxSeq,
		lastHandledResyncCursor = lastHandledResyncSeq
	)
}
