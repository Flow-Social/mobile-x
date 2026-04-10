package me.floow.shared.chats.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import me.floow.shared.chats.model.ChatListItemModel
import me.floow.shared.chats.uilogic.ChatsListStateHolder

@Composable
fun SharedChatsRoute(
	stateHolder: ChatsListStateHolder,
	onSearchClick: () -> Unit,
	onChatClick: (ChatListItemModel) -> Unit,
	modifier: Modifier = Modifier,
) {
	val state by stateHolder.state.collectAsState()

	LaunchedEffect(stateHolder) {
		stateHolder.loadIfNeeded()
	}

	SharedChatsScreen(
		onSearchClick = onSearchClick,
		state = state,
		onChatClick = onChatClick,
		modifier = modifier,
	)
}
