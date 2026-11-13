package me.floow.domain.api.models

import me.floow.domain.models.PostImageVariant

data class FeedItem(
	val id: String,
	val authorId: String,
	val authorName: String?,
	val authorUsername: String?,
	val authorAvatarUrl: String?,
	val authorAvatarThumb: String? = null,
	val imageUrls: List<String>,
	val description: String?,
	val category: String,
	val createdAt: Long,
	val reason: String,
	val likesCount: Int = 0,
	val commentsCount: Int = 0,
	val commentersPreview: List<String> = emptyList(),
	val imageVariants: List<PostImageVariant> = emptyList()
)
