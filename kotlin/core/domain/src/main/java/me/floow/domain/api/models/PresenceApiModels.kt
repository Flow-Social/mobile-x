package me.floow.domain.api.models

data class PresenceItem(
	val userId: String,
	val isOnline: Boolean,
	val lastSeenAtMillis: Long?,
	val serverTimestampMillis: Long,
	val expiresAtMillis: Long? = null
)

sealed interface PresenceGetResponse {
	data class Success(
		val items: List<PresenceItem>
	) : PresenceGetResponse

	data object Error : PresenceGetResponse
}

sealed interface PresenceUpdateResponse {
	data class Success(
		val item: PresenceItem
	) : PresenceUpdateResponse

	data object Error : PresenceUpdateResponse
}
