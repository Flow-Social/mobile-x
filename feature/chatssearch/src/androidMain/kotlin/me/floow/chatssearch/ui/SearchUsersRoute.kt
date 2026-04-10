package me.floow.chatssearch.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import me.floow.chatssearch.R
import me.floow.chatssearch.uilogic.SearchUsersScreenViewModel
import me.floow.uikit.util.SetNavigationBarColor
import me.floow.uikit.util.SetStatusBarStyle
import org.koin.androidx.compose.koinViewModel

@Composable
fun SearchUsersRoute(
	onBackClick: () -> Unit,
	onUserPick: (String) -> Unit,
	vm: SearchUsersScreenViewModel = koinViewModel(),
	modifier: Modifier = Modifier
) {
	val statusBarColor = MaterialTheme.colorScheme.background
	val useDarkStatusIcons = statusBarColor.luminance() > 0.5f
	val strings = SearchUsersStrings(
		searchFieldPlaceholder = stringResource(R.string.search_field_placeholder),
		recentSearchesTitle = stringResource(R.string.recent_searches),
		globalSearchTitle = stringResource(R.string.global_search),
		showMoreLabel = stringResource(R.string.show_more),
		showLessLabel = stringResource(R.string.show_less),
		messageSearchTitle = stringResource(R.string.message_search),
		onlineLabel = stringResource(me.floow.uikit.R.string.online),
		offlineLabel = stringResource(me.floow.uikit.R.string.offline),
	)

	SetStatusBarStyle(
		color = statusBarColor,
		darkIcons = useDarkStatusIcons
	)

	SharedSearchUsersRoute(
		onBackClick = onBackClick,
		onUserPick = onUserPick,
		component = vm,
		strings = strings,
		modifier = modifier
	)

	SetNavigationBarColor(MaterialTheme.colorScheme.background)
}
