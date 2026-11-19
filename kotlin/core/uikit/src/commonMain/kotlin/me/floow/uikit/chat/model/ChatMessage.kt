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
	val thumbUrl: String? = null,
	val fullUrl: String? = null,
	val width: Int? = null,
	val height: Int? = null
) {
	val lqUrl: String? get() = thumbUrl
	val previewUrl: String? get() = fullUrl
}

fun ChatPostImageVariant.normalized(): ChatPostImageVariant {
	val full = fullUrl?.takeIf(String::isNotBlank)
	val thumb = thumbUrl?.takeIf(String::isNotBlank)
	return copy(
		thumbUrl = thumb,
		fullUrl = full ?: thumb
	)
}

fun ChatPostImageVariant.hasVisibleUrl(): Boolean {
	return thumbUrl?.isNotBlank() == true ||
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
	val authorAvatarThumb: String? get() = null
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
	override val authorAvatarThumb: String? = null,
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
	override val authorAvatarThumb: String? = null,
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
	override val authorAvatarThumb: String? = null,
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
	override val authorAvatarThumb: String? = null,
	override val deliveryStatus: ChatMessageDeliveryStatus = ChatMessageDeliveryStatus.SENT
) : ChatReplyMessage

@Immutable
enum class VideoCircleUploadStatus {
	Pending,
	Uploading,
	Uploaded,
	Failed,
}

@Immutable
data class VideoCircleOutMessage(
	override val id: Long,
	override val uiKey: String = "msg_$id",
	override val clientMessageId: String? = null,
	override val messageText: String = "",
	override val createdAtMillis: Long,
	val isOutgoing: Boolean = true,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null,
	override val deliveryStatus: ChatMessageDeliveryStatus = ChatMessageDeliveryStatus.SENDING,
	val localVideoPath: String?,
	val remoteVideoUrl: String? = null,
	val durationMs: Long = 0L,
	val thumbnailPath: String? = null,
	val remoteThumbnailUrl: String? = null,
	val videoWidth: Int = 0,
	val videoHeight: Int = 0,
	val uploadStatus: VideoCircleUploadStatus = VideoCircleUploadStatus.Pending,
) : ChatMessage {
	/** Returns the best available playable source (remote preferred, falls back to local). */
	val playableSource: String? get() = remoteVideoUrl?.takeIf(String::isNotBlank) ?: localVideoPath
}

@Immutable
data class VoiceMessage(
	override val id: Long,
	override val uiKey: String = "msg_$id",
	override val clientMessageId: String? = null,
	override val messageText: String = "",
	override val createdAtMillis: Long,
	val isOutgoing: Boolean = true,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null,
	override val deliveryStatus: ChatMessageDeliveryStatus = ChatMessageDeliveryStatus.SENT,
	val localAudioPath: String? = null,
	val remoteAudioUrl: String? = null,
	val durationMs: Long = 0L,
	val waveform: List<Int> = emptyList(),
	val uploadStatus: VideoCircleUploadStatus = VideoCircleUploadStatus.Uploaded,
) : ChatMessage {
	val playableSource: String? get() = remoteAudioUrl ?: localAudioPath
}

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
	override val authorAvatarThumb: String? = null,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val deliveryStatus: ChatMessageDeliveryStatus = ChatMessageDeliveryStatus.SENT
) : ChatMessage

@Immutable
data class ChatImageItem(
	val id: Long,
	val clientMessageId: String? = null,
	val localPath: String? = null,
	val remoteUrl: String? = null,
	val width: Int = 0,
	val height: Int = 0,
	val position: Int = 0,
	val uploadStatus: VideoCircleUploadStatus = VideoCircleUploadStatus.Uploaded,
	val thumbnailUrl: String? = null,
	val sizeBytes: Long = 0L,
) {
	val displayUrl: String? get() = remoteUrl ?: localPath ?: thumbnailUrl
}

@Immutable
data class ChatDraftImage(
	val id: String,
	val uri: String,
)

@Immutable
data class ImageMessage(
	override val id: Long,
	override val uiKey: String = "msg_$id",
	override val clientMessageId: String? = null,
	override val messageText: String = "",
	override val createdAtMillis: Long,
	val isOutgoing: Boolean = true,
	override val isPinned: Boolean = false,
	override val authorName: String? = null,
	override val authorUsername: String? = null,
	override val authorAvatarUrl: String? = null,
	override val authorAvatarThumb: String? = null,
	override val deliveryStatus: ChatMessageDeliveryStatus = ChatMessageDeliveryStatus.SENT,
	val images: List<ChatImageItem> = emptyList(),
	val groupId: String? = null,
) : ChatMessage
