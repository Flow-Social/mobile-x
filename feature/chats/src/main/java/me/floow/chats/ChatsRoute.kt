package me.floow.chats

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import me.floow.chats.ui.chats.ChatsScreen
import me.floow.chats.uilogic.chats.Chat
import me.floow.chats.uilogic.chats.ChatsScreenViewModel
import me.floow.uikit.util.SetNavigationBarColor
import me.floow.uikit.util.SwipeBackOverlay
import org.koin.androidx.compose.koinViewModel

@Composable
fun ChatsRoute(
	onSearchClick: () -> Unit,
	onChatClick: (Chat) -> Unit,
	onProfileClick: (String) -> Unit = {},
	bottomBar: @Composable () -> Unit = {},
	isMockBuild: Boolean = false,
	vm: ChatsScreenViewModel,
	modifier: Modifier = Modifier
) {
	val state by vm.state.collectAsState()
	var selectedChat by remember { mutableStateOf<Chat?>(null) }
	var overlayProgress by remember { mutableStateOf(0f) }

	BackHandler(enabled = selectedChat != null) {
		selectedChat = null
		overlayProgress = 0f
			}

	LaunchedEffect(isMockBuild) {
		vm.setUseMockData(isMockBuild)
		vm.load()
	}
	
	val navigationBarColor = if (selectedChat == null) {
		NavigationBarDefaults.containerColor
	} else {
		MaterialTheme.colorScheme.surface
	}
	SetNavigationBarColor(navigationBarColor)

	BoxWithConstraints(modifier = modifier) {
		val density = LocalDensity.current
		val widthPx = with(density) { maxWidth.toPx() }
		val parallaxFactor = 0.12f

		val backgroundModifier = if (selectedChat != null) {
			Modifier.offset {
				androidx.compose.ui.unit.IntOffset(
					(-widthPx * parallaxFactor * (1f - overlayProgress)).toInt(),
					0
				)
			}
		} else {
			Modifier
		}

		Box(modifier = backgroundModifier) {
			Scaffold(
				bottomBar = bottomBar,
				modifier = Modifier.fillMaxSize()
			) { innerPadding ->
				ChatsScreen(
					onSearchClick = onSearchClick,
					state = state,
					onChatClick = { chat ->
						selectedChat = chat
					},
					modifier = Modifier
						.fillMaxSize()
						.padding(innerPadding)
				)
			}
		}

		if (selectedChat != null) {
			val chat = selectedChat!!
			val chatVm = koinViewModel<me.floow.chats.uilogic.chat.ChatScreenViewModel>()

			SwipeBackOverlay(
				onClose = {
					selectedChat = null
					overlayProgress = 0f
									},
				modifier = Modifier.fillMaxSize(),
				onProgressChange = { overlayProgress = it },
				entryKey = chat.id,
							) {
				ChatRoute(
					initialData = ChatRouteInitialData(
						chatInterlocutorId = chat.id,
						chatInterlocutorName = chat.name.value,
						chatInterlocutorAvatarUrl = chat.avatarUrl
					),
					onBackClick = {
						selectedChat = null
						overlayProgress = 0f
											},
										onProfileClick = onProfileClick,
					vm = chatVm,
					modifier = Modifier.fillMaxSize()
				)
			}
		}
	}
}
