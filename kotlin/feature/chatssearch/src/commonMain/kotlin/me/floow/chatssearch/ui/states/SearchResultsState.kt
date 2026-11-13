package me.floow.chatssearch.ui.states

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.floow.chatssearch.ui.SearchUsersStrings
import me.floow.chatssearch.ui.components.globalSearchUsersList
import me.floow.chatssearch.ui.components.messageResultsList
import me.floow.chatssearch.uilogic.SearchUsersScreenUiState
import me.floow.chatssearch.uilogic.UserSearchResult

@Composable
fun SearchResultsState(
	state: SearchUsersScreenUiState.HasResults,
	strings: SearchUsersStrings,
	onUserClick: (UserSearchResult) -> Unit,
	modifier: Modifier = Modifier
) {
	var isGlobalUsersSearchExpanded by remember { mutableStateOf(false) }

	Column(modifier = modifier) {
		LazyColumn(
			modifier = Modifier.fillMaxSize(),
		) {
			if (state.userResults.isNotEmpty()) {
				globalSearchUsersList(
					isExpanded = isGlobalUsersSearchExpanded,
					onExpandedToggle = {
						isGlobalUsersSearchExpanded = !isGlobalUsersSearchExpanded
					},
					results = state.userResults,
					globalSearchTitle = strings.globalSearchTitle,
					showMoreLabel = strings.showMoreLabel,
					showLessLabel = strings.showLessLabel,
					onlineLabel = strings.onlineLabel,
					offlineLabel = strings.offlineLabel,
					onClick = onUserClick
				)

				item {
					Spacer(Modifier.height(12.dp))
					HorizontalDivider()
				}
			}

			if (state.messageResults.isNotEmpty()) {
				messageResultsList(
					results = state.messageResults,
					messageSearchTitle = strings.messageSearchTitle,
					onClick = { }
				)
			}
		}
	}
}
