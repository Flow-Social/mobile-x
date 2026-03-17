package me.floow.chats

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.floow.chats.ui.chats.ChatsScreen
import me.floow.chats.uilogic.chats.Chat
import me.floow.chats.uilogic.chats.ChatsScreenViewModel
import me.floow.uikit.components.shell.MainShellDefaults
import me.floow.uikit.util.SetNavigationBarColor

@Composable
fun ChatsRoute(
	onSearchClick: () -> Unit,
	onChatClick: (Chat) -> Unit,
	isMockBuild: Boolean = false,
	vm: ChatsScreenViewModel,
	modifier: Modifier = Modifier
) {
	val state by vm.state.collectAsStateWithLifecycle()

	LaunchedEffect(isMockBuild) {
		vm.setUseMockData(isMockBuild)
		vm.load()
	}

	SetNavigationBarColor(MainShellDefaults.appBackgroundColor)

	ChatsScreen(
		onSearchClick = onSearchClick,
		state = state,
		onChatClick = onChatClick,
		modifier = modifier.fillMaxSize()
	)
}
