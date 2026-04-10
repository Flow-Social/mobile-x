package me.floow.chatssearch.uilogic

import kotlinx.coroutines.flow.StateFlow

interface SearchUsersRouteComponent {
	val state: StateFlow<SearchUsersScreenUiState>

	fun loadInitialData()
	fun updateSearchField(newValue: String)
	fun onRouteClosed() {}
}
