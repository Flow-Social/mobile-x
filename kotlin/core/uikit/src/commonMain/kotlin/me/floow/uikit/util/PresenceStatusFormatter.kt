package me.floow.uikit.util

fun formatPresenceStatusLabel(
	isOnline: Boolean,
	lastSeenAtMillis: Long?,
	nowMillis: Long = currentPresenceStatusEpochMillis(),
): String {
	return platformFormatPresenceStatusLabel(
		isOnline = isOnline,
		lastSeenAtMillis = lastSeenAtMillis,
		nowMillis = nowMillis,
	)
}

internal expect fun platformFormatPresenceStatusLabel(
	isOnline: Boolean,
	lastSeenAtMillis: Long?,
	nowMillis: Long,
): String

internal expect fun currentPresenceStatusEpochMillis(): Long
