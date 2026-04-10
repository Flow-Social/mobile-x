package me.floow.comments.uilogic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.auth.models.AuthState
import me.floow.domain.auth.models.PendingRegistrationInitialData
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.CommentsReadCursorStore
import me.floow.domain.data.repos.CommentsRepository
import me.floow.domain.models.Comment
import me.floow.domain.models.CommentAuthor
import me.floow.domain.models.CommentReply
import me.floow.domain.models.CommentRealtimeEvent
import me.floow.domain.models.CommentsPage
import me.floow.domain.utils.Logger
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyInMessage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommentsOwnershipTest {

	@Test
	fun `loaded comments flip to mine after self user id restore`() = runBlocking {
		val authManager = TestAuthenticationManager(
			signedIn = true,
			selfUserId = null
		)
		val stateHolder = CommentsStateHolder(
			commentsRepository = TestCommentsRepository(
				initialComments = listOf(comment(id = "1", authorId = "self"))
			),
			commentsReadCursorStore = TestCommentsReadCursorStore(),
			authenticationManager = authManager,
			logger = TestLogger(),
			scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
		)

		stateHolder.setInitialData(
			postId = "post_1",
			postAuthorId = "author_1",
			postAuthorName = "Author",
			postAuthorAvatarUrl = null,
			postAuthorUsername = null,
			postImageUrls = emptyList(),
			postImageVariants = emptyList(),
			postDescription = null,
			postCreatedAt = 0L,
			postLikesCount = 0,
			defaultTitle = "Comments"
		)
		stateHolder.loadInitial(anchorCommentId = null)
		delay(150L)

		assertTrue(firstRenderedMessage(stateHolder) is PrimaryInMessage)

		authManager.saveSelfUserId("self")
		delay(350L)

		assertTrue(firstRenderedMessage(stateHolder) is PrimaryOutMessage)
		stateHolder.dispose()
	}

	@Test
	fun `locally owned optimistic comments stay outgoing without fake self id`() {
		val optimisticComment = buildOptimisticComment(
			state = CommentsVmState(postId = "post_1"),
			selfUserId = null,
			temporaryCommentId = "temp_1",
			text = "Hello",
			replyToId = null,
			createdAt = 42L
		)

		assertEquals("", optimisticComment.author.id)
		assertTrue(
			optimisticComment.toChatMessage(
				selfUserId = null,
				messageIdByCommentId = emptyMap(),
				locallyOwnedCommentIds = setOf(optimisticComment.id)
			) is PrimaryOutMessage
		)
		assertNull(
			optimisticComment.resolveForeignAuthorId(
				selfUserId = null,
				locallyOwnedCommentIds = setOf(optimisticComment.id)
			)
		)
	}

	@Test
	fun `optimistic comments use a separate id namespace from server reply targets`() = runBlocking {
		val targetComment = comment(
			id = "1700000000000",
			authorId = "author_1",
			text = "Target"
		)
		val replyComment = comment(
			id = "1700000000001",
			authorId = "author_2",
			text = "Reply",
			replyTo = targetComment
		)
		val createDeferred = CompletableDeferred<GetDataResponse<Comment>>()
		val stateHolder = CommentsStateHolder(
			commentsRepository = DeferredCreateCommentsRepository(
				initialComments = listOf(targetComment, replyComment),
				createDeferred = createDeferred
			),
			commentsReadCursorStore = TestCommentsReadCursorStore(),
			authenticationManager = TestAuthenticationManager(
				signedIn = true,
				selfUserId = "self"
			),
			logger = TestLogger(),
			scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
		)

		stateHolder.setInitialData(
			postId = "post_1",
			postAuthorId = "author_1",
			postAuthorName = "Author",
			postAuthorAvatarUrl = null,
			postAuthorUsername = null,
			postImageUrls = emptyList(),
			postImageVariants = emptyList(),
			postDescription = null,
			postCreatedAt = 0L,
			postLikesCount = 0,
			defaultTitle = "Comments"
		)
		stateHolder.loadInitial(anchorCommentId = null)
		delay(150L)
		stateHolder.updateMessageInputField("Mine")
		stateHolder.sendComment()
		delay(150L)

		val renderedMessages = renderedMessages(stateHolder)
		val optimisticMessage = renderedMessages
			.filterIsInstance<PrimaryOutMessage>()
			.first { it.messageText == "Mine" }
		val replyMessage = renderedMessages
			.filterIsInstance<ReplyInMessage>()
			.first { it.messageText == "Reply" }

		assertTrue(optimisticMessage.id < 0L)
		assertFalse(renderedMessages.map { it.id }.groupingBy { it }.eachCount().values.any { it > 1 })
		assertEquals(targetComment.id.toLong(), replyMessage.replyMessageId)

		createDeferred.complete(GetDataResponse.Success(comment(id = "3", authorId = "self", text = "Mine")))
		delay(150L)
		stateHolder.dispose()
	}

	@Test
	fun `comments context menu excludes pin actions and keeps owner actions`() {
		val stateHolder = CommentsStateHolder(
			commentsRepository = TestCommentsRepository(initialComments = emptyList()),
			commentsReadCursorStore = TestCommentsReadCursorStore(),
			authenticationManager = TestAuthenticationManager(
				signedIn = true,
				selfUserId = "self"
			),
			logger = TestLogger(),
			scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
		)

		val ownActions = stateHolder.resolveContextMenuActions(
			PrimaryOutMessage(
				id = 1L,
				messageText = "hello",
				createdAtMillis = 1L
			)
		)
		val foreignActions = stateHolder.resolveContextMenuActions(
			PrimaryInMessage(
				id = 2L,
				messageText = "hello",
				createdAtMillis = 1L
			)
		)

		assertFalse(ChatContextMenuAction.Pin in ownActions)
		assertFalse(ChatContextMenuAction.Unpin in ownActions)
		assertTrue(ChatContextMenuAction.Edit in ownActions)
		assertTrue(ChatContextMenuAction.Delete in ownActions)
		assertFalse(ChatContextMenuAction.Pin in foreignActions)
		assertFalse(ChatContextMenuAction.Unpin in foreignActions)
		assertFalse(ChatContextMenuAction.Edit in foreignActions)
		assertFalse(ChatContextMenuAction.Delete in foreignActions)

		stateHolder.dispose()
	}

	@Test
	fun `edit comment routes repository update through comment id mapping`() = runBlocking {
		val repository = RecordingCommentsRepository(
			initialComments = listOf(comment(id = "1700000000000", authorId = "self", text = "Before"))
		)
		val stateHolder = CommentsStateHolder(
			commentsRepository = repository,
			commentsReadCursorStore = TestCommentsReadCursorStore(),
			authenticationManager = TestAuthenticationManager(
				signedIn = true,
				selfUserId = "self"
			),
			logger = TestLogger(),
			scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
		)

		stateHolder.setInitialData(
			postId = "post_1",
			postAuthorId = "author_1",
			postAuthorName = "Author",
			postAuthorAvatarUrl = null,
			postAuthorUsername = null,
			postImageUrls = emptyList(),
			postImageVariants = emptyList(),
			postDescription = null,
			postCreatedAt = 0L,
			postLikesCount = 0,
			defaultTitle = "Comments"
		)
		stateHolder.loadInitial(anchorCommentId = null)
		delay(150L)

		val messageId = renderedMessages(stateHolder).single().id
		assertEquals(1700000000000L, messageId)

		stateHolder.startEditingComment(messageId, "Before")
		stateHolder.editComment(messageId, "After")
		delay(150L)

		assertEquals("1700000000000" to "After", repository.lastUpdateRequest)
		assertEquals("After", renderedMessages(stateHolder).single().messageText)

		stateHolder.dispose()
	}
}

