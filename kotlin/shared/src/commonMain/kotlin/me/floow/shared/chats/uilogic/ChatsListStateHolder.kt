package me.floow.shared.chats.uilogic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class ChatsListStateHolder(
	private val repository: ChatsListRepository,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
	private val _state = MutableStateFlow<ChatsScreenState>(ChatsScreenState.Loading)
	val state: StateFlow<ChatsScreenState> = _state.asStateFlow()

	private var hasLocalData = false
	private var didNetworkRefresh = false
	private var isLoading = false
	private var isLoadingMore = false

	init {
		scope.launch {
			combine(
				repository.observeChats(),
				repository.observePagingState(),
			) { chats, pagingState -> chats to pagingState }.collectLatest { (chats, pagingState) ->
				chats ?: return@collectLatest
				hasLocalData = true
				_state.value = if (chats.isEmpty()) {
					ChatsScreenState.NoChats
				} else {
					ChatsScreenState.HasData(
						chats = chats,
						isLoadingMore = pagingState.isLoadingMore,
						hasMore = pagingState.hasMore,
						loadMoreError = pagingState.loadMoreError,
					)
				}
			}
		}
	}

	fun loadIfNeeded() {
		if (didNetworkRefresh || isLoading) return
		load()
	}

	fun load(force: Boolean = false) {
		if (isLoading && !force) return
		isLoading = true
		if (!hasLocalData) {
			_state.value = ChatsScreenState.Loading
		}
		scope.launch {
			repository.refreshChats()
				.onSuccess { chats ->
					didNetworkRefresh = true
					_state.value = if (chats.isEmpty()) {
						ChatsScreenState.NoChats
					} else {
						val pagingState = repository.observePagingState().value
						ChatsScreenState.HasData(
							chats = chats,
							isLoadingMore = pagingState.isLoadingMore,
							hasMore = pagingState.hasMore,
							loadMoreError = pagingState.loadMoreError,
						)
					}
				}
				.onFailure {
					if (!hasLocalData) {
						_state.value = ChatsScreenState.Error
					}
				}
			isLoading = false
		}
	}

	fun refresh() {
		load(force = true)
	}

	fun loadMore() {
		if (isLoadingMore) return
		val current = _state.value as? ChatsScreenState.HasData ?: return
		if (!current.hasMore || current.isLoadingMore) return
		isLoadingMore = true
		scope.launch {
			repository.loadMoreChats()
				.onFailure {
					val state = _state.value as? ChatsScreenState.HasData ?: return@onFailure
					_state.value = state.copy(
						isLoadingMore = false,
						loadMoreError = true,
					)
				}
			isLoadingMore = false
		}
	}

	fun toggleConversationPinned(item: me.floow.shared.chats.model.ChatListItemModel) {
		val conversationId = item.conversationId?.takeIf { it > 0L } ?: return
		scope.launch {
			repository.setConversationPinned(conversationId, !item.isPinned)
		}
	}

	fun deleteConversation(item: me.floow.shared.chats.model.ChatListItemModel, forEveryone: Boolean) {
		val conversationId = item.conversationId?.takeIf { it > 0L } ?: return
		scope.launch {
			repository.deleteConversation(conversationId, forEveryone)
		}
	}
}
