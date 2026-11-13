package me.floow.chatssearch.uilogic

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow

class SearchUsersScreenViewModel(
	private val stateHolder: SearchUsersStateHolder,
) : ViewModel(), SearchUsersRouteComponent {
	override val state: StateFlow<SearchUsersScreenUiState>
		get() = stateHolder.state

	override fun loadInitialData() = stateHolder.loadInitialData()

	override fun updateSearchField(newValue: String) = stateHolder.updateSearchField(newValue)

	override fun onCleared() {
		stateHolder.dispose()
		super.onCleared()
	}
}
