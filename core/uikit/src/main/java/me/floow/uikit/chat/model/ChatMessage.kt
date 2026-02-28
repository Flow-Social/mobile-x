package me.floow.uikit.chat.model

import me.floow.domain.models.PostImageVariant
import java.time.LocalDateTime

sealed interface ChatMessage {
	val id: Long
	val messageText: String
	val dateTime: LocalDateTime
	val isPinned: Boolean
	val authorName: String?
	val authorUsername: String?
	val authorAvatarUrl: String?
}

sealed interface ChatReplyMessage : ChatMessage {
	val replyMessageId: Long
	val replyMessageText: String
}

data class PrimaryOutMessage(
	override val id: Long,
	override val messageText: String,
	override val dateTime: LocalDateTime,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null
) : ChatMessage

data class ReplyOutMessage(
	override val id: Long,
	override val messageText: String,
	override val dateTime: LocalDateTime,
	override val replyMessageId: Long,
	override val replyMessageText: String,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null
) : ChatReplyMessage

data class PrimaryInMessage(
	override val id: Long,
	override val messageText: String,
	override val dateTime: LocalDateTime,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null
) : ChatMessage

data class ReplyInMessage(
	override val id: Long,
	override val replyMessageId: Long,
	override val replyMessageText: String,
	override val messageText: String,
	override val dateTime: LocalDateTime,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null
) : ChatReplyMessage

data class PostPreviewMessage(
	override val id: Long,
	override val messageText: String,
	override val dateTime: LocalDateTime,
	val imageVariants: List<PostImageVariant>,
	val likesCount: Int,
	override val authorAvatarUrl: String?,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null
) : ChatMessage
