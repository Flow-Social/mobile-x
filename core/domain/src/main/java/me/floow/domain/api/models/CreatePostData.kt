package me.floow.domain.api.models

import me.floow.domain.models.PostCategory

data class CreatePostData(
	val imageUrls: List<String>,
	val description: String?,
	val category: PostCategory
)
