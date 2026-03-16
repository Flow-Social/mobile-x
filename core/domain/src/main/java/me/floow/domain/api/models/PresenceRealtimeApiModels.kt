package me.floow.domain.api.models

sealed interface PresenceRealtimeEvent {
	data class Snapshot(
		val items: List<PresenceItem>,
		val serverTimestampMillis: Long
	) : PresenceRealtimeEvent

	data class PresenceChanged(
		val item: PresenceItem
	) : PresenceRealtimeEvent

	data class ResyncRequired(
		val serverTimestampMillis: Long
	) : PresenceRealtimeEvent
}
