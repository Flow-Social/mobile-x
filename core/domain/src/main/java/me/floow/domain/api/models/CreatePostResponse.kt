package me.floow.domain.api.models

sealed interface CreatePostResponse {
	data class Success(
		val createdPost: PostItem? = null
	) : CreatePostResponse

	data object Error : CreatePostResponse
}

