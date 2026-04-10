package me.floow.chatssearch.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.floow.uikit.components.topbar.SearchTopBar

@Composable
fun SearchUsersScreenTopBar(
	onBackClick: () -> Unit,
	searchFieldValue: String,
	onSearchFieldUpdate: (String) -> Unit,
	placeholder: String,
	modifier: Modifier = Modifier
) {
	SearchTopBar(
		onBackClick = onBackClick,
		searchFieldValue = searchFieldValue,
		placeholder = placeholder,
		onSearchFieldUpdate = onSearchFieldUpdate,
		autoFocusOnStart = true,
		modifier = modifier
	)
}
