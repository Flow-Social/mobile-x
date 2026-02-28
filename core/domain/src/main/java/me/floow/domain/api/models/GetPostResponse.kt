package me.floow.domain.api.models

sealed interface GetPostResponse {
	data class Success(
		val post: PostItem
	) : GetPostResponse

	data object Error : GetPostResponse
}
