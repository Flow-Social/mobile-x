package me.floow.chatssearch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import me.floow.chatssearch.uilogic.SearchUsersScreenViewModel
import me.floow.uikit.util.SetNavigationBarColor
import me.floow.uikit.util.SetStatusBarStyle

@Composable
fun SearchUsersRoute(
	onBackClick: () -> Unit,
	onUserPick: (String) -> Unit,
	vm: SearchUsersScreenViewModel,
	modifier: Modifier = Modifier
) {
	val state by vm.state.collectAsState()
	val statusBarColor = MaterialTheme.colorScheme.background
	val useDarkStatusIcons = statusBarColor.luminance() > 0.5f

	LaunchedEffect(Unit) {
		vm.loadInitialData()
	}

	SetStatusBarStyle(
		color = statusBarColor,
		darkIcons = useDarkStatusIcons
	)

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
