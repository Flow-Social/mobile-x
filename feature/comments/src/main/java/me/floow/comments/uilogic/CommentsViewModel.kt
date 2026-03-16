package me.floow.comments.uilogic

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.models.CommentId
import me.floow.domain.models.resolveCommentTargetCandidates as resolveTargetCandidates
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.CommentsReadCursorStore
import me.floow.domain.data.repos.CommentsRepository
import me.floow.domain.models.Comment
import me.floow.domain.models.CommentRealtimeEvent
import me.floow.domain.models.PostImageVariant
import me.floow.domain.realtime.CommentTimelineMutation
import me.floow.domain.realtime.reduceCommentRealtimeEvent
import me.floow.domain.realtime.mergeRealtimeCursor
import me.floow.domain.realtime.RealtimeViewCursorState
import me.floow.domain.readmodel.resolveMaxVisibleUnreadCursor
import me.floow.domain.utils.Logger
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatHighlightRequest
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatScrollRequest
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.MessageFieldReply
import me.floow.uikit.chat.model.DEFAULT_CHAT_MESSAGE_MAX_LENGTH
import me.floow.uikit.chat.model.isWithinCodePointLimit
import me.floow.uikit.chat.model.resolveDefaultContextMenuActions
import me.floow.uikit.chat.model.trimToCodePointLimit

private const val COMMENTS_PAGE_SIZE = 30
private const val UNDO_DELETE_TIMEOUT_MS = 4000L
private const val COMMENT_SEND_TAG = "CommentsViewModel.sendComment"

internal data class CommentsVmState(
	val postId: String = "",
	val postAuthorId: String = "",
	val postAuthorName: String = "",
	val defaultTitle: String = "",
	val postAuthorAvatarUrl: Uri? = null,
	val postAuthorUsername: String? = null,
	val postImageUrls: List<String> = emptyList(),
	val postImageVariants: List<PostImageVariant> = emptyList(),
	val postDescription: String? = null,
	val postCreatedAt: Long = 0L,
	val postLikesCount: Int = 0,
	val isLoading: Boolean = false,
	val isError: Boolean = false,
	val comments: List<Comment> = emptyList(),
	val commentOrderById: Map<String, Long> = emptyMap(),
	val nextCommentOrder: Long = 0L,
	val commentIdByMessageId: Map<Long, Long> = emptyMap(),
	val messageIdByCommentId: Map<Long, Long> = emptyMap(),
	val nextCursor: String? = null,
	val isLoadingMore: Boolean = false,
	val messageFieldValue: String = "",
	val messageFieldReply: MessageFieldReply? = null,
	val highlightedMessageId: Long? = null,
	val messageToEditId: Long? = null,
	val scrollToBottomRequestToken: Long = 0L
) {
	fun toUiState(groupedMessages: List<DatedChatMessages>): ChatScreenUiState {
		val title = postAuthorName.ifBlank { defaultTitle.ifBlank { "Comments" } }
		return when {
			isLoading && comments.isEmpty() -> {
				ChatScreenUiState.Loading(
					chatInterlocutorId = postAuthorId,
					chatInterlocutorAvatarUrl = postAuthorAvatarUrl,
					messageFieldValue = messageFieldValue,
					chatInterlocutorName = title,
					messageFieldReply = messageFieldReply,
					peerIsOnline = false,
					peerLastSeenAtMillis = null
				)
			}

			isError && comments.isEmpty() -> {
				ChatScreenUiState.Error(
					chatInterlocutorId = postAuthorId,
					chatInterlocutorAvatarUrl = postAuthorAvatarUrl,
					messageFieldValue = messageFieldValue,
					chatInterlocutorName = title,
					messageFieldReply = messageFieldReply,
					peerIsOnline = false,
					peerLastSeenAtMillis = null
				)
			}

			else -> {
				if (groupedMessages.isEmpty()) {
					return ChatScreenUiState.NoMessages(
						chatInterlocutorId = postAuthorId,
						chatInterlocutorAvatarUrl = postAuthorAvatarUrl,
						messageFieldValue = messageFieldValue,
						chatInterlocutorName = title,
						messageFieldReply = messageFieldReply,
						peerIsOnline = false,
						peerLastSeenAtMillis = null
					)
				}

				ChatScreenUiState.HasData(
					chatInterlocutorId = postAuthorId,
					chatInterlocutorName = title,
					chatInterlocutorAvatarUrl = postAuthorAvatarUrl,
					messageFieldValue = messageFieldValue,
					messages = groupedMessages,
					messageFieldReply = messageFieldReply,
					highlightRequest = highlightedMessageId?.let { messageId ->
						ChatHighlightRequest(messageId = messageId, requestToken = messageId)
					},
					typingUserNames = emptyList(),
					pinnedMessages = emptyList(),
					messageToEditId = messageToEditId,
					scrollRequest = scrollToBottomRequestToken
						.takeIf { token -> token > 0L }
						?.let(::ChatScrollRequest),
					scrollToBottomRequestToken = scrollToBottomRequestToken,
					canLoadMore = nextCursor != null,
					isLoadingMore = isLoadingMore
				)
			}
		}
	}
}

