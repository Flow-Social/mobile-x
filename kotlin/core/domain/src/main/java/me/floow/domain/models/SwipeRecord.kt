package me.floow.domain.models

import kotlinx.serialization.Serializable

@Serializable
data class SwipeRecord(
	val postId: String,
	val authorUsername: String,
	val category: PostCategory,
	val isLiked: Boolean,
	val timestamp: Long
)
