package me.floow.domain.models

data class UserPresence(
	val userId: String,
	val isOnline: Boolean,
	val lastSeenAtMillis: Long?,
	val serverTimestampMillis: Long
)
