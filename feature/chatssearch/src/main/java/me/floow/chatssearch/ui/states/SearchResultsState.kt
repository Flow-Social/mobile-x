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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import me.floow.chatssearch.ui.components.globalSearchUsersList
import me.floow.chatssearch.ui.components.messageResultsList
import me.floow.chatssearch.uilogic.MessageResult
import me.floow.chatssearch.uilogic.SearchUsersScreenUiState
import me.floow.chatssearch.uilogic.UserSearchResult
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername

@Composable
fun SearchResultsState(
	state: SearchUsersScreenUiState.HasResults,
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
					onClick = { }
				)
			}
		}
	}
}

@Preview
@Composable
private fun SearchResultsStatePreview() {
	SearchResultsState(
		state = SearchUsersScreenUiState.HasResults(
			searchField = "test",
			userResults = listOf(
				UserSearchResult(
					id = "1",
					name = ProfileName.create("Demn"),
					username = ProfileUsername.create("demndevel"),
					avatarUrl = null,
					isOnline = false
				)
			),
			messageResults = listOf(
				MessageResult(
					name = ProfileName.create("Finsi"),
					messageText = "Some example text. Some example text. Some example text. Some example text. Some example text. Some example text. Some example text. "
				),
				MessageResult(
					name = ProfileName.create("Demn"),
					messageText = "Some example text"
				),
				MessageResult(
					name = ProfileName.create("Finsi"),
					messageText = "Some example text. Some example text. Some example text. Some example text. Some example text. Some example text. Some example text. "
				),
				MessageResult(
					name = ProfileName.create("Demn"),
					messageText = "Some example text"
				),
				MessageResult(
					name = ProfileName.create("Finsi"),
					messageText = "Some example text. Some example text. Some example text. Some example text. Some example text. Some example text. Some example text. "
				)
			)
		),
		onUserClick = { },
		Modifier.fillMaxSize()
	)
}