private fun firstRenderedMessage(stateHolder: CommentsStateHolder) =
	((stateHolder.state.value as ChatScreenUiState.HasData).messages.single().messages.single())

private fun renderedMessages(stateHolder: CommentsStateHolder) =
	(stateHolder.state.value as ChatScreenUiState.HasData).messages.flatMap { it.messages }

private fun comment(
	id: String,
	authorId: String,
	text: String = "Hello",
	replyTo: Comment? = null
) = Comment(
	id = id,
	postId = "post_1",
	author = CommentAuthor(
		id = authorId,
		name = null,
		username = null,
		avatarUrl = null
	),
	text = text,
	createdAt = 1L,
	updatedAt = 1L,
	replyTo = replyTo?.let { source ->
		CommentReply(
			id = source.id,
			text = source.text,
			authorId = source.author.id,
			authorName = source.author.name?.value
		)
	}
)

private class TestCommentsRepository(
	private val initialComments: List<Comment>
) : CommentsRepository {
	override suspend fun getComments(
		postId: String,
		cursor: String?,
		limit: Int,
		anchorCommentId: Long?,
		anchorBefore: Int?,
		anchorAfter: Int?
	): GetDataResponse<CommentsPage> {
		return GetDataResponse.Success(
			CommentsPage(
				items = initialComments,
				nextCursor = null
			)
		)
	}

	override suspend fun getCommentsContext(
		postId: String,
		targetCommentId: Long,
		anchorBefore: Int?,
		anchorAfter: Int?
	): GetDataResponse<CommentsPage> = getComments(postId, null, 30, targetCommentId, anchorBefore, anchorAfter)

	override suspend fun createComment(postId: String, text: String, replyToId: Long?): GetDataResponse<Comment> {
		return GetDataResponse.Success(comment(id = "2", authorId = "self", text = text))
	}

	override suspend fun updateComment(commentId: String, text: String): UpdateDataResponse =
		UpdateDataResponse.Success

	override suspend fun deleteComment(commentId: String): UpdateDataResponse =
		UpdateDataResponse.Success

	override suspend fun markCommentsReadUpTo(postId: String, readUpToSeq: Long): GetDataResponse<Long> =
		GetDataResponse.Success(readUpToSeq)

	override fun subscribePostComments(
		postId: String,
		afterSeq: Long,
		replayLimit: Int
	) = emptyFlow<CommentRealtimeEvent>()
}

