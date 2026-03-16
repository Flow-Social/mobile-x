package me.floow.chats

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import kotlinx.coroutines.flow.collectLatest
import me.floow.chats.uilogic.replies.RepliesOverlayOpenMode
import me.floow.chats.uilogic.replies.RepliesOverlayViewModel
import me.floow.domain.models.CommentId
import me.floow.domain.models.resolveReplyTargetCommentCandidates
import me.floow.uikit.chat.ChatScreen
import me.floow.uikit.chat.model.ChatLayoutMode
import me.floow.uikit.chat.model.ChatScreenConfig
import me.flowme.chats.R
import org.koin.androidx.compose.koinViewModel

data class ReplyThreadNavigationTarget(
	val postId: String,
	val commentId: String,
	val replyToCommentId: String?,
	val threadId: String? = null
)

fun ReplyThreadNavigationTarget.resolveCommentTargetCandidates(): List<CommentId> {
	return resolveReplyTargetCommentCandidates(
		commentId = commentId,
		replyToCommentId = replyToCommentId,
		threadId = threadId
	)
}

@Composable
fun RepliesOverlayRoute(
	onBackClick: () -> Unit,
	onOpenThreadClick: (ReplyThreadNavigationTarget) -> Unit,
	onAllRepliesRead: () -> Unit = {},
	onProfileClick: (String) -> Unit = {},
	openMode: RepliesOverlayOpenMode = RepliesOverlayOpenMode.FROM_UNREAD,
	messageLinkAnchorSeq: Long? = null,
	vm: RepliesOverlayViewModel = koinViewModel(),
	modifier: Modifier = Modifier
) {
	val state by vm.state.collectAsState()
	val repliesInboxTitle = stringResource(R.string.replies_inbox_title)
	val repliesFallbackActorName = stringResource(R.string.replies_fallback_actor_name)
	val repliesFallbackMessageText = stringResource(R.string.replies_fallback_message_text)

	LaunchedEffect(
		repliesInboxTitle,
		repliesFallbackActorName,
		repliesFallbackMessageText,
		openMode,
		messageLinkAnchorSeq
	) {
		vm.load(
			interlocutorName = repliesInboxTitle,
			fallbackActorName = repliesFallbackActorName,
			fallbackMessageText = repliesFallbackMessageText,
			openMode = openMode,
			messageLinkAnchorSeq = messageLinkAnchorSeq
		)
	}
	LaunchedEffect(vm, onOpenThreadClick) {
		var lastConsumedEventId = 0L
		vm.openThreadEvents.collectLatest { event ->
			if (event.eventId <= lastConsumedEventId) return@collectLatest
			lastConsumedEventId = event.eventId
			onOpenThreadClick(event.target)
		}
	}
	DisposableEffect(vm) {
		onDispose {
			vm.onOverlayClosed()
		}
	}

	val config = ChatScreenConfig(
		layoutMode = ChatLayoutMode.OldestAtTop,
		showTypingIndicator = false,
		showPinActions = false,
		showInputBar = false,
		showMessageOptions = false,
		showReplyInteractions = false,
		showHighlightedMessageBackground = false,
		animateJumpToHighlightedMessage = false,
		showEmojiButton = false,
		scrollToBottomOnInputFocus = false,
		alwaysShowScrollToBottomWhenNotAtBottom = true,
		liftMessageListWithIme = false,
		showAuthorHeaderForInMessages = true,
		topBarTitle = null,
		topBarAvatarResId = R.drawable.replyplz,
		showTopBarSubtitle = false,
		showTopBarDropdown = false,
		dividerColor = Color.Black.copy(alpha = 0.1f)
	)

		ChatScreen(
			onBackClick = {
				vm.onOverlayClosed()
				onBackClick()
			},
		onProfileClick = {},
		onTopBarDropdownClick = {},
		onChatBubbleClick = {},
		onMessageActionClick = { message ->
			vm.onMessageOpenRequested(message.id)
		},
		onAvatarClick = { message ->
			vm.getActorIdForMessage(message.id)?.let(onProfileClick)
		},
		onJumpToMessage = {},
		onReply = {},
		onReplyClick = {},
		onCurrentReplyClose = {},
		onCurrentReplyClick = {},
		onCancelEdit = {},
		onMessageInputFieldValueChange = {},
		onSendClick = {},
			onRequestScrollToBottom = {
				vm.onSeeAllRequested()
				onAllRepliesRead()
			},
			onUserStartedScroll = vm::onUserStartedScroll,
			onViewportSnapshotChanged = vm::onViewportSnapshotChanged,
			onLoadMore = {},
		onPostImageClick = { _, _ -> },
		onPinMessage = {},
		onUnpinMessage = {},
		onDeleteMessage = {},
		onEditMessage = { _, _ -> },
		onRetryMessage = {},
		onUndoDelete = {},
		config = config,
		state = state,
		modifier = modifier
	)
}
