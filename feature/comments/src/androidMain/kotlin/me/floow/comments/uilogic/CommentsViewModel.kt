package me.floow.comments.uilogic

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.StateFlow
import me.floow.domain.models.CommentId
import me.floow.domain.models.PostImageVariant
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatScreenUiState

class CommentsViewModel(
	private val stateHolder: CommentsStateHolder,
) : ViewModel(), CommentsRouteComponent {
	override val state: StateFlow<ChatScreenUiState>
		get() = stateHolder.state

	override val isInitialTargetResolved: StateFlow<Boolean>
		get() = stateHolder.isInitialTargetResolved

	override fun setInitialData(
		postId: String,
		postAuthorId: String,
		postAuthorName: String,
		postAuthorAvatarUrl: String?,
		postAuthorUsername: String?,
		postImageUrls: List<String>,
		postImageVariants: List<PostImageVariant>,
		postDescription: String?,
		postCreatedAt: Long,
		postLikesCount: Int,
		defaultTitle: String
	) = stateHolder.setInitialData(
		postId = postId,
		postAuthorId = postAuthorId,
		postAuthorName = postAuthorName,
		postAuthorAvatarUrl = postAuthorAvatarUrl,
		postAuthorUsername = postAuthorUsername,
		postImageUrls = postImageUrls,
		postImageVariants = postImageVariants,
		postDescription = postDescription,
		postCreatedAt = postCreatedAt,
		postLikesCount = postLikesCount,
		defaultTitle = defaultTitle
	)

	override fun startInitialLoad(
		primaryTargetCommentId: CommentId?,
		fallbackTargetCommentId: CommentId?
	) = stateHolder.startInitialLoad(primaryTargetCommentId, fallbackTargetCommentId)

	override fun onInitialTargetSearchStateChanged() = stateHolder.onInitialTargetSearchStateChanged()
	override fun loadInitial(anchorCommentId: CommentId?) = stateHolder.loadInitial(anchorCommentId)
	override fun loadMore() = stateHolder.loadMore()
	override fun updateMessageInputField(newValue: String) = stateHolder.updateMessageInputField(newValue)
	override fun closeCurrentReply() = stateHolder.closeCurrentReply()
	override fun addCurrentReply(chatMessage: ChatMessage) = stateHolder.addCurrentReply(chatMessage)
	override fun sendComment() = stateHolder.sendComment()
	override fun startEditingComment(messageId: Long, currentText: String) =
		stateHolder.startEditingComment(messageId, currentText)
	override fun cancelEditing() = stateHolder.cancelEditing()
	override fun editComment(messageId: Long, newText: String) = stateHolder.editComment(messageId, newText)
	override fun deleteComment(messageId: Long) = stateHolder.deleteComment(messageId)
	override fun undoDelete() = stateHolder.undoDelete()
	override fun jumpToComment(messageId: Long) = stateHolder.jumpToComment(messageId)
	override fun requestScrollToBottom() = stateHolder.requestScrollToBottom()
	override fun onVisibleMessageIdsChanged(visibleMessageIds: Set<Long>) =
		stateHolder.onVisibleMessageIdsChanged(visibleMessageIds)
	override fun getForeignAuthorIdByMessageId(messageId: Long): String? =
		stateHolder.getForeignAuthorIdByMessageId(messageId)
	override fun resolveContextMenuActions(message: ChatMessage): List<ChatContextMenuAction> =
		stateHolder.resolveContextMenuActions(message)

	override fun onCleared() {
		stateHolder.dispose()
		super.onCleared()
	}
}
