package me.floow.uikit.chat.model

import androidx.compose.runtime.Immutable

@Immutable
enum class ChatMessageDeliveryStatus {
	SENDING,
	SENT,
	FAILED
}

@Immutable
data class ChatPostImageVariant(
	val lqUrl: String? = null,
	val previewUrl: String? = null,
	val fullUrl: String? = null,
	val width: Int? = null,
	val height: Int? = null
)

fun ChatPostImageVariant.normalized(): ChatPostImageVariant {
	val full = fullUrl?.takeIf(String::isNotBlank)
	val preview = previewUrl?.takeIf(String::isNotBlank) ?: full
	val lq = lqUrl?.takeIf(String::isNotBlank) ?: preview ?: full
	val visible = preview ?: lq ?: full
	return copy(
		lqUrl = lq ?: visible,
		previewUrl = preview ?: visible,
		fullUrl = full ?: visible
	)
}

fun ChatPostImageVariant.hasVisibleUrl(): Boolean {
	return lqUrl?.isNotBlank() == true ||
		previewUrl?.isNotBlank() == true ||
		fullUrl?.isNotBlank() == true
}

@Immutable
sealed interface ChatMessage {
	val uiKey: String
	val id: Long
	val clientMessageId: String?
	val messageText: String
	val createdAtMillis: Long
	val isPinned: Boolean
	val authorName: String?
	val authorUsername: String?
	val authorAvatarUrl: String?
	val deliveryStatus: ChatMessageDeliveryStatus
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
	override val createdAtMillis: Long,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null,
	override val deliveryStatus: ChatMessageDeliveryStatus = ChatMessageDeliveryStatus.SENT
) : ChatMessage

@Immutable
data class ReplyOutMessage(
	override val id: Long,
	override val uiKey: String = "msg_$id",
	override val clientMessageId: String? = null,
	override val messageText: String,
	override val createdAtMillis: Long,
	override val replyMessageId: Long,
	override val replyMessageText: String,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null,
	override val deliveryStatus: ChatMessageDeliveryStatus = ChatMessageDeliveryStatus.SENT
) : ChatReplyMessage

@Immutable
data class PrimaryInMessage(
	override val id: Long,
	override val uiKey: String = "msg_$id",
	override val clientMessageId: String? = null,
	override val messageText: String,
	override val createdAtMillis: Long,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null,
	override val deliveryStatus: ChatMessageDeliveryStatus = ChatMessageDeliveryStatus.SENT
) : ChatMessage

@Immutable
data class ReplyInMessage(
	override val id: Long,
	override val uiKey: String = "msg_$id",
	override val clientMessageId: String? = null,
	override val replyMessageId: Long,
	override val replyMessageText: String,
	override val messageText: String,
	override val createdAtMillis: Long,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null,
	override val deliveryStatus: ChatMessageDeliveryStatus = ChatMessageDeliveryStatus.SENT
) : ChatReplyMessage

@Immutable
data class PostPreviewMessage(
	override val id: Long,
	override val uiKey: String = "msg_$id",
	override val clientMessageId: String? = null,
	override val messageText: String,
	override val createdAtMillis: Long,
	val imageVariants: List<ChatPostImageVariant>,
	val likesCount: Int,
	override val authorAvatarUrl: String?,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val deliveryStatus: ChatMessageDeliveryStatus = ChatMessageDeliveryStatus.SENT
) : ChatMessage
