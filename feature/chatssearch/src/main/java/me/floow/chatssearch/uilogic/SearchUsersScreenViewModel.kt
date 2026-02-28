package me.floow.chatssearch.uilogic

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
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
import java.util.LinkedHashMap

private data class SearchUsersScreenVmState(
	val searchField: String = "",
	val isLoading: Boolean = false,
	val globalSearchResults: List<UserSearchResult>? = null,
	val messageSearchResults: List<MessageResult>? = null,
	val recentUsers: List<RecentUser>? = null,
) {
	fun toUiState(): SearchUsersScreenUiState {
		if (isLoading) return SearchUsersScreenUiState.Loading(searchField)

		if ((searchField.isBlank() || globalSearchResults == null) && recentUsers != null) return SearchUsersScreenUiState.NoSearchInput(
			searchField = searchField,
			recentUsers = recentUsers
		)

		if (globalSearchResults != null && messageSearchResults != null) {
			return SearchUsersScreenUiState.HasResults(
				searchField,
				globalSearchResults,
				messageSearchResults
			)
		}

		return SearchUsersScreenUiState.Loading(searchField)
	}
}

@OptIn(FlowPreview::class)
class SearchUsersScreenViewModel(
	private val usersRepository: UsersRepository,
) : ViewModel() {
	private companion object {
		private const val SEARCH_DEBOUNCE_MS = 300L
		private const val QUERY_CACHE_MAX_SIZE = 20
	}

	private val _state = MutableStateFlow(SearchUsersScreenVmState())
	private val queryResultsCache = object : LinkedHashMap<String, List<UserSearchResult>>(
		QUERY_CACHE_MAX_SIZE,
		0.75f,
		true
	) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<UserSearchResult>>): Boolean {
			return size > QUERY_CACHE_MAX_SIZE
		}
	}

	val state: StateFlow<SearchUsersScreenUiState> = _state
		.map(SearchUsersScreenVmState::toUiState)
		.stateIn(
			viewModelScope,
			SharingStarted.Eagerly,
			SearchUsersScreenUiState.NoSearchInput("", emptyList())
		)

	init {
		viewModelScope.launch {
			_state
				.map { it.searchField }
				.debounce(SEARCH_DEBOUNCE_MS)
				.distinctUntilChanged()
				.collectLatest { query ->
					handleSearch(query)
				}
		}
	}

	fun loadInitialData() {
		viewModelScope.launch {
			_state.update {
				it.copy(
					recentUsers = emptyList(),
					isLoading = false,
				)
			}
		}
	}

	fun updateSearchField(newValue: String) {
		_state.update {
			it.copy(
				searchField = newValue
			)
		}
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
		val cachedResults = queryResultsCache[cacheKey]
		if (cachedResults != null) {
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
				response.data.map { user ->
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
					.distinctBy { result -> result.id }
			}

			is GetDataResponse.Error -> emptyList()
		}

		queryResultsCache[cacheKey] = userResults
		_state.update {
			it.copy(
				isLoading = false,
				globalSearchResults = userResults,
				messageSearchResults = emptyList(),
			)
		}
	}
}
