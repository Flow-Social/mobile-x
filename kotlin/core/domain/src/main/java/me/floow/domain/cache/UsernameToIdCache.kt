package me.floow.domain.cache

interface UsernameToIdCache {
	fun getUserId(username: String): String?
	fun put(username: String, userId: String)
	fun clear()
}
