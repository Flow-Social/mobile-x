package me.floow.comments.uilogic

import kotlinx.coroutines.flow.StateFlow
import me.floow.domain.models.CommentId
import me.floow.domain.models.PostImageVariant
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatScreenUiState

interface CommentsRouteComponent {
	val state: StateFlow<ChatScreenUiState>
	val isInitialTargetResolved: StateFlow<Boolean>

	fun setInitialData(
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
	)

	fun startInitialLoad(
		primaryTargetCommentId: CommentId?,
		fallbackTargetCommentId: CommentId?
	)

	fun onInitialTargetSearchStateChanged()
	fun loadInitial(anchorCommentId: CommentId? = null)
	fun loadMore()
	fun updateMessageInputField(newValue: String)
	fun closeCurrentReply()
	fun addCurrentReply(chatMessage: ChatMessage)
	fun sendComment()
	fun startEditingComment(messageId: Long, currentText: String)
	fun cancelEditing()
	fun editComment(messageId: Long, newText: String)
	fun deleteComment(messageId: Long)
	fun undoDelete()
	fun jumpToComment(messageId: Long)
	fun requestScrollToBottom()
	fun onVisibleMessageIdsChanged(visibleMessageIds: Set<Long>)
	fun getForeignAuthorIdByMessageId(messageId: Long): String?
	fun resolveContextMenuActions(message: ChatMessage): List<ChatContextMenuAction>
}
