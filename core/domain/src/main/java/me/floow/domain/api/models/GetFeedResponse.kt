package me.floow.domain.api.models

sealed interface GetFeedResponse {
	data class Success(
		val items: List<FeedItem>
	) : GetFeedResponse

	data object Error : GetFeedResponse
}