private class DeferredCreateCommentsRepository(
	private val initialComments: List<Comment>,
	private val createDeferred: CompletableDeferred<GetDataResponse<Comment>>
) : CommentsRepository {
	override suspend fun getComments(
		postId: String,
		cursor: String?,
		limit: Int,
		anchorCommentId: Long?,
		anchorBefore: Int?,
		anchorAfter: Int?
	): GetDataResponse<CommentsPage> {
		return GetDataResponse.Success(
			CommentsPage(
				items = initialComments,
				nextCursor = null
			)
		)
	}

	override suspend fun getCommentsContext(
		postId: String,
		targetCommentId: Long,
		anchorBefore: Int?,
		anchorAfter: Int?
	): GetDataResponse<CommentsPage> = getComments(postId, null, 30, targetCommentId, anchorBefore, anchorAfter)

	override suspend fun createComment(postId: String, text: String, replyToId: Long?): GetDataResponse<Comment> =
		createDeferred.await()

	override suspend fun updateComment(commentId: String, text: String): UpdateDataResponse =
		UpdateDataResponse.Success

	override suspend fun deleteComment(commentId: String): UpdateDataResponse =
		UpdateDataResponse.Success

	override suspend fun markCommentsReadUpTo(postId: String, readUpToSeq: Long): GetDataResponse<Long> =
		GetDataResponse.Success(readUpToSeq)

	override fun subscribePostComments(
		postId: String,
		afterSeq: Long,
		replayLimit: Int
	) = emptyFlow<CommentRealtimeEvent>()
}

private class RecordingCommentsRepository(
	private val initialComments: List<Comment>
) : CommentsRepository {
	var lastUpdateRequest: Pair<String, String>? = null

	override suspend fun getComments(
		postId: String,
		cursor: String?,
		limit: Int,
		anchorCommentId: Long?,
		anchorBefore: Int?,
		anchorAfter: Int?
	): GetDataResponse<CommentsPage> {
		return GetDataResponse.Success(
			CommentsPage(
				items = initialComments,
				nextCursor = null
			)
		)
	}

	override suspend fun getCommentsContext(
		postId: String,
		targetCommentId: Long,
		anchorBefore: Int?,
		anchorAfter: Int?
	): GetDataResponse<CommentsPage> = getComments(postId, null, 30, targetCommentId, anchorBefore, anchorAfter)

	override suspend fun createComment(postId: String, text: String, replyToId: Long?): GetDataResponse<Comment> {
		return GetDataResponse.Success(comment(id = "2", authorId = "self", text = text))
	}

	override suspend fun updateComment(commentId: String, text: String): UpdateDataResponse {
		lastUpdateRequest = commentId to text
		return UpdateDataResponse.Success
	}

	override suspend fun deleteComment(commentId: String): UpdateDataResponse =
		UpdateDataResponse.Success

	override suspend fun markCommentsReadUpTo(postId: String, readUpToSeq: Long): GetDataResponse<Long> =
		GetDataResponse.Success(readUpToSeq)

	override fun subscribePostComments(
		postId: String,
		afterSeq: Long,
		replayLimit: Int
	) = emptyFlow<CommentRealtimeEvent>()
}

private class TestCommentsReadCursorStore : CommentsReadCursorStore {
	override suspend fun getLocalLastReadSeq(postId: String): Long = 0L
	override suspend fun setLocalLastReadSeq(postId: String, readSeq: Long) = Unit
	override suspend fun enqueueReadUpTo(postId: String, readUpToSeq: Long) = Unit
	override suspend fun getPendingReadUpTo(postId: String): Long = 0L
	override suspend fun markPendingReadUpToApplied(postId: String, appliedReadSeq: Long) = Unit
}

private class TestAuthenticationManager(
	private val signedIn: Boolean,
	selfUserId: String?
) : AuthenticationManager {
	private var storedSelfUserId = selfUserId
	override val authenticationStateFlow: StateFlow<AuthState> = MutableStateFlow(AuthState.NoIdToken)

	override suspend fun handleGoogleOAuthCode(code: String) = Unit
	override suspend fun getAuthTokenOrNull(): String? = null
	override fun getSelfUserIdOrNull(): String? = storedSelfUserId
	override fun saveSelfUserId(userId: String) {
		storedSelfUserId = userId
	}

	override fun isSignedIn(): Boolean = signedIn
	override fun hasPendingRegistration(): Boolean = false
	override fun getPendingRegistrationTokenOrNull(): String? = null
	override fun getPendingRegistrationInitialDataOrNull(): PendingRegistrationInitialData? = null
	override suspend fun startGoogleAuthentication() = Unit
	override suspend fun writeAuthToken(token: String) = Unit
	override suspend fun clearPendingRegistration() = Unit
	override suspend fun clearAuth() = Unit
}

private class TestLogger : Logger {
	override fun d(tag: String?, message: String) = Unit
}
