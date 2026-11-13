package me.floow.database.sharedpref

import me.floow.domain.cache.UsernameToIdCache
import me.floow.domain.utils.currentTimeMillis
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

class InMemoryUsernameToIdCache(
    private val ttlMs: Long = 24 * 60 * 60 * 1000L
) : UsernameToIdCache {
    private data class CachedEntry(val userId: String, val savedAtMs: Long)
    private val entries = MutableStateFlow<Map<String, CachedEntry>>(emptyMap())

    override fun getUserId(username: String): String? {
        val normalized = username.trim().lowercase().removePrefix("@")
        if (normalized.isBlank()) return null
        val current = entries.value
        val entry = current[normalized] ?: return null
        if (currentTimeMillis() - entry.savedAtMs > ttlMs) {
            entries.update { it - normalized }
            return null
        }
        return entry.userId
    }

    override fun put(username: String, userId: String) {
        val normalized = username.trim().lowercase().removePrefix("@")
        if (normalized.isBlank()) return
        entries.update { it + (normalized to CachedEntry(userId, currentTimeMillis())) }
    }

    override fun clear() {
        entries.value = emptyMap()
    }
}
