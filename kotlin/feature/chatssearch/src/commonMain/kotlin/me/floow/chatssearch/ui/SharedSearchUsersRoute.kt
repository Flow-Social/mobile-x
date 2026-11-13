package me.floow.chatssearch.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import me.floow.chatssearch.uilogic.SearchUsersRouteComponent

@Composable
fun SharedSearchUsersRoute(
	onBackClick: () -> Unit,
	onUserPick: (String) -> Unit,
	component: SearchUsersRouteComponent,
	strings: SearchUsersStrings,
	modifier: Modifier = Modifier,
) {
	val state by component.state.collectAsState()

	LaunchedEffect(component) {
		component.loadInitialData()
	}
	DisposableEffect(component) {
		onDispose {
			component.onRouteClosed()
		}
	}

	SearchUsersScreen(
		onBackClick = onBackClick,
		onUserPick = onUserPick,
		onSearchFieldUpdate = component::updateSearchField,
		state = state,
		strings = strings,
		modifier = modifier
	)
}
