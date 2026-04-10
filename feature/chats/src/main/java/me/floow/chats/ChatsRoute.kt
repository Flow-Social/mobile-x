package me.floow.chats

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import me.floow.shared.chats.model.ChatListItemModel
import me.floow.shared.chats.ui.SharedChatsRoute
import me.floow.shared.chats.uilogic.ChatsListStateHolder
import me.floow.shared.chats.uilogic.StaticChatsListRepository
import me.floow.uikit.components.shell.MainShellDefaults
import me.floow.uikit.util.SetNavigationBarColor
import org.koin.compose.koinInject

@Composable
fun ChatsRoute(
	onSearchClick: () -> Unit,
	onChatClick: (ChatListItemModel) -> Unit,
	stateHolder: ChatsListStateHolder? = null,
	isMockBuild: Boolean = false,
	modifier: Modifier = Modifier
) {
	val routeStateHolder = stateHolder ?: if (isMockBuild) {
		remember(isMockBuild) {
			ChatsListStateHolder(StaticChatsListRepository())
		}
	} else {
		koinInject<ChatsListStateHolder>()
	}

	SetNavigationBarColor(MainShellDefaults.appBackgroundColor)

	SharedChatsRoute(
		onSearchClick = onSearchClick,
		stateHolder = routeStateHolder,
		onChatClick = onChatClick,
		modifier = modifier.fillMaxSize()
	)
}
