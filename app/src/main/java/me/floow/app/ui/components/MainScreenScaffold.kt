package me.floow.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import me.floow.app.navigation.bottomNavigationItems

@Composable
fun MainScreenScaffold(
	navController: NavController,
	modifier: Modifier = Modifier,
	disableTopInset: Boolean = false,
	feedUndoEnabled: Boolean = false,
	onFeedUndoClick: (() -> Unit)? = null,
	content: @Composable (PaddingValues) -> Unit
) {
	val navBackStackEntry by navController.currentBackStackEntryAsState()
	val currentDestination = navBackStackEntry?.destination

	Scaffold(
		bottomBar = {
			FlowBottomBar(
				currentDestination = currentDestination,
				navigationItems = bottomNavigationItems,
				feedUndoEnabled = feedUndoEnabled,
				onFeedUndoClick = onFeedUndoClick,
				onClick = {
					navController.navigate(it) {
						popUpTo(navController.graph.findStartDestination().id) {
							saveState = true
						}
						launchSingleTop = true
						restoreState = true
					}
				}
			)
		},
		modifier = modifier.fillMaxSize()
	) { innerPadding ->
		val contentPadding = if (disableTopInset) {
			PaddingValues(
				start = 0.dp,
				top = 0.dp,
				end = 0.dp,
				bottom = innerPadding.calculateBottomPadding()
			)
		} else {
			innerPadding
		}

		Box(
			modifier = Modifier
				.fillMaxSize()
				.padding(contentPadding)
				.consumeWindowInsets(contentPadding)
		) {
			content(contentPadding)
		}
	}
}
