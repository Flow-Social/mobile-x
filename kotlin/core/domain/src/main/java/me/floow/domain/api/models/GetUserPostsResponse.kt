package me.floow.domain.api.models

sealed interface GetUserPostsResponse {
	data class Success(
		val posts: List<PostItem>
	) : GetUserPostsResponse

	data object Error : GetUserPostsResponse
}

