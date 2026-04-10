package me.floow.shared.chats.uilogic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatsListStateHolder(
	private val repository: ChatsListRepository,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
	private val _state = MutableStateFlow<ChatsScreenState>(ChatsScreenState.Loading)
	val state: StateFlow<ChatsScreenState> = _state.asStateFlow()

	private var didLoad = false
	private var isLoading = false

	init {
		scope.launch {
			repository.observeChats().collectLatest { chats ->
				chats ?: return@collectLatest
				didLoad = true
				_state.value = if (chats.isEmpty()) {
					ChatsScreenState.NoChats
				} else {
					ChatsScreenState.HasData(chats)
				}
			}
		}
	}

	fun loadIfNeeded() {
		if (didLoad || isLoading) return
		load()
	}

	fun load(force: Boolean = false) {
		if (isLoading && !force) return
		isLoading = true
		if (!didLoad) {
			_state.value = ChatsScreenState.Loading
		}
		scope.launch {
			repository.refreshChats()
				.onSuccess { chats ->
					didLoad = true
					_state.value = if (chats.isEmpty()) {
						ChatsScreenState.NoChats
					} else {
						ChatsScreenState.HasData(chats)
					}
				}
				.onFailure {
					if (!didLoad) {
						_state.value = ChatsScreenState.Error
					}
				}
			isLoading = false
		}
	}

	fun refresh() {
		load(force = true)
	}
}
