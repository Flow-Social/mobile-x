package me.floow.domain.models

import kotlinx.serialization.Serializable
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername

typealias PostCategory = String

@Serializable
object PostCategories {
	const val NATURE: PostCategory = "NATURE"
	const val ART: PostCategory = "ART"
	const val MEMES: PostCategory = "MEMES"
	const val FOOD: PostCategory = "FOOD"
	const val TRAVEL: PostCategory = "TRAVEL"
	const val TECH: PostCategory = "TECH"
	const val LIFESTYLE: PostCategory = "LIFESTYLE"
	const val SPACE: PostCategory = "SPACE"
	const val PETS: PostCategory = "PETS"
	const val SPORT: PostCategory = "SPORT"

	val defaults: List<PostCategory> = listOf(
		NATURE,
		ART,
		MEMES,
		FOOD,
		TRAVEL,
		TECH,
		LIFESTYLE,
		SPACE,
		PETS,
		SPORT
	)
}

data class Post(
	val id: String,
	val author: PostAuthor,
	val content: PostContent,
	val category: PostCategory,
	val createdAt: Long, // timestamp
	val likesCount: Int = 0,
	val commentsCount: Int = 0,
	val commentersPreview: List<String> = emptyList()
)

data class PostAuthor(
	val id: String,
	val name: ProfileName?,
	val username: ProfileUsername?,
	val avatarUrl: String?,
	val avatarThumb: String? = null
)

data class PostContent(
	val imageUrls: List<String>,
	val description: String?,
	val imageVariants: List<PostImageVariant> = emptyList()
)

@Serializable
data class PostImageVariant(
	val thumbUrl: String? = null,
	val fullUrl: String? = null,
	val width: Int? = null,
	val height: Int? = null
) {
	val effectiveThumbUrl: String?
		get() = thumbUrl?.takeIf(String::isNotBlank)
			?: fullUrl?.takeIf(String::isNotBlank)

	val targetUrl: String?
		get() = fullUrl?.takeIf(String::isNotBlank)
			?: thumbUrl?.takeIf(String::isNotBlank)

	val detailUrl: String?
		get() = targetUrl
}

fun PostImageVariant.normalized(): PostImageVariant {
	val full = fullUrl.nonBlank()
	val thumb = thumbUrl.nonBlank()
	return copy(
		thumbUrl = thumb,
		fullUrl = full ?: thumb
	)
}

fun PostContent.resolvedImageVariants(): List<PostImageVariant> {
	if (imageVariants.isNotEmpty()) {
		return imageVariants
			.map(PostImageVariant::normalized)
			.filter { it.hasAnyUrl() }
	}
	return imageUrls
		.mapNotNull(::variantFromRawUrl)
		.map(PostImageVariant::normalized)
		.filter { it.hasAnyUrl() }
}

fun PostContent.viewerImageUrls(): List<String> {
	return resolvedImageVariants()
		.mapNotNull { variant ->
			variant.fullUrl.nonBlank()
				?: variant.thumbUrl.nonBlank()
		}
}

fun PostContent.previewImageUrls(): List<String> {
	return resolvedImageVariants()
		.mapNotNull { variant ->
			variant.thumbUrl.nonBlank()
				?: variant.fullUrl.nonBlank()
		}
}

private fun variantFromRawUrl(rawUrl: String): PostImageVariant? {
	val cleanRaw = rawUrl.nonBlank() ?: return null
	return PostImageVariant(
		fullUrl = cleanRaw
	)
}

private fun PostImageVariant.hasAnyUrl(): Boolean {
	return thumbUrl.nonBlank() != null || fullUrl.nonBlank() != null
}

private fun String?.nonBlank(): String? = this?.takeIf(String::isNotBlank)

