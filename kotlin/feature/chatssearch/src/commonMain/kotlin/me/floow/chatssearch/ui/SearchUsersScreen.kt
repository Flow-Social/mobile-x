package me.floow.chatssearch.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.floow.chatssearch.ui.components.SearchUsersScreenTopBar
import me.floow.chatssearch.ui.states.LoadingState
import me.floow.chatssearch.ui.states.NoSearchInputState
import me.floow.chatssearch.ui.states.SearchResultsState
import me.floow.chatssearch.uilogic.SearchUsersScreenUiState

@Composable
internal fun SearchUsersScreen(
	onBackClick: () -> Unit,
	onUserPick: (String) -> Unit,
	onSearchFieldUpdate: (String) -> Unit,
	state: SearchUsersScreenUiState,
	strings: SearchUsersStrings,
	modifier: Modifier = Modifier
) {
	Scaffold(
		topBar = {
			SearchUsersScreenTopBar(
				onBackClick = onBackClick,
				searchFieldValue = state.searchField,
				onSearchFieldUpdate = onSearchFieldUpdate,
				placeholder = strings.searchFieldPlaceholder,
				modifier = Modifier.statusBarsPadding()
			)
		},
		contentWindowInsets = WindowInsets(0.dp),
		modifier = modifier,
	) { innerPadding ->
		val contentModifier = Modifier
			.fillMaxSize()
			.padding(innerPadding)
			.navigationBarsPadding()

		when (state) {
			is SearchUsersScreenUiState.Loading -> {
				LoadingState(modifier = contentModifier)
			}

			is SearchUsersScreenUiState.NoSearchInput -> {
				NoSearchInputState(
					state = state,
					recentSearchesTitle = strings.recentSearchesTitle,
					modifier = contentModifier
				)
			}

			is SearchUsersScreenUiState.HasResults -> {
				SearchResultsState(
					state = state,
					strings = strings,
					onUserClick = { onUserPick(it.id) },
					modifier = contentModifier
				)
			}
		}
	}
}
