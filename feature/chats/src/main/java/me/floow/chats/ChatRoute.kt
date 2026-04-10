package me.floow.chats

import android.net.Uri
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import me.floow.chats.uilogic.chat.DirectChatOpenMode
import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.ui.SharedDirectChatRoute
import me.floow.shared.chats.uilogic.direct.DirectChatInitialRequest
import me.floow.shared.chats.uilogic.direct.ChatPresenceContract
import me.floow.shared.chats.uilogic.direct.ChatRealtimeContract
import me.floow.shared.chats.uilogic.direct.DirectChatStateHolder
import me.floow.shared.chats.uilogic.direct.StaticChatThreadRepository
import me.floow.uikit.components.pickers.FlowEmojiPanel
import me.floow.uikit.util.SetStatusBarStyle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf

data class ChatRouteInitialData(
	val chatInterlocutorId: String,
	val chatInterlocutorName: String,
	val chatInterlocutorAvatarUrl: Uri?,
	val conversationId: Long? = null,
	val messageAnchorId: Long? = null,
	val openMode: DirectChatOpenMode = DirectChatOpenMode.FROM_LAST_SEEN,
	val isSavedMessages: Boolean = false,
)

@Composable
fun ChatRoute(
	initialData: ChatRouteInitialData,
	onBackClick: () -> Unit,
	onProfileClick: (String) -> Unit = {},
	isMockBuild: Boolean = false,
	modifier: Modifier = Modifier
) {
	val statusBarColor = MaterialTheme.colorScheme.background
	val useDarkStatusIcons = statusBarColor.luminance() > 0.5f
	val snackbarHostState = remember { SnackbarHostState() }
	val clipboardManager = LocalClipboardManager.current
	val scope = rememberCoroutineScope()
	val initialRequest = remember(initialData) { initialData.toSharedInitialRequest() }
	val chatRealtimeContract: ChatRealtimeContract? = if (isMockBuild) null else koinInject()
	val chatPresenceContract: ChatPresenceContract? = if (isMockBuild) null else koinInject()
	val stateHolder = if (isMockBuild) {
		remember(initialRequest) {
			DirectChatStateHolder(repository = StaticChatThreadRepository())
		}
	} else {
		koinInject<DirectChatStateHolder>(
			parameters = { parametersOf(chatRealtimeContract, chatPresenceContract) }
		)
	}

	SetStatusBarStyle(
		color = statusBarColor,
		darkIcons = useDarkStatusIcons
	)

	SharedDirectChatRoute(
		stateHolder = stateHolder,
		initialRequest = initialRequest,
		onBackClick = onBackClick,
		onShowMessage = { message ->
			scope.launch {
				snackbarHostState.showSnackbar(message)
			}
		},
		onCopyText = { text ->
			clipboardManager.setText(AnnotatedString(text))
		},
		onHeaderClick = if (initialRequest.isSavedMessages || initialRequest.peerUserId.isBlank()) {
			null
		} else {
			{ onProfileClick(initialRequest.peerUserId) }
		},
		emojiPanel = { inputController ->
			FlowEmojiPanel(
				onEmojiPicked = inputController::insertEmoji,
				modifier = Modifier,
			)
		},
		modifier = modifier,
	)
}

private fun ChatRouteInitialData.toSharedInitialRequest(): DirectChatInitialRequest {
	return DirectChatInitialRequest(
		peerUserId = chatInterlocutorId,
		peerDisplayName = chatInterlocutorName,
		peerAvatarUrl = chatInterlocutorAvatarUrl?.toString(),
		conversationId = conversationId,
		anchorMessageId = messageAnchorId,
		openMode = when (openMode) {
			DirectChatOpenMode.FROM_UNREAD -> ChatOpenMode.FROM_UNREAD
			DirectChatOpenMode.FROM_LAST_SEEN -> ChatOpenMode.FROM_LAST_SEEN
			DirectChatOpenMode.FROM_MESSAGE_LINK -> ChatOpenMode.FROM_MESSAGE_LINK
		},
		isSavedMessages = isSavedMessages,
		allowCreateFromPeerUserId = conversationId == null && chatInterlocutorId.isNotBlank(),
	)
}
