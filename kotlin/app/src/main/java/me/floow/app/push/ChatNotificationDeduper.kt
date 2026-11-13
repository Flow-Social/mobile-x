package me.floow.app.push

import android.content.Context

class ChatNotificationDeduper(
	private val context: Context
) {
	private val lock = Any()
	private val recentByConversation = mutableMapOf<Long, MessageMeta>()

	fun shouldRender(payload: ChatNotificationPayload): Boolean {
		synchronized(lock) {
			if (payload.isFallback && wasSeen(payload.notificationId)) return false
			val meta = recentByConversation[payload.conversationId] ?: return true
			val incomingTimestamp = payload.messageTimestampMs
			if (incomingTimestamp < meta.timestampMs) return false
			if (incomingTimestamp == meta.timestampMs) {
				if (payload.messageId < meta.messageId) return false
				if (payload.messageId == meta.messageId && payload.messageText.hashCode() == meta.textHash) {
					return false
				}
			}
			return true
		}
	}

	fun markRendered(payload: ChatNotificationPayload) {
		synchronized(lock) {
			val timestamp = payload.messageTimestampMs.takeIf { it > 0L } ?: System.currentTimeMillis()
			recentByConversation[payload.conversationId] = MessageMeta(
				messageId = payload.messageId,
				textHash = payload.messageText.hashCode(),
				timestampMs = timestamp
			)
			storeSeen(payload.notificationId)
		}
	}

	fun wasSeen(notificationId: String): Boolean {
		synchronized(lock) {
			val now = System.currentTimeMillis()
			val entries = readAndPruneEntries(now)
			return entries.containsKey(notificationId)
		}
	}

	private fun storeSeen(notificationId: String) {
		val normalized = notificationId.trim()
		if (normalized.isEmpty()) return
		val now = System.currentTimeMillis()
		val entries = readAndPruneEntries(now)
		entries[normalized] = now
		trimBySize(entries)
		writeEntries(entries)
	}

	private fun readAndPruneEntries(now: Long): MutableMap<String, Long> {
		val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		val raw = prefs.getString(RECENT_IDS_KEY, "").orEmpty()
		if (raw.isBlank()) return mutableMapOf()
		val entries = mutableMapOf<String, Long>()
		var changed = false
		raw.split('|')
			.map(String::trim)
			.filter(String::isNotEmpty)
			.forEach { token ->
				val parts = token.split('@', limit = 2)
				if (parts.size != 2) {
					changed = true
					return@forEach
				}
				val id = parts[0].trim()
				val ts = parts[1].toLongOrNull() ?: 0L
				if (id.isEmpty() || ts <= 0L) {
					changed = true
					return@forEach
				}
				if (now - ts <= WINDOW_MS) {
					entries[id] = ts
				} else {
					changed = true
				}
			}
		if (changed) {
			writeEntries(entries)
		}
		return entries
	}

	private fun trimBySize(entries: MutableMap<String, Long>) {
		if (entries.size <= MAX_IDS) return
		val sorted = entries.entries.sortedBy { it.value }
		val toDrop = entries.size - MAX_IDS
		sorted.take(toDrop).forEach { entries.remove(it.key) }
	}

	private fun writeEntries(entries: Map<String, Long>) {
		val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
		val packed = entries.entries.joinToString("|") { "${it.key}@${it.value}" }
		prefs.edit().putString(RECENT_IDS_KEY, packed).apply()
	}

	private data class MessageMeta(
		val messageId: Long,
		val textHash: Int,
		val timestampMs: Long
	)

	private companion object {
		const val PREFS_NAME = "chat_notification_dedup"
		const val RECENT_IDS_KEY = "recent_notification_ids"
		const val MAX_IDS = 400
		const val WINDOW_MS = 5 * 60 * 1000L
	}
}