class CommentsViewModel(
	private val commentsRepository: CommentsRepository,
	private val commentsReadCursorStore: CommentsReadCursorStore,
	private val authenticationManager: AuthenticationManager,
	private val logger: Logger
) : ViewModel() {
	fun resolveContextMenuActions(message: ChatMessage): List<ChatContextMenuAction> {
		return message.resolveDefaultContextMenuActions()
	}

	private val _state = MutableStateFlow(CommentsVmState())
	private val _isInitialTargetResolved = MutableStateFlow(true)
	private var initialTargetSearchState = InitialTargetSearchState()
	private var pendingDelete: Comment? = null
	private var pendingDeleteOrder: Long? = null
	private var pendingDeleteJob: Job? = null
	private var realtimeJob: Job? = null
	private var lastRealtimeSeq: Long = 0L
	private var lastHandledResyncSeq: Long = 0L
	private var pendingReadUpToSeq: Long? = null
	private var lastQueuedReadUpToSeq: Long = 0L
	private var enqueuePendingReadJob: Job? = null
	private var initialTargetStartedAtMs: Long? = null
	private var initialTargetResolutionLogged: Boolean = false
	private var nextOptimisticCommentIdValue: Long = 0L
	private var cachedGroupedSelfUserId: String? = null
	private var cachedGroupedCommentsRef: List<Comment>? = null
	private var cachedGroupedMessageIdByCommentRef: Map<Long, Long>? = null
	private var cachedGroupedPostImageUrlsRef: List<String>? = null
	private var cachedGroupedPostImageVariantsRef: List<PostImageVariant>? = null
	private var cachedGroupedPostDescription: String? = null
	private var cachedGroupedPostAuthorName: String = ""
	private var cachedGroupedPostAuthorAvatarUrl: Uri? = null
	private var cachedGroupedPostAuthorUsername: String? = null
	private var cachedGroupedPostCreatedAt: Long = Long.MIN_VALUE
	private var cachedGroupedPostLikesCount: Int = Int.MIN_VALUE
	private var cachedGroupedMessages: List<DatedChatMessages> = emptyList()

	val state: StateFlow<ChatScreenUiState> = _state
		.map { vmState ->
			val selfUserId = authenticationManager.getSelfUserIdOrNull()
			val groupedMessages = computeGroupedMessages(vmState, selfUserId)
			vmState.toUiState(groupedMessages)
		}
		.flowOn(Dispatchers.Default)
		.stateIn(
			viewModelScope,
			SharingStarted.Eagerly,
			ChatScreenUiState.Loading(
				chatInterlocutorId = "",
				chatInterlocutorAvatarUrl = null,
				messageFieldValue = "",
				chatInterlocutorName = "",
				messageFieldReply = null,
				peerIsOnline = false,
				peerLastSeenAtMillis = null
			)
		)

	val isInitialTargetResolved: StateFlow<Boolean> = _isInitialTargetResolved.asStateFlow()

	private fun computeGroupedMessages(
		state: CommentsVmState,
		selfUserId: String?
	): List<DatedChatMessages> {
		val shouldRebuild = cachedGroupedSelfUserId != selfUserId ||
			cachedGroupedCommentsRef !== state.comments ||
			cachedGroupedMessageIdByCommentRef !== state.messageIdByCommentId ||
			cachedGroupedPostImageUrlsRef !== state.postImageUrls ||
			cachedGroupedPostImageVariantsRef !== state.postImageVariants ||
			cachedGroupedPostDescription != state.postDescription ||
			cachedGroupedPostAuthorName != state.postAuthorName ||
			cachedGroupedPostAuthorAvatarUrl != state.postAuthorAvatarUrl ||
			cachedGroupedPostAuthorUsername != state.postAuthorUsername ||
			cachedGroupedPostCreatedAt != state.postCreatedAt ||
			cachedGroupedPostLikesCount != state.postLikesCount
		if (!shouldRebuild) return cachedGroupedMessages

		val baseMessages = state.comments
			.map { it.toChatMessage(selfUserId, state.messageIdByCommentId) }
		val postMessage = state.buildPostPreviewMessage()
		val messages = if (postMessage != null) {
			listOf(postMessage) + baseMessages
		} else {
			baseMessages
		}

		val rebuiltGroups = messages
			.groupBy { it.dateTime.toLocalDate() }
			.map { (date, list) ->
				DatedChatMessages(datetime = date, messages = list)
			}
			.sortedBy { it.datetime }

		cachedGroupedSelfUserId = selfUserId
		cachedGroupedCommentsRef = state.comments
		cachedGroupedMessageIdByCommentRef = state.messageIdByCommentId
		cachedGroupedPostImageUrlsRef = state.postImageUrls
		cachedGroupedPostImageVariantsRef = state.postImageVariants
		cachedGroupedPostDescription = state.postDescription
		cachedGroupedPostAuthorName = state.postAuthorName
		cachedGroupedPostAuthorAvatarUrl = state.postAuthorAvatarUrl
		cachedGroupedPostAuthorUsername = state.postAuthorUsername
		cachedGroupedPostCreatedAt = state.postCreatedAt
		cachedGroupedPostLikesCount = state.postLikesCount
		cachedGroupedMessages = rebuiltGroups
		return rebuiltGroups
	}

	fun setInitialData(
		postId: String,
		postAuthorId: String,
		postAuthorName: String,
		postAuthorAvatarUrl: Uri?,
		postAuthorUsername: String?,
		postImageUrls: List<String>,
		postImageVariants: List<PostImageVariant>,
		postDescription: String?,
		postCreatedAt: Long,
		postLikesCount: Int,
		defaultTitle: String
	) {
		_state.update {
			it.copy(
				postId = postId,
				postAuthorId = postAuthorId,
				postAuthorName = postAuthorName,
				defaultTitle = defaultTitle,
				postAuthorAvatarUrl = postAuthorAvatarUrl,
				postAuthorUsername = postAuthorUsername,
				postImageUrls = postImageUrls,
				postImageVariants = postImageVariants,
				postDescription = postDescription,
				postCreatedAt = postCreatedAt,
				postLikesCount = postLikesCount,
				comments = emptyList(),
				commentOrderById = emptyMap(),
				nextCommentOrder = 0L,
				commentIdByMessageId = emptyMap(),
				messageIdByCommentId = emptyMap(),
				nextCursor = null,
				isLoading = false,
				isError = false,
				isLoadingMore = false,
				messageFieldValue = "",
				messageFieldReply = null,
				messageToEditId = null,
				highlightedMessageId = null
			)
		}
		pendingDeleteJob?.cancel()
		pendingDeleteJob = null
		pendingDelete = null
		pendingDeleteOrder = null
		realtimeJob?.cancel()
		realtimeJob = null
		lastRealtimeSeq = 0L
		lastHandledResyncSeq = 0L
		initialTargetSearchState = InitialTargetSearchState()
		_isInitialTargetResolved.value = true
		initialTargetStartedAtMs = null
		initialTargetResolutionLogged = false
		pendingReadUpToSeq = null
		lastQueuedReadUpToSeq = 0L
		enqueuePendingReadJob?.cancel()
		enqueuePendingReadJob = null
		resetGroupedMessagesCache()
		viewModelScope.launch {
			lastQueuedReadUpToSeq = commentsReadCursorStore.getLocalLastReadSeq(postId)
		}
	}

	fun startInitialLoad(
		primaryTargetCommentId: CommentId?,
		fallbackTargetCommentId: CommentId?
	) {
		val candidates = buildInitialTargetCandidates(
			primaryTargetCommentId = primaryTargetCommentId,
			fallbackTargetCommentId = fallbackTargetCommentId
		)
		val initialState = InitialTargetSearchState(
			candidates = candidates,
			activeIndex = 0,
			loadAttempts = 0,
			isResolved = candidates.isEmpty()
		)
		initialTargetSearchState = initialState
		initialTargetStartedAtMs = if (candidates.isNotEmpty()) System.currentTimeMillis() else null
		initialTargetResolutionLogged = false
		_isInitialTargetResolved.value = initialState.isResolved
		loadInitial(anchorCommentId = initialState.activeTarget?.let(::CommentId))
	}

	fun onInitialTargetSearchStateChanged() {
		val currentSearchState = initialTargetSearchState
		if (currentSearchState.isResolved) return
		val targetCommentIdValue = currentSearchState.activeTarget ?: run {
			resolveInitialTargetSearch()
			return
		}
		val targetCommentId = CommentId(targetCommentIdValue)
		val currentState = _state.value
		val isAwaitingFirstLoadResult = currentState.comments.isEmpty() &&
			currentState.nextCursor.isNullOrBlank() &&
			!currentState.isLoading &&
			!currentState.isLoadingMore &&
			!currentState.isError
		if (isAwaitingFirstLoadResult) return

		if (tryJumpToCommentId(targetCommentId)) {
			reportInitialTargetResolution(found = true)
			resolveInitialTargetSearch()
			return
		}

		val snapshot = InitialTargetSearchSnapshot(
			canLoadMore = !currentState.nextCursor.isNullOrBlank(),
			isLoadingMore = currentState.isLoading || currentState.isLoadingMore,
			isTargetPresent = false
		)
		val (nextState, action) = advanceInitialTargetSearch(
			state = currentSearchState,
			snapshot = snapshot
		)
		if (nextState != currentSearchState) {
			initialTargetSearchState = nextState
			_isInitialTargetResolved.value = nextState.isResolved
		}
		when (action) {
			InitialTargetSearchAction.NoOp -> Unit
			InitialTargetSearchAction.LoadMore -> loadMore()
			is InitialTargetSearchAction.ReloadWithAnchor -> {
				loadInitial(anchorCommentId = CommentId(action.anchorCommentId))
			}
		}
	}

	fun loadInitial(anchorCommentId: CommentId? = null) {
		val current = _state.value
		if (current.postId.isBlank()) return
		if (current.isLoading) return
		val anchorWindow = resolveAnchorWindow(anchorCommentId)

		_state.update { it.copy(isLoading = true, isError = false) }
		viewModelScope.launch {
			when (
				val result = if (anchorCommentId != null) {
					val contextResult = commentsRepository.getCommentsContext(
						postId = current.postId,
						targetCommentId = anchorCommentId.value,
						anchorBefore = anchorWindow.before,
						anchorAfter = anchorWindow.after
					)
					if (contextResult is GetDataResponse.Success) {
						contextResult
					} else {
						commentsRepository.getComments(
							postId = current.postId,
							cursor = null,
							limit = COMMENTS_PAGE_SIZE,
							anchorCommentId = anchorCommentId.value,
							anchorBefore = anchorWindow.before,
							anchorAfter = anchorWindow.after
						)
					}
				} else {
					commentsRepository.getComments(
						postId = current.postId,
						cursor = null,
						limit = COMMENTS_PAGE_SIZE,
						anchorCommentId = null,
						anchorBefore = null,
						anchorAfter = null
					)
				}
			) {
				is GetDataResponse.Success -> {
					lastQueuedReadUpToSeq = maxOf(lastQueuedReadUpToSeq, result.data.lastReadSeq)
					lastRealtimeSeq = maxOf(lastRealtimeSeq, result.data.maxSeq)
					val orderState = createInitialCommentOrder(result.data.items.map { it.id })
					val orderedComments = sortCommentsByOrder(
						comments = result.data.items,
						orderById = orderState.orderById,
						idSelector = { it.id },
						createdAtSelector = { it.createdAt }
					)
					val (commentIdByMessageId, messageIdByCommentId) = rebuildCommentMessageMaps(orderedComments)
					_state.update {
						it.copy(
							isLoading = false,
							isError = false,
							comments = orderedComments,
							commentOrderById = orderState.orderById,
							nextCommentOrder = orderState.nextOrder,
							commentIdByMessageId = commentIdByMessageId,
							messageIdByCommentId = messageIdByCommentId,
							nextCursor = result.data.nextCursor
						)
					}
					startRealtimeSubscriptionIfNeeded(
						postId = current.postId,
						afterSeq = lastRealtimeSeq
					)
				}

				is GetDataResponse.Error -> {
					logger.d("CommentsViewModel.loadInitial", "Failed to load comments")
					_state.update { it.copy(isLoading = false, isError = true) }
				}
			}
		}
	}

	private fun resetGroupedMessagesCache() {
		cachedGroupedSelfUserId = null
		cachedGroupedCommentsRef = null
		cachedGroupedMessageIdByCommentRef = null
		cachedGroupedPostImageUrlsRef = null
		cachedGroupedPostImageVariantsRef = null
		cachedGroupedPostDescription = null
		cachedGroupedPostAuthorName = ""
		cachedGroupedPostAuthorAvatarUrl = null
		cachedGroupedPostAuthorUsername = null
		cachedGroupedPostCreatedAt = Long.MIN_VALUE
		cachedGroupedPostLikesCount = Int.MIN_VALUE
		cachedGroupedMessages = emptyList()
	}

	fun loadMore() {
		val current = _state.value
		if (current.postId.isBlank()) return
		if (current.isLoadingMore || current.nextCursor.isNullOrBlank()) return

		_state.update { it.copy(isLoadingMore = true) }
		viewModelScope.launch {
			when (val result = commentsRepository.getComments(current.postId, current.nextCursor, COMMENTS_PAGE_SIZE)) {
				is GetDataResponse.Success -> {
					lastQueuedReadUpToSeq = maxOf(lastQueuedReadUpToSeq, result.data.lastReadSeq)
					lastRealtimeSeq = maxOf(lastRealtimeSeq, result.data.maxSeq)
					_state.update { state ->
						val merged = mergeCommentsById(
							existing = state.comments,
							incoming = result.data.items
						)

						val orderState = appendMissingOrderForComments(
							state = CommentOrderState(
								orderById = state.commentOrderById,
								nextOrder = state.nextCommentOrder
							),
							commentIds = merged.map { it.id }
						)
						val orderedComments = sortCommentsByOrder(
							comments = merged,
							orderById = orderState.orderById,
							idSelector = { it.id },
							createdAtSelector = { it.createdAt }
						)
						val (commentIdByMessageId, messageIdByCommentId) = rebuildCommentMessageMaps(orderedComments)
						state.copy(
							comments = orderedComments,
							commentOrderById = orderState.orderById,
							nextCommentOrder = orderState.nextOrder,
							commentIdByMessageId = commentIdByMessageId,
							messageIdByCommentId = messageIdByCommentId,
							nextCursor = result.data.nextCursor,
							isLoadingMore = false
						)
					}
					startRealtimeSubscriptionIfNeeded(
						postId = current.postId,
						afterSeq = lastRealtimeSeq
					)
				}

				is GetDataResponse.Error -> {
					logger.d("CommentsViewModel.loadMore", "Failed to load more comments")
					_state.update { it.copy(isLoadingMore = false) }
				}
			}
		}
	}

	fun updateMessageInputField(newValue: String) {
		val trimmed = newValue.trimToCodePointLimit(DEFAULT_CHAT_MESSAGE_MAX_LENGTH)
		_state.update { it.copy(messageFieldValue = trimmed) }
	}

	fun closeCurrentReply() {
		_state.update { it.copy(messageFieldReply = null) }
	}

	fun addCurrentReply(chatMessage: ChatMessage) {
		val authorLabel = chatMessage.authorName
			?: chatMessage.authorUsername
			?: ""

		_state.update {
			it.copy(
				messageFieldReply = MessageFieldReply(
					replyId = chatMessage.id,
					replyAuthorName = authorLabel,
					replyMessageText = chatMessage.messageText
				)
			)
		}
	}

	fun sendComment() {
		val current = _state.value
		val text = current.messageFieldValue
		if (text.isBlank()) return
		if (!text.isWithinCodePointLimit(DEFAULT_CHAT_MESSAGE_MAX_LENGTH)) return
		if (current.postId.isBlank()) return

		val now = System.currentTimeMillis()
		val initialReply = current.messageFieldReply
		val replyToId = initialReply?.replyId?.let { current.commentIdByMessageId[it] }
		val optimisticCommentId = nextOptimisticCommentId(now)
		val optimisticComment = buildOptimisticComment(
			state = current,
			selfUserId = authenticationManager.getSelfUserIdOrNull().orEmpty(),
			temporaryCommentId = optimisticCommentId,
			text = text,
			replyToId = replyToId,
			createdAt = now
		)

		viewModelScope.launch {
			_state.update { state ->
				val orderState = allocateOrderForComment(
					state = CommentOrderState(
						orderById = state.commentOrderById,
						nextOrder = state.nextCommentOrder
					),
					commentId = optimisticComment.id
				)
				val updatedComments = (state.comments + optimisticComment).distinctBy { comment -> comment.id }
				val orderedComments = sortCommentsByOrder(
					comments = updatedComments,
					orderById = orderState.orderById,
					idSelector = { it.id },
					createdAtSelector = { it.createdAt }
				)
				val (commentIdByMessageId, messageIdByCommentId) = rebuildCommentMessageMaps(orderedComments)
				state.copy(
					comments = orderedComments,
					commentOrderById = orderState.orderById,
					nextCommentOrder = orderState.nextOrder,
					commentIdByMessageId = commentIdByMessageId,
					messageIdByCommentId = messageIdByCommentId,
					messageFieldValue = "",
					messageFieldReply = null,
					scrollToBottomRequestToken = now
				)
			}

			when (val result = commentsRepository.createComment(current.postId, text, replyToId)) {
				is GetDataResponse.Success -> {
					_state.update { state ->
						val replacedComments = state.comments
							.map { comment ->
								if (comment.id == optimisticComment.id) result.data else comment
							}
							.distinctBy { comment -> comment.id }

						val orderState = transferOrderBetweenComments(
							state = CommentOrderState(
								orderById = state.commentOrderById,
								nextOrder = state.nextCommentOrder
							),
							fromCommentId = optimisticComment.id,
							toCommentId = result.data.id
						)
						val updatedComments = sortCommentsByOrder(
							comments = replacedComments,
							orderById = orderState.orderById,
							idSelector = { it.id },
							createdAtSelector = { it.createdAt }
						)
						val (commentIdByMessageId, messageIdByCommentId) = rebuildCommentMessageMaps(updatedComments)
						state.copy(
							comments = updatedComments,
							commentOrderById = orderState.orderById,
							nextCommentOrder = orderState.nextOrder,
							commentIdByMessageId = commentIdByMessageId,
							messageIdByCommentId = messageIdByCommentId,
							scrollToBottomRequestToken = System.currentTimeMillis()
						)
					}
				}

				is GetDataResponse.Error -> {
					logger.d(COMMENT_SEND_TAG, "Failed to create comment")
					_state.update { state ->
						val restoredComments = state.comments
							.filterNot { comment -> comment.id == optimisticComment.id }
						val orderState = removeOrderForComment(
							state = CommentOrderState(
								orderById = state.commentOrderById,
								nextOrder = state.nextCommentOrder
							),
							commentId = optimisticComment.id
						)
						val orderedComments = sortCommentsByOrder(
							comments = restoredComments,
							orderById = orderState.orderById,
							idSelector = { it.id },
							createdAtSelector = { it.createdAt }
						)
						val (commentIdByMessageId, messageIdByCommentId) = rebuildCommentMessageMaps(orderedComments)
						state.copy(
							comments = orderedComments,
							commentOrderById = orderState.orderById,
							nextCommentOrder = orderState.nextOrder,
							commentIdByMessageId = commentIdByMessageId,
							messageIdByCommentId = messageIdByCommentId,
							messageFieldValue = state.messageFieldValue.ifBlank { text },
							messageFieldReply = state.messageFieldReply ?: initialReply,
						)
					}
				}
			}
		}
	}

	fun startEditingComment(messageId: Long, currentText: String) {
		_state.update {
			it.copy(
				messageToEditId = messageId,
				messageFieldValue = currentText,
				messageFieldReply = null
			)
		}
	}

	fun cancelEditing() {
		_state.update {
			it.copy(
				messageToEditId = null,
				messageFieldValue = "",
				messageFieldReply = null
			)
		}
	}

	fun editComment(messageId: Long, newText: String) {
		val updatedText = newText
		if (updatedText.isBlank()) return
		if (!updatedText.isWithinCodePointLimit(DEFAULT_CHAT_MESSAGE_MAX_LENGTH)) return

		val commentId = messageId.toString()
		viewModelScope.launch {
			when (commentsRepository.updateComment(commentId, updatedText)) {
				UpdateDataResponse.Success -> {
					_state.update { state ->
						val updated = state.comments.map { comment ->
							if (comment.id == commentId) {
								comment.copy(text = updatedText, updatedAt = System.currentTimeMillis())
							} else {
								comment
							}
						}
						val (commentIdByMessageId, messageIdByCommentId) = rebuildCommentMessageMaps(updated)
						state.copy(
							comments = updated,
							commentIdByMessageId = commentIdByMessageId,
							messageIdByCommentId = messageIdByCommentId,
							messageToEditId = null,
							messageFieldValue = "",
							messageFieldReply = null
						)
					}
				}

				is UpdateDataResponse.Failure -> {
					logger.d("CommentsViewModel.editComment", "Failed to update comment")
				}
			}
		}
	}

	fun deleteComment(messageId: Long) {
		val comment = _state.value.comments.firstOrNull { it.id.toLongOrNull() == messageId } ?: return
		finalizePendingDelete()
		pendingDelete = comment
		pendingDeleteOrder = _state.value.commentOrderById[comment.id]

		_state.update { state ->
			val updated = state.comments.filterNot { it.id == comment.id }
			val orderState = removeOrderForComment(
				state = CommentOrderState(
					orderById = state.commentOrderById,
					nextOrder = state.nextCommentOrder
				),
				commentId = comment.id
			)
			val orderedComments = sortCommentsByOrder(
				comments = updated,
				orderById = orderState.orderById,
				idSelector = { it.id },
				createdAtSelector = { it.createdAt }
			)
			val (commentIdByMessageId, messageIdByCommentId) = rebuildCommentMessageMaps(orderedComments)
			state.copy(
				comments = orderedComments,
				commentOrderById = orderState.orderById,
				nextCommentOrder = orderState.nextOrder,
				commentIdByMessageId = commentIdByMessageId,
				messageIdByCommentId = messageIdByCommentId
			)
		}

		pendingDeleteJob?.cancel()
		pendingDeleteJob = viewModelScope.launch {
			delay(UNDO_DELETE_TIMEOUT_MS)
			val deleteResult = commentsRepository.deleteComment(comment.id)
			if (deleteResult is UpdateDataResponse.Failure) {
				restorePendingDelete()
			}
			pendingDelete = null
			pendingDeleteOrder = null
		}
	}

	fun undoDelete() {
		pendingDeleteJob?.cancel()
		pendingDeleteJob = null
		restorePendingDelete()
	}

	fun jumpToComment(messageId: Long) {
		_state.update { it.copy(highlightedMessageId = messageId) }
		viewModelScope.launch {
			delay(2500)
			_state.update { state ->
				if (state.highlightedMessageId == messageId) {
					state.copy(highlightedMessageId = null)
				} else {
					state
				}
			}
		}
	}

	fun tryJumpToCommentId(commentId: CommentId): Boolean {
		val messageId = _state.value.messageIdByCommentId[commentId.value] ?: return false
		jumpToComment(messageId)
		return true
	}

	fun requestScrollToBottom() {
		_state.update { it.copy(scrollToBottomRequestToken = System.currentTimeMillis()) }
	}

	fun onVisibleMessageIdsChanged(visibleMessageIds: Set<Long>) {
		if (visibleMessageIds.isEmpty()) return
		val current = _state.value
		if (current.postId.isBlank()) return
		val selfUserId = authenticationManager.getSelfUserIdOrNull()
		val readProjection = projectCommentsReadModel(
			comments = current.comments,
			selfUserId = selfUserId,
			serverReadUpToSeq = lastQueuedReadUpToSeq,
			localReadUpToSeq = lastQueuedReadUpToSeq
		)
		val readUpToSeq = resolveMaxVisibleUnreadCursor(
			visibleMessageIds = visibleMessageIds,
			unreadMessageIds = readProjection.projection.unreadMessageIds,
			cursorByMessageId = readProjection.cursorByMessageId
		)
		if (readUpToSeq <= 0L) return

		enqueueReadUpToSeq(current.postId, readUpToSeq)
	}

	fun getAuthorIdByMessageId(messageId: Long): String? {
		return _state.value.comments
			.firstOrNull { commentToMessageId(it) == messageId }
			?.author
			?.id
	}

	fun getForeignAuthorIdByMessageId(messageId: Long): String? {
		val selfUserId = authenticationManager.getSelfUserIdOrNull()
		return getAuthorIdByMessageId(messageId)
			?.takeIf { authorId -> authorId.isNotBlank() && authorId != selfUserId }
	}

	private fun startRealtimeSubscriptionIfNeeded(postId: String, afterSeq: Long) {
		if (postId.isBlank()) return
		if (realtimeJob?.isActive == true) return
		lastRealtimeSeq = maxOf(lastRealtimeSeq, afterSeq.coerceAtLeast(0L))
		realtimeJob = viewModelScope.launch {
			commentsRepository.subscribePostComments(
				postId = postId,
				afterSeq = lastRealtimeSeq
			).collect { event ->
				if (_state.value.postId != postId) {
					return@collect
				}
				lastRealtimeSeq = mergeRealtimeCursor(
					currentCursor = lastRealtimeSeq,
					eventSeq = event.seq,
					eventMaxCursor = event.maxSeq
				)
				applyRealtimeEvent(event)
			}
		}
	}

	private fun applyRealtimeEvent(event: CommentRealtimeEvent) {
		val decision = reduceCommentRealtimeEvent(
			state = RealtimeViewCursorState(
				readCursor = lastQueuedReadUpToSeq,
				lastHandledResyncCursor = lastHandledResyncSeq
			),
			event = event,
			isLoading = _state.value.isLoading
		)
		lastQueuedReadUpToSeq = decision.cursorDecision.nextCursorState.readCursor
		lastHandledResyncSeq = decision.cursorDecision.nextCursorState.lastHandledResyncCursor

		if (decision.cursorDecision.shouldApplyReadProjection) {
			val selfUserId = authenticationManager.getSelfUserIdOrNull()
			_state.update { state ->
				applyRealtimeReadUpTo(
					state = state,
					selfUserId = selfUserId,
					readUpToSeq = decision.cursorDecision.nextCursorState.readCursor
				)
			}
		}

		when (val mutation = decision.mutation) {
			is CommentTimelineMutation.Upsert -> {
				_state.update { state ->
					applyRealtimeCommentUpsert(state = state, comment = mutation.comment)
				}
			}
			is CommentTimelineMutation.Delete -> {
				if (pendingDelete?.id == mutation.commentId) {
					pendingDeleteJob?.cancel()
					pendingDeleteJob = null
					pendingDelete = null
					pendingDeleteOrder = null
				}
				_state.update { state ->
					applyRealtimeCommentDelete(
						state = state,
						deletedCommentId = mutation.commentId
					)
				}
			}
			CommentTimelineMutation.None -> Unit
		}

		if (decision.cursorDecision.shouldHandleResync) {
			loadInitial()
		}
	}

	private fun resolveInitialTargetSearch() {
		reportInitialTargetResolution(found = false)
		initialTargetSearchState = initialTargetSearchState.copy(isResolved = true)
		_isInitialTargetResolved.value = true
	}

	private fun finalizePendingDelete() {
		val comment = pendingDelete ?: return
		pendingDeleteJob?.cancel()
		pendingDeleteJob = null
		pendingDelete = null
		pendingDeleteOrder = null
		viewModelScope.launch {
			commentsRepository.deleteComment(comment.id)
		}
	}

	private fun enqueueReadUpToSeq(postId: String, readUpToSeq: Long) {
		if (postId.isBlank() || readUpToSeq <= 0L) return
		if (readUpToSeq <= lastQueuedReadUpToSeq) return
		pendingReadUpToSeq = maxOf(pendingReadUpToSeq ?: 0L, readUpToSeq)
		enqueuePendingReadJob?.cancel()
		enqueuePendingReadJob = viewModelScope.launch {
			delay(350L)
			flushPendingReadToQueue(postId)
		}
	}

	private suspend fun flushPendingReadToQueue(postId: String) {
		val seqToFlush = pendingReadUpToSeq ?: return
		pendingReadUpToSeq = null
		if (seqToFlush <= lastQueuedReadUpToSeq) return
		lastQueuedReadUpToSeq = seqToFlush
		commentsReadCursorStore.enqueueReadUpTo(postId = postId, readUpToSeq = seqToFlush)
	}

	private fun reportInitialTargetResolution(found: Boolean) {
		if (initialTargetResolutionLogged) return
		val startedAt = initialTargetStartedAtMs ?: return
		initialTargetResolutionLogged = true
		val latencyMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L)
		if (found) {
			logger.d("CommentsMetrics.open_to_anchor_ms", latencyMs.toString())
		} else {
			logger.d("CommentsMetrics.anchor_miss_rate", "1")
			logger.d("CommentsMetrics.open_to_anchor_ms", latencyMs.toString())
		}
	}

	private fun restorePendingDelete() {
		val comment = pendingDelete ?: return
		_state.update { state ->
			val restored = (state.comments + comment).distinctBy { it.id }
			val restoredOrder = pendingDeleteOrder
			val orderById = buildMap {
				putAll(state.commentOrderById)
				if (restoredOrder != null) {
					put(comment.id, restoredOrder)
				}
			}
			val orderState = if (restoredOrder != null) {
				CommentOrderState(orderById = orderById, nextOrder = state.nextCommentOrder)
			} else {
				allocateOrderForComment(
					state = CommentOrderState(
						orderById = state.commentOrderById,
						nextOrder = state.nextCommentOrder
					),
					commentId = comment.id
				)
			}
			val orderedComments = sortCommentsByOrder(
				comments = restored,
				orderById = orderState.orderById,
				idSelector = { it.id },
				createdAtSelector = { it.createdAt }
			)
			val (commentIdByMessageId, messageIdByCommentId) = rebuildCommentMessageMaps(orderedComments)
			state.copy(
				comments = orderedComments,
				commentOrderById = orderState.orderById,
				nextCommentOrder = orderState.nextOrder,
				commentIdByMessageId = commentIdByMessageId,
				messageIdByCommentId = messageIdByCommentId
			)
		}
		pendingDelete = null
		pendingDeleteOrder = null
	}

	private fun nextOptimisticCommentId(nowMs: Long): String {
		val baseValue = nowMs * 1000
		nextOptimisticCommentIdValue = maxOf(nextOptimisticCommentIdValue + 1, baseValue)
		return nextOptimisticCommentIdValue.toString()
	}

	override fun onCleared() {
		realtimeJob?.cancel()
		realtimeJob = null
		super.onCleared()
	}

}
