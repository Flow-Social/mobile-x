package me.floow.shared.chats.uilogic.replies

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.RepliesActorTarget
import me.floow.shared.chats.model.RepliesNavigationTarget
import me.floow.shared.chats.model.RepliesThreadItemModel

@OptIn(ExperimentalCoroutinesApi::class)
class RepliesStateHolderTest {

	@Test
	fun `load sets has data on success`() = runTest {
		val holder = RepliesStateHolder(
			repository = object : RepliesRepository {
				override suspend fun load(
					openMode: me.floow.shared.chats.model.ChatOpenMode,
					anchorSeq: Long?
				): Result<RepliesScreenData> = Result.success(
					RepliesScreenData(
						items = listOf(
							RepliesThreadItemModel(
								messageId = 1L,
								actorDisplayName = "User",
								actorAvatarUrl = null,
								text = "Reply",
								createdAtMillis = 1L,
							)
						),
						unreadCount = 1,
					)
				)

				override suspend fun markAllRead(): Result<Unit> = Result.success(Unit)
			},
			scope = this,
		)

		holder.load()
		advanceUntilIdle()

		assertTrue(holder.state.value is RepliesScreenState.HasData)
	}

	@Test
	fun `see all clears unread state`() = runTest {
		val holder = RepliesStateHolder(
			repository = object : RepliesRepository {
				override suspend fun load(
					openMode: me.floow.shared.chats.model.ChatOpenMode,
					anchorSeq: Long?
				): Result<RepliesScreenData> = Result.success(
					RepliesScreenData(
						items = listOf(
							RepliesThreadItemModel(
								messageId = 1L,
								actorDisplayName = "User",
								actorAvatarUrl = null,
								text = "Reply",
								createdAtMillis = 1L,
								isUnread = true,
							)
						),
						unreadCount = 1,
					)
				)

				override suspend fun markAllRead(): Result<Unit> = Result.success(Unit)
			},
			scope = this,
		)

		holder.load()
		advanceUntilIdle()
		holder.seeAll()
		advanceUntilIdle()

		val state = holder.state.value as? RepliesScreenState.HasData
		assertTrue(state != null)
		assertEquals(0, state.unreadCount)
		assertTrue(state.items.all { !it.isUnread })
	}

	@Test
	fun `open thread emits navigation target instead of direct chat item`() = runTest {
		val holder = RepliesStateHolder(
			repository = object : RepliesRepository {
				override suspend fun load(
					openMode: me.floow.shared.chats.model.ChatOpenMode,
					anchorSeq: Long?
				): Result<RepliesScreenData> = Result.success(RepliesScreenData(emptyList(), 0))

				override suspend fun markAllRead(): Result<Unit> = Result.success(Unit)
			},
			scope = this,
		)

		val deferredEvent = async(start = CoroutineStart.UNDISPATCHED) { holder.events.first() }
		holder.openThread(
			RepliesThreadItemModel(
				messageId = 7L,
				actorDisplayName = "User",
				text = "Reply",
				createdAtMillis = 1L,
				targetPostId = "post-1",
				targetCommentId = "comment-1",
				targetReplyToCommentId = "reply-1",
				threadId = "thread-1",
			)
		)

		val event = deferredEvent.await()
		assertEquals(
			RepliesStateHolder.Event.OpenThread(
				RepliesNavigationTarget(
					postId = "post-1",
					commentId = "comment-1",
					replyToCommentId = "reply-1",
					threadId = "thread-1",
				)
			),
			event,
		)
	}

	@Test
	fun `open actor profile emits actor target`() = runTest {
		val holder = RepliesStateHolder(
			repository = object : RepliesRepository {
				override suspend fun load(
					openMode: me.floow.shared.chats.model.ChatOpenMode,
					anchorSeq: Long?
				): Result<RepliesScreenData> = Result.success(RepliesScreenData(emptyList(), 0))

				override suspend fun markAllRead(): Result<Unit> = Result.success(Unit)
			},
			scope = this,
		)

		val deferredEvent = async(start = CoroutineStart.UNDISPATCHED) { holder.events.first() }
		holder.openActorProfile(
			RepliesThreadItemModel(
				messageId = 7L,
				actorUserId = "user-7",
				actorDisplayName = "User",
				text = "Reply",
				createdAtMillis = 1L,
			)
		)

		val event = deferredEvent.await()
		assertEquals(
			RepliesStateHolder.Event.OpenActorProfile(RepliesActorTarget("user-7")),
			event,
		)
	}

	@Test
	fun `load from unread anchors first unread reply`() = runTest {
		val items = listOf(
			replyItem(messageId = 1L, isUnread = false),
			replyItem(messageId = 2L, isUnread = false),
			replyItem(messageId = 3L, isUnread = true),
			replyItem(messageId = 4L, isUnread = true),
		)
		val holder = RepliesStateHolder(
			repository = repliesRepository(items),
			scope = this,
		)

		holder.load(openMode = ChatOpenMode.FROM_UNREAD)
		advanceUntilIdle()

		val state = holder.state.value as? RepliesScreenState.HasData
		assertTrue(state != null)
		assertEquals(3L, state.unreadBoundaryMessageId)
		assertEquals(3L, state.openAnchorMessageId)
	}

	@Test
	fun `load from last seen anchors last read reply before unread boundary`() = runTest {
		val items = listOf(
			replyItem(messageId = 1L, isUnread = false),
			replyItem(messageId = 2L, isUnread = false),
			replyItem(messageId = 3L, isUnread = true),
			replyItem(messageId = 4L, isUnread = true),
		)
		val holder = RepliesStateHolder(
			repository = repliesRepository(items),
			scope = this,
		)

		holder.load(openMode = ChatOpenMode.FROM_LAST_SEEN)
		advanceUntilIdle()

		val state = holder.state.value as? RepliesScreenState.HasData
		assertTrue(state != null)
		assertEquals(3L, state.unreadBoundaryMessageId)
		assertEquals(2L, state.openAnchorMessageId)
	}

	@Test
	fun `load from message link anchors provided reply`() = runTest {
		val items = listOf(
			replyItem(messageId = 1L, isUnread = false),
			replyItem(messageId = 2L, isUnread = false),
			replyItem(messageId = 3L, isUnread = true),
			replyItem(messageId = 4L, isUnread = true),
		)
		val holder = RepliesStateHolder(
			repository = repliesRepository(items),
			scope = this,
		)

		holder.load(openMode = ChatOpenMode.FROM_MESSAGE_LINK, anchorSeq = 4L)
		advanceUntilIdle()

		val state = holder.state.value as? RepliesScreenState.HasData
		assertTrue(state != null)
		assertEquals(3L, state.unreadBoundaryMessageId)
		assertEquals(4L, state.openAnchorMessageId)
	}
}

private fun replyItem(
	messageId: Long,
	isUnread: Boolean,
): RepliesThreadItemModel {
	return RepliesThreadItemModel(
		messageId = messageId,
		actorDisplayName = "User $messageId",
		actorAvatarUrl = null,
		text = "Reply $messageId",
		createdAtMillis = messageId,
		isUnread = isUnread,
	)
}

private fun repliesRepository(items: List<RepliesThreadItemModel>): RepliesRepository {
	return object : RepliesRepository {
		override suspend fun load(
			openMode: ChatOpenMode,
			anchorSeq: Long?,
		): Result<RepliesScreenData> = Result.success(
			RepliesScreenData(
				items = items,
				unreadCount = items.count(RepliesThreadItemModel::isUnread),
			)
		)

		override suspend fun markAllRead(): Result<Unit> = Result.success(Unit)
	}
}
