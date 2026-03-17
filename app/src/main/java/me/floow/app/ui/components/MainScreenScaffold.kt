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
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import me.floow.app.navigation.bottomNavigationItems
import me.floow.uikit.components.shell.MainContentContainer
import me.floow.uikit.components.shell.MainShellBackground
import me.floow.uikit.components.shell.MainShellDefaults
import me.floow.uikit.util.SetStatusBarStyle

enum class MainScreenScaffoldMode {
	Default,
	Immersive
}

@Composable
fun MainScreenScaffold(
	navController: NavController,
	modifier: Modifier = Modifier,
	shellMode: MainScreenScaffoldMode = MainScreenScaffoldMode.Default,
	feedUndoEnabled: Boolean = false,
	chatsUnreadCount: Int = 0,
	onFeedUndoClick: (() -> Unit)? = null,
	content: @Composable (PaddingValues) -> Unit
) {
	val navBackStackEntry by navController.currentBackStackEntryAsState()
	val currentDestination = navBackStackEntry?.destination
	val contentContainerColor = MainShellDefaults.contentContainerColor

	Scaffold(
		containerColor = androidx.compose.ui.graphics.Color.Transparent,
		bottomBar = {
			FlowBottomBar(
				currentDestination = currentDestination,
				navigationItems = bottomNavigationItems,
				feedUndoEnabled = feedUndoEnabled,
				chatsUnreadCount = chatsUnreadCount,
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
		val containerPadding = PaddingValues(
			start = 0.dp,
			top = 0.dp,
			end = 0.dp,
			bottom = innerPadding.calculateBottomPadding()
		)
		val contentPadding = PaddingValues(
			start = 0.dp,
			top = if (shellMode == MainScreenScaffoldMode.Default) {
				innerPadding.calculateTopPadding()
			} else {
				0.dp
			},
			end = 0.dp,
			bottom = 0.dp
		)

		MainShellBackground(modifier = Modifier.fillMaxSize()) {
			if (shellMode == MainScreenScaffoldMode.Default) {
				SetStatusBarStyle(
					color = contentContainerColor,
					darkIcons = contentContainerColor.luminance() > 0.5f
				)
			}

			MainContentContainer(
				modifier = Modifier
					.fillMaxSize()
					.padding(containerPadding)
			) {
				Box(
					modifier = Modifier
						.fillMaxSize()
						.padding(contentPadding)
						.consumeWindowInsets(contentPadding)
				) {
					content(PaddingValues(0.dp))
				}
			}
		}
	}
}
