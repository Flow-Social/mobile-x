package me.floow.domain.api.models

sealed interface GetSelfResponse {
	data class Success(
		val name: String?,
		val username: String?,
		val avatarUrl: String?,
		val backgroundUrl: String?,
		val backgroundUpdatedAt: Long?,
		val biography: String?,
		val totalLikesReceived: Int = 0,
	) : GetSelfResponse

	data object Error : GetSelfResponse
}
