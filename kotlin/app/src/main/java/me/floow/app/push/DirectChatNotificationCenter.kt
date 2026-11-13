package me.floow.app.push

import android.content.Context
import androidx.core.app.NotificationManagerCompat

object DirectChatNotificationCenter {
	private const val PREFS_NAME = "direct_chat_notifications"
	private const val CONVERSATION_NOTIFICATION_IDS_PREFIX = "conversation_notification_ids:"
	private const val INTERLOCUTOR_NOTIFICATION_IDS_PREFIX = "interlocutor_notification_ids:"
	private const val MAX_STORED_NOTIFICATION_IDS_PER_CONVERSATION = 40

	fun registerConversationNotification(
		context: Context,
		conversationId: Long,
		notificationTag: String?,
		notificationId: Int
	) {
		if (conversationId <= 0L) return
		val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		val key = notificationIdsKey(conversationId)
		val existing = prefs.getString(key, "").orEmpty()
			.split('|')
			.mapNotNull { decodeNotificationKey(it.trim()) }
			.toMutableList()
		existing.add(NotificationEntry(notificationTag, notificationId))
		if (existing.size > MAX_STORED_NOTIFICATION_IDS_PER_CONVERSATION) {
			existing.subList(0, existing.size - MAX_STORED_NOTIFICATION_IDS_PER_CONVERSATION).clear()
		}
		val packed = existing.joinToString("|") { entry ->
			encodeNotificationKey(entry.tag, entry.id)
		}
		prefs.edit().putString(key, packed).apply()
	}

	fun registerInterlocutorNotification(
		context: Context,
		interlocutorId: String,
		notificationTag: String?,
		notificationId: Int
	) {
		val normalizedInterlocutorId = interlocutorId.trim()
		if (normalizedInterlocutorId.isEmpty()) return
		val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		val key = interlocutorNotificationIdsKey(normalizedInterlocutorId)
		val existing = prefs.getString(key, "").orEmpty()
			.split('|')
			.mapNotNull { decodeNotificationKey(it.trim()) }
			.toMutableList()
		existing.add(NotificationEntry(notificationTag, notificationId))
		if (existing.size > MAX_STORED_NOTIFICATION_IDS_PER_CONVERSATION) {
			existing.subList(0, existing.size - MAX_STORED_NOTIFICATION_IDS_PER_CONVERSATION).clear()
		}
		val packed = existing.joinToString("|") { entry ->
			encodeNotificationKey(entry.tag, entry.id)
		}
		prefs.edit().putString(key, packed).apply()
	}

	fun cancelConversationNotifications(
		context: Context,
		conversationId: Long
	) {
		if (conversationId <= 0L) return
		val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		val key = notificationIdsKey(conversationId)
		val ids = prefs.getString(key, "").orEmpty()
			.split('|')
			.mapNotNull { value -> decodeNotificationKey(value.trim()) }
		val manager = NotificationManagerCompat.from(context)
		ids.forEach { entry ->
			if (entry.tag != null) {
				manager.cancel(entry.tag, entry.id)
			} else {
				manager.cancel(entry.id)
			}
		}
		prefs.edit().remove(key).apply()
	}

	fun cancelInterlocutorNotifications(
		context: Context,
		interlocutorId: String
	) {
		val normalizedInterlocutorId = interlocutorId.trim()
		if (normalizedInterlocutorId.isEmpty()) return
		val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		val key = interlocutorNotificationIdsKey(normalizedInterlocutorId)
		val ids = prefs.getString(key, "").orEmpty()
			.split('|')
			.mapNotNull { value -> decodeNotificationKey(value.trim()) }
		val manager = NotificationManagerCompat.from(context)
		ids.forEach { entry ->
			if (entry.tag != null) {
				manager.cancel(entry.tag, entry.id)
			} else {
				manager.cancel(entry.id)
			}
		}
		prefs.edit().remove(key).apply()
	}

	private fun notificationIdsKey(conversationId: Long): String {
		return "$CONVERSATION_NOTIFICATION_IDS_PREFIX$conversationId"
	}

	private fun interlocutorNotificationIdsKey(interlocutorId: String): String {
		return "$INTERLOCUTOR_NOTIFICATION_IDS_PREFIX$interlocutorId"
	}

	private fun encodeNotificationKey(tag: String?, id: Int): String {
		val normalizedTag = tag?.trim().orEmpty()
		return if (normalizedTag.isEmpty()) id.toString() else "$normalizedTag#$id"
	}

	private fun decodeNotificationKey(raw: String): NotificationEntry? {
		val value = raw.trim()
		if (value.isEmpty()) return null
		val parts = value.split('#', limit = 2)
		return if (parts.size == 2) {
			val id = parts[1].toIntOrNull() ?: return null
			NotificationEntry(parts[0].ifBlank { null }, id)
		} else {
			val id = value.toIntOrNull() ?: return null
			NotificationEntry(null, id)
		}
	}

	private data class NotificationEntry(val tag: String?, val id: Int)
}
