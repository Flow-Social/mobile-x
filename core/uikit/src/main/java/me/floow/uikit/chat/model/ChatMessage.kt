package me.floow.uikit.chat.model

import androidx.compose.runtime.Immutable
import me.floow.domain.models.PostImageVariant
import java.time.LocalDateTime

@Immutable
sealed interface ChatMessage {
	val id: Long
	val messageText: String
	val dateTime: LocalDateTime
	val isPinned: Boolean
	val authorName: String?
	val authorUsername: String?
	val authorAvatarUrl: String?
}

@Immutable
sealed interface ChatReplyMessage : ChatMessage {
	val replyMessageId: Long
	val replyMessageText: String
}

@Immutable
data class PrimaryOutMessage(
	override val id: Long,
	override val messageText: String,
	override val dateTime: LocalDateTime,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null
) : ChatMessage

@Immutable
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

@Immutable
data class PrimaryInMessage(
	override val id: Long,
	override val messageText: String,
	override val dateTime: LocalDateTime,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null
) : ChatMessage

@Immutable
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

@Immutable
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
