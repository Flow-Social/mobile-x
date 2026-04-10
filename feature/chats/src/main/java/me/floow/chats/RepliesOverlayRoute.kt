package me.floow.chats

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import me.floow.chats.uilogic.replies.RepliesOverlayOpenMode
import me.floow.domain.models.CommentId
import me.floow.domain.models.resolveReplyTargetCommentCandidates
import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.model.RepliesActorTarget
import me.floow.shared.chats.model.RepliesNavigationTarget
import me.floow.shared.chats.ui.SharedRepliesRoute
import me.floow.shared.chats.uilogic.replies.RepliesStateHolder
import me.floow.uikit.util.SetStatusBarStyle

data class ReplyThreadNavigationTarget(
	val postId: String,
	val commentId: String,
	val replyToCommentId: String?,
	val threadId: String? = null,
)

fun ReplyThreadNavigationTarget.resolveCommentTargetCandidates(): List<CommentId> {
	return resolveReplyTargetCommentCandidates(
		commentId = commentId,
		replyToCommentId = replyToCommentId,
		threadId = threadId,
	)
}

@Composable
fun RepliesOverlayRoute(
	stateHolder: RepliesStateHolder,
	onBackClick: () -> Unit,
	onOpenThreadClick: (ReplyThreadNavigationTarget) -> Unit,
	onAllRepliesRead: () -> Unit = {},
	onProfileClick: (String) -> Unit = {},
	openMode: RepliesOverlayOpenMode = RepliesOverlayOpenMode.FROM_UNREAD,
	messageLinkAnchorSeq: Long? = null,
	modifier: Modifier = Modifier,
) {
	val statusBarColor = MaterialTheme.colorScheme.background
	val useDarkStatusIcons = statusBarColor.luminance() > 0.5f

	SetStatusBarStyle(
		color = statusBarColor,
		darkIcons = useDarkStatusIcons,
	)

	SharedRepliesRoute(
		stateHolder = stateHolder,
		onBackClick = onBackClick,
		onOpenThread = { target ->
			onOpenThreadClick(target.toLegacyNavigationTarget())
		},
		onOpenActorProfile = { target: RepliesActorTarget ->
			onProfileClick(target.userId)
		},
		onShowMessage = {},
		onSeeAll = onAllRepliesRead,
		openMode = openMode.toSharedOpenMode(),
		anchorSeq = messageLinkAnchorSeq,
		modifier = modifier,
	)
}

private fun RepliesNavigationTarget.toLegacyNavigationTarget(): ReplyThreadNavigationTarget {
	return ReplyThreadNavigationTarget(
		postId = postId,
		commentId = commentId,
		replyToCommentId = replyToCommentId,
		threadId = threadId,
	)
}

private fun RepliesOverlayOpenMode.toSharedOpenMode(): ChatOpenMode = when (this) {
	RepliesOverlayOpenMode.FROM_UNREAD -> ChatOpenMode.FROM_UNREAD
	RepliesOverlayOpenMode.FROM_LAST_SEEN -> ChatOpenMode.FROM_LAST_SEEN
	RepliesOverlayOpenMode.FROM_MESSAGE_LINK -> ChatOpenMode.FROM_MESSAGE_LINK
}
