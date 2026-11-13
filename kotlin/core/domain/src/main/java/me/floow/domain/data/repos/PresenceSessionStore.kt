package me.floow.domain.data.repos

interface PresenceSessionStore {
	fun getOrCreateSessionId(): String
}
