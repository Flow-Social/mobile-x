package me.floow.chatssearch.uilogic

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.UsersRepository
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate

private data class SearchUsersScreenVmState(
	val searchField: String = "",
	val isLoading: Boolean = false,
	val globalSearchResults: List<UserSearchResult>? = null,
	val messageSearchResults: List<MessageResult>? = null,
	val recentUsers: List<RecentUser>? = null,
) {
	fun toUiState(): SearchUsersScreenUiState {
		if (isLoading) return SearchUsersScreenUiState.Loading(searchField)

		if ((searchField.isBlank() || globalSearchResults == null) && recentUsers != null) {
			return SearchUsersScreenUiState.NoSearchInput(
				searchField = searchField,
				recentUsers = recentUsers
			)
		}

		if (globalSearchResults != null && messageSearchResults != null) {
			return SearchUsersScreenUiState.HasResults(
				searchField = searchField,
				userResults = globalSearchResults,
				messageResults = messageSearchResults
			)
		}

		return SearchUsersScreenUiState.Loading(searchField)
	}
}

@OptIn(FlowPreview::class)
class SearchUsersStateHolder(
	private val usersRepository: UsersRepository,
	private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
) : SearchUsersRouteComponent {
	private companion object {
		private const val SEARCH_DEBOUNCE_MS = 300L
		private const val QUERY_CACHE_MAX_SIZE = 20
	}

	private val _state = MutableStateFlow(SearchUsersScreenVmState())
	private val queryResultsCache = linkedMapOf<String, List<UserSearchResult>>()

	override val state: StateFlow<SearchUsersScreenUiState> = _state
		.map(SearchUsersScreenVmState::toUiState)
		.stateIn(
			scope,
			SharingStarted.Eagerly,
			SearchUsersScreenUiState.NoSearchInput("", emptyList())
		)

	init {
		scope.launch {
			_state
				.map { it.searchField }
				.debounce(SEARCH_DEBOUNCE_MS)
				.distinctUntilChanged()
				.collectLatest(::handleSearch)
		}
	}

	override fun loadInitialData() {
		_state.update {
			it.copy(
				recentUsers = emptyList(),
				isLoading = false,
			)
		}
	}

	override fun updateSearchField(newValue: String) {
		_state.update {
			it.copy(searchField = newValue)
		}
	}

	override fun onRouteClosed() {
		dispose()
	}

	@OptIn(RawValueObjectCreate::class)
	private suspend fun handleSearch(query: String) {
		val normalizedQuery = query.trim()
		if (normalizedQuery.isBlank()) {
			_state.update {
				it.copy(
					isLoading = false,
					globalSearchResults = null,
					messageSearchResults = null,
				)
			}
			return
		}

		val cacheKey = normalizedQuery.lowercase()
		queryResultsCache[cacheKey]?.let { cachedResults ->
			queryResultsCache.remove(cacheKey)
			queryResultsCache[cacheKey] = cachedResults
			_state.update {
				it.copy(
					isLoading = false,
					globalSearchResults = cachedResults,
					messageSearchResults = emptyList(),
				)
			}
			return
		}

		_state.update {
			it.copy(
				isLoading = true,
				globalSearchResults = it.globalSearchResults ?: emptyList(),
				messageSearchResults = it.messageSearchResults ?: emptyList(),
			)
		}

		val userResults = when (val response = usersRepository.searchUsers(normalizedQuery)) {
			is GetDataResponse.Success -> {
				response.data
					.map { user ->
						val name = user.name ?: ProfileName.createRaw(user.username?.value ?: "Unknown")
						val username = user.username ?: ProfileUsername.createRaw("unknown")

						UserSearchResult(
							id = user.id,
							name = name,
							username = username,
							avatarUrl = user.avatarUrl,
							isOnline = false,
						)
					}
					.distinctBy(UserSearchResult::id)
			}

			is GetDataResponse.Error -> emptyList()
		}

		queryResultsCache[cacheKey] = userResults
		while (queryResultsCache.size > QUERY_CACHE_MAX_SIZE) {
			val oldestKey = queryResultsCache.keys.firstOrNull() ?: break
			queryResultsCache.remove(oldestKey)
		}
		_state.update {
			it.copy(
				isLoading = false,
				globalSearchResults = userResults,
				messageSearchResults = emptyList(),
			)
		}
	}

	fun dispose() {
		scope.cancel()
	}
}
