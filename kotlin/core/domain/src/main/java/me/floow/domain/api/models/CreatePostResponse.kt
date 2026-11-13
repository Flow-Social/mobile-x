package me.floow.domain.api.models

sealed interface CreatePostResponse {
	data class Success(
		val createdPost: PostItem? = null
	) : CreatePostResponse

	data class Error(
		val message: String? = null
	) : CreatePostResponse
}
