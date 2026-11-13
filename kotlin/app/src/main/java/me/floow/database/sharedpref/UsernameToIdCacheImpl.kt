package me.floow.database.sharedpref

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.floow.domain.cache.UsernameToIdCache

class UsernameToIdCacheImpl(
	context: Context,
	private val ttlMs: Long = 24 * 60 * 60 * 1000L
) : UsernameToIdCache {
	@Serializable
	private data class CachedEntry(
		val userId: String,
		val savedAtMs: Long
	)

	@Serializable
	private data class CachePayload(
		val entries: Map<String, CachedEntry> = emptyMap()
	)

	private companion object {
		private const val PREFS_NAME = "username_to_id_cache"
		private const val KEY_ENTRIES = "entries_json"
	}

	private val sharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
	private val lock = Any()
	private var inMemoryEntries: MutableMap<String, CachedEntry>? = null

	override fun getUserId(username: String): String? {
		val normalized = normalizeUsername(username) ?: return null
		synchronized(lock) {
			val entries = entriesLocked()
			val entry = entries[normalized] ?: return null
			val nowMs = System.currentTimeMillis()
			if (nowMs - entry.savedAtMs > ttlMs) {
				entries.remove(normalized)
				persistLocked(entries)
				return null
			}
			return entry.userId.takeIf { it.isNotBlank() }
		}
	}

	override fun put(username: String, userId: String) {
		val normalized = normalizeUsername(username) ?: return
		val normalizedUserId = userId.trim().takeIf { it.isNotEmpty() } ?: return
		synchronized(lock) {
			val entries = entriesLocked()
			entries[normalized] = CachedEntry(
				userId = normalizedUserId,
				savedAtMs = System.currentTimeMillis()
			)
			persistLocked(entries)
		}
	}

	override fun clear() {
		synchronized(lock) {
			inMemoryEntries = mutableMapOf()
			sharedPreferences.edit().remove(KEY_ENTRIES).apply()
		}
	}

	private fun entriesLocked(): MutableMap<String, CachedEntry> {
		val current = inMemoryEntries
		if (current != null) return current

		val payload = sharedPreferences.getString(KEY_ENTRIES, null)
			?.let { raw ->
				runCatching {
					Json.decodeFromString<CachePayload>(raw)
				}.getOrNull()
			}
			?: CachePayload()
		return payload.entries.toMutableMap().also { loaded ->
			pruneExpiredLocked(loaded)
			inMemoryEntries = loaded
		}
	}

	private fun pruneExpiredLocked(entries: MutableMap<String, CachedEntry>) {
		val nowMs = System.currentTimeMillis()
		val iterator = entries.iterator()
		var changed = false
		while (iterator.hasNext()) {
			val entry = iterator.next().value
			if (nowMs - entry.savedAtMs > ttlMs) {
				iterator.remove()
				changed = true
			}
		}
		if (changed) {
			persistLocked(entries)
		}
	}

	private fun persistLocked(entries: Map<String, CachedEntry>) {
		val payload = CachePayload(entries = entries)
		val raw = Json.encodeToString(payload)
		sharedPreferences.edit().putString(KEY_ENTRIES, raw).apply()
	}

	private fun normalizeUsername(username: String): String? {
		val normalized = username.trim().lowercase()
		return normalized.takeIf { it.isNotEmpty() }
	}
}
