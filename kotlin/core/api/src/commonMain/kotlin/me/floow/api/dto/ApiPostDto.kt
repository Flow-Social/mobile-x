package me.floow.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
internal data class ApiPostAuthor(
	val id: String,
	val name: String? = null,
	val username: String? = null,
	@SerialName("avatar")
	val avatarUrl: String? = null,
	@SerialName("avatar_thumb")
	val avatarThumb: String? = null
)

@Serializable
internal data class ApiImageVariant(
	@SerialName("thumb_url")
	val thumbUrl: String? = null,
	@SerialName("full_url")
	val fullUrl: String? = null,
	val width: Int? = null,
	val height: Int? = null
)

@Serializable
internal data class ApiPostContent(
	@SerialName("image_urls")
	val imageUrls: List<String> = emptyList(),
	@SerialName("image_variants")
	val imageVariants: List<ApiImageVariant> = emptyList(),
	val description: String? = null
)

internal fun ApiPostContent.resolveImageUrls(): List<String> {
	val fromVariants = imageVariants.mapNotNull { variant ->
		variant.fullUrl?.ifBlank { null }
			?: variant.thumbUrl?.ifBlank { null }
	}
	if (fromVariants.isNotEmpty()) return fromVariants
	return imageUrls.filter { it.isNotBlank() }
}

internal fun ApiPostContent.resolveImageVariants(): List<ApiImageVariant> {
	if (imageVariants.isNotEmpty()) return imageVariants
	return imageUrls.filter { it.isNotBlank() }.map { url ->
		ApiImageVariant(
			thumbUrl = null,
			fullUrl = url
		)
	}
}

@Serializable
internal data class ApiPost(
	val id: String,
	val author: ApiPostAuthor,
	val content: ApiPostContent,
	val category: String,
	@SerialName("created_at")
	val createdAt: Long,
	@SerialName("likes_count")
	val likesCount: Int = 0,
	@SerialName("comments_count")
	val commentsCount: Int = 0,
	@SerialName("commenters_preview")
	val commentersPreview: List<String> = emptyList()
)
