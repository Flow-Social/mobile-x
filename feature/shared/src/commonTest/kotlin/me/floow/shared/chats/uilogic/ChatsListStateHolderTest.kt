package me.floow.shared.chats.uilogic

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.assertEquals
import kotlin.test.Test
import kotlin.test.assertTrue
import me.floow.shared.chats.model.ChatListItemModel
import me.floow.shared.chats.model.ChatListItemVisualType

@OptIn(ExperimentalCoroutinesApi::class)
class ChatsListStateHolderTest {

	@Test
	fun `load sets has data on success`() = runTest {
		val cached = MutableStateFlow<List<ChatListItemModel>?>(null)
		val holder = ChatsListStateHolder(
			repository = object : ChatsListRepository {
				override fun observeChats() = cached
				override suspend fun refreshChats(): Result<List<ChatListItemModel>> = Result.success(
					listOf(
						ChatListItemModel(
							id = "1",
							peerUserId = "1",
							conversationId = 1L,
							title = "Chat",
							previewText = "hello",
							timeLabel = "10:00",
							unreadCount = 0,
							isMuted = false,
							isOnline = true,
							avatarUrl = null,
							hasAttachmentPreview = false,
							isOutgoingPreview = false,
							deliveryStatus = null,
							visualType = ChatListItemVisualType.Regular,
						)
					)
				).also { cached.value = it.getOrNull() }
			},
			scope = this,
		)

		holder.load()
		advanceUntilIdle()

		assertTrue(holder.state.value is ChatsScreenState.HasData)
		coroutineContext.cancelChildren()
	}

	@Test
	fun `load sets error on failure`() = runTest {
		val holder = ChatsListStateHolder(
			repository = object : ChatsListRepository {
				override fun observeChats() = MutableStateFlow<List<ChatListItemModel>?>(null)
				override suspend fun refreshChats(): Result<List<ChatListItemModel>> =
					Result.failure(IllegalStateException("boom"))
			},
			scope = this,
		)

		holder.load()
		advanceUntilIdle()

		assertTrue(holder.state.value is ChatsScreenState.Error)
		coroutineContext.cancelChildren()
	}

	@Test
	fun `cached snapshot stays visible when refresh fails`() = runTest {
		val cachedItems = listOf(
			ChatListItemModel(
				id = "1",
				peerUserId = "1",
				conversationId = 1L,
				title = "Chat",
				previewText = "hello",
				timeLabel = "10:00",
				unreadCount = 0,
				isMuted = false,
				isOnline = true,
				avatarUrl = null,
				hasAttachmentPreview = false,
				isOutgoingPreview = false,
				deliveryStatus = null,
				visualType = ChatListItemVisualType.Regular,
			)
		)
		val holder = ChatsListStateHolder(
			repository = object : ChatsListRepository {
				override fun observeChats() = MutableStateFlow<List<ChatListItemModel>?>(cachedItems)
				override suspend fun refreshChats(): Result<List<ChatListItemModel>> =
					Result.failure(IllegalStateException("boom"))
			},
			scope = this,
		)

		holder.load()
		advanceUntilIdle()

		val state = holder.state.value as? ChatsScreenState.HasData
		assertTrue(state != null)
		assertEquals(cachedItems, state.chats)
		coroutineContext.cancelChildren()
	}
}
