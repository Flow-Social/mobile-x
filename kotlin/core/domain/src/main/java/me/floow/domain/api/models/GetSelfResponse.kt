package me.floow.domain.api.models

sealed interface GetSelfResponse {
	data class Success(
		val id: String? = null,
		val name: String?,
		val username: String?,
		val avatarUrl: String?,
		val avatarThumb: String? = null,
		val backgroundUrl: String?,
		val backgroundThumb: String? = null,
		val backgroundUpdatedAt: Long?,
		val biography: String?,
		val totalLikesReceived: Int = 0,
	) : GetSelfResponse

	data object Error : GetSelfResponse
}
