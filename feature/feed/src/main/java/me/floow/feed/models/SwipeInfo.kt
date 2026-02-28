package me.floow.feed.models

data class SwipeInfo(
	val isLiked: Boolean,
	val category: String,
	val authorUsername: String,
	val categoryDelta: Float,
	val authorDelta: Float
)
