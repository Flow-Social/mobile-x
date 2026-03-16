package me.floow.uikit.chat.model

import androidx.compose.runtime.Immutable
import me.floow.domain.models.MessageDeliveryStatus
import me.floow.domain.models.PostImageVariant
import java.time.LocalDateTime

@Immutable
sealed interface ChatMessage {
	val uiKey: String
	val id: Long
	val clientMessageId: String?
	val messageText: String
	val dateTime: LocalDateTime
	val isPinned: Boolean
	val authorName: String?
	val authorUsername: String?
	val authorAvatarUrl: String?
	val deliveryStatus: MessageDeliveryStatus
}

@Immutable
sealed interface ChatReplyMessage : ChatMessage {
	val replyMessageId: Long
	val replyMessageText: String
}

@Immutable
data class PrimaryOutMessage(
	override val id: Long,
	override val uiKey: String = "msg_$id",
	override val clientMessageId: String? = null,
	override val messageText: String,
	override val dateTime: LocalDateTime,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null,
	override val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT
) : ChatMessage

@Immutable
data class ReplyOutMessage(
	override val id: Long,
	override val uiKey: String = "msg_$id",
	override val clientMessageId: String? = null,
	override val messageText: String,
	override val dateTime: LocalDateTime,
	override val replyMessageId: Long,
	override val replyMessageText: String,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null,
	override val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT
) : ChatReplyMessage

@Immutable
data class PrimaryInMessage(
	override val id: Long,
	override val uiKey: String = "msg_$id",
	override val clientMessageId: String? = null,
	override val messageText: String,
	override val dateTime: LocalDateTime,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null,
	override val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT
) : ChatMessage

@Immutable
data class ReplyInMessage(
	override val id: Long,
	override val uiKey: String = "msg_$id",
	override val clientMessageId: String? = null,
	override val replyMessageId: Long,
	override val replyMessageText: String,
	override val messageText: String,
	override val dateTime: LocalDateTime,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null,
	override val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT
) : ChatReplyMessage

@Immutable
data class PostPreviewMessage(
	override val id: Long,
	override val uiKey: String = "msg_$id",
	override val clientMessageId: String? = null,
	override val messageText: String,
	override val dateTime: LocalDateTime,
	val imageVariants: List<PostImageVariant>,
	val likesCount: Int,
	override val authorAvatarUrl: String?,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val deliveryStatus: MessageDeliveryStatus = MessageDeliveryStatus.SENT
) : ChatMessage
