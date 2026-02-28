package me.floow.chatssearch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import me.floow.chatssearch.uilogic.SearchUsersScreenViewModel
import me.floow.uikit.util.SetNavigationBarColor
import androidx.compose.material3.MaterialTheme

@Composable
fun SearchUsersRoute(
	onBackClick: () -> Unit,
	onUserPick: (String) -> Unit,
	vm: SearchUsersScreenViewModel,
	modifier: Modifier = Modifier
) {
	val state by vm.state.collectAsState()

	LaunchedEffect(Unit) {
		vm.loadInitialData()
	}

	SearchUsersScreen(
		onBackClick = onBackClick,
		onUserPick = onUserPick,
		onSearchFieldUpdate = vm::updateSearchField,
		state = state,
		modifier = modifier
	)

	SetNavigationBarColor(
		MaterialTheme.colorScheme.background
	)
}
