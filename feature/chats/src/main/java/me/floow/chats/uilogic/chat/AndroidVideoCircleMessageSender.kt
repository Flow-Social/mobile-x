package me.floow.chats.uilogic.chat

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.floow.domain.api.ChatsApi
import me.floow.domain.api.UploadsApi
import me.floow.domain.api.models.ChatMessageItem
import me.floow.domain.api.models.ChatMessageMediaItem
import me.floow.domain.api.models.ChatsSendMessageResponse
import me.floow.domain.api.models.CreateUploadPresignData
import me.floow.domain.api.models.CreateUploadPresignResponse
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.utils.Logger
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.VideoUploadState
import me.floow.shared.chats.uilogic.direct.RecordedClip
import java.io.File

data class SentVideoCircleMessage(
	val clientMessageId: String,
	val message: ChatMessageItemModel,
	val remoteUrl: String,
	val durationMs: Long,
	val width: Int,
	val height: Int,
)

class AndroidVideoCircleMessageSender(
	private val chatsApi: ChatsApi,
	private val uploadsApi: UploadsApi,
	private val authenticationManager: AuthenticationManager,
	private val logger: Logger,
	private val compressor: AndroidVideoCircleCompressor,
) {
	suspend fun send(
		conversationId: Long,
		clientMessageId: String,
		clip: RecordedClip,
	): Result<SentVideoCircleMessage> = runCatching {
		require(conversationId > 0L) { "conversation id is missing" }
		val normalizedClientMessageId = clientMessageId.trim().takeIf(String::isNotEmpty)
			?: error("client message id is missing")
		val file = File(clip.path)
		require(file.exists() && file.length() > 0L) { "video circle file is missing" }

		val preparedUpload = compressor.prepare(clip)
		val uploadFile = preparedUpload.file
		val contentType = "video/mp4"
		try {
			val bytes = withContext(Dispatchers.IO) { uploadFile.readBytes() }
			val presign = when (
				val response = uploadsApi.createPresign(
					CreateUploadPresignData(
						fileName = uploadFile.name.ifBlank { "video_circle.mp4" },
						contentType = contentType,
						sizeBytes = bytes.size.toLong(),
						kind = "chat_video_circle",
					)
				)
			) {
				is CreateUploadPresignResponse.Success -> response
				CreateUploadPresignResponse.Error -> error("video circle presign failed")
			}

			val uploaded = uploadsApi.uploadFile(
				uploadUrl = presign.uploadUrl,
				contentType = contentType,
				bytes = bytes,
			)
			check(uploaded) { "video circle upload failed" }

			val sendResponse = chatsApi.sendVideoCircleMessage(
				conversationId = conversationId,
				clientMessageId = normalizedClientMessageId,
				replyToMessageId = null,
				mediaUrl = presign.fileUrl,
				objectKey = presign.objectKey,
				mimeType = contentType,
				sizeBytes = bytes.size.toLong(),
				durationMs = preparedUpload.durationMs,
				width = preparedUpload.width.takeIf { it > 0 },
				height = preparedUpload.height.takeIf { it > 0 },
			)

			when (sendResponse) {
				is ChatsSendMessageResponse.Success -> {
					val serverMedia = sendResponse.message.media
					val remoteUrl = serverMedia?.url ?: presign.fileUrl
					val durationMs = serverMedia?.durationMs ?: preparedUpload.durationMs
					val width = serverMedia?.width ?: preparedUpload.width
					val height = serverMedia?.height ?: preparedUpload.height
					SentVideoCircleMessage(
						clientMessageId = normalizedClientMessageId,
						message = sendResponse.message.toItemModel(
							selfUserId = authenticationManager.getSelfUserIdOrNull(),
							fallbackLocalPath = clip.path,
							fallbackMedia = ChatMessageMediaItem(
								url = remoteUrl,
								objectKey = serverMedia?.objectKey ?: presign.objectKey,
								mimeType = serverMedia?.mimeType ?: contentType,
								sizeBytes = serverMedia?.sizeBytes ?: bytes.size.toLong(),
								durationMs = durationMs,
								width = width.takeIf { it > 0 },
								height = height.takeIf { it > 0 },
							),
						),
						remoteUrl = remoteUrl,
						durationMs = durationMs,
						width = width,
						height = height,
					)
				}
				ChatsSendMessageResponse.Conflict -> error("video circle send conflict")
				ChatsSendMessageResponse.NotFound -> error("chat not found")
				ChatsSendMessageResponse.Error -> error("video circle send failed")
			}
		} finally {
			if (preparedUpload.deleteAfterUpload) {
				withContext(Dispatchers.IO) { preparedUpload.file.delete() }
			}
		}
	}.onFailure {
		logger.d("AndroidVideoCircleMessageSender", "Failed: ${it.message}")
	}
}

private fun ChatMessageItem.toItemModel(
	selfUserId: String?,
	fallbackLocalPath: String?,
	fallbackMedia: ChatMessageMediaItem?,
): ChatMessageItemModel {
	val normalizedSelfUserId = selfUserId?.trim()?.takeIf(String::isNotEmpty)
	val isOutgoing = normalizedSelfUserId != null && sender.id == normalizedSelfUserId
	val content = when {
		contentType == "video_circle" -> {
			val mediaPayload = media ?: fallbackMedia
			ChatMessageContent.VideoCircle(
				localPath = fallbackLocalPath,
				remoteUrl = mediaPayload?.url,
				durationMs = mediaPayload?.durationMs ?: 0L,
				width = mediaPayload?.width ?: 0,
				height = mediaPayload?.height ?: 0,
				uploadState = VideoUploadState.Uploaded,
			)
		}
		else -> ChatMessageContent.Text
	}
	return ChatMessageItemModel(
		id = id,
		clientMessageId = clientMessageId,
		senderUserId = sender.id,
		senderDisplayName = sender.name ?: sender.username,
		text = text,
		createdAtMillis = createdAt,
		isOutgoing = isOutgoing,
		replyToMessageId = replyToMessageId,
		replyToMessageText = replyToMessageText,
		isPinned = isPinned,
		isDeleted = false,
		deliveryState = if (isOutgoing) ChatDeliveryState.SENT else null,
		content = content,
	)
}
