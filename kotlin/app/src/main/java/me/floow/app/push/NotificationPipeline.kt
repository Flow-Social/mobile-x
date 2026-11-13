package me.floow.app.push

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import me.floow.domain.utils.Logger

class NotificationPipeline(
	private val context: Context,
	private val renderer: ChatNotificationRenderer,
	private val ackSender: ChatPushAckSender,
	private val logger: Logger
) {
	private val deduper = ChatNotificationDeduper(context)
	private val scope = CoroutineScope(Dispatchers.IO)

	fun process(payload: ChatNotificationPayload, source: ChatNotificationSource): RenderOutcome {
		val normalized = normalize(payload, source)
		val validation = validate(normalized)
		if (!validation.isValid) {
			logger.d(
				"NotificationPipeline",
				"payload_missing_fields source=${source.name} reason=${validation.reason}"
			)
			return RenderOutcome.Failed
		}
		if (!deduper.shouldRender(normalized)) {
			logger.d(
				"NotificationPipeline",
				"dedup_skip source=${source.name} notification_id=${normalized.notificationId}"
			)
			return RenderOutcome.Suppressed
		}

		val outcome = renderer.render(normalized)
		if (outcome != RenderOutcome.Failed) {
			deduper.markRendered(normalized)
			if (normalized.isFallback) {
				logger.d("NotificationPipeline", "fallback_rendered source=${source.name}")
			} else {
				logger.d("NotificationPipeline", "render_source=${source.name}")
			}
			scope.launch { ackSender.sendAck(normalized) }
		} else {
			logger.d("NotificationPipeline", "renderer_failed source=${source.name}")
		}
		return outcome
	}

	private fun normalize(payload: ChatNotificationPayload, source: ChatNotificationSource): ChatNotificationPayload {
		val conversationId = payload.conversationId
		val messageId = payload.messageId
		val messageText = payload.messageText.trim().ifBlank { "Новое сообщение" }
		val timestamp = normalizeTimestamp(payload.messageTimestampMs)
		val notificationId = payload.notificationId.trim().ifBlank {
			if (conversationId > 0L && messageId > 0L) {
				"chat:$conversationId:$messageId"
			} else {
				payload.notificationId
			}
		}
		val isFallback = payload.isFallback || source == ChatNotificationSource.FALLBACK
		return payload.copy(
			type = payload.type.ifBlank { "chat_message" },
			notificationId = notificationId,
			messageText = messageText,
			messageTimestampMs = timestamp,
			isFallback = isFallback,
			source = source
		)
	}

	private fun validate(payload: ChatNotificationPayload): ValidationResult {
		if (payload.type != "chat_message") {
			return ValidationResult(false, "invalid_type")
		}
		if (payload.conversationId <= 0L) {
			return ValidationResult(false, "missing_conversation_id")
		}
		if (payload.messageId <= 0L) {
			return ValidationResult(false, "missing_message_id")
		}
		if (payload.messageTimestampMs <= 0L) {
			return ValidationResult(false, "missing_message_timestamp")
		}
		return ValidationResult(true, "")
	}

	private fun normalizeTimestamp(raw: Long): Long {
		if (raw <= 0L) return System.currentTimeMillis()
		return if (raw in 1L..9_999_999_999L) raw * 1000L else raw
	}

	private data class ValidationResult(
		val isValid: Boolean,
		val reason: String
	)
}
