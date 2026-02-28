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
	val avatarUrl: String?
)

data class PostContent(
	val imageUrls: List<String>,
	val description: String?,
	val imageVariants: List<PostImageVariant> = emptyList()
)

@Serializable
data class PostImageVariant(
	val lqUrl: String? = null,
	val previewUrl: String? = null,
	val fullUrl: String? = null,
	val width: Int? = null,
	val height: Int? = null
)

private val VARIANT_SUFFIX_REGEX =
	Regex("(?i)_(full|preview|lq)\\.(jpg|jpeg|png|webp|heic|avif)(\\?.*)?$")

fun PostImageVariant.normalized(): PostImageVariant {
	val full = fullUrl.nonBlank()
	val preview = previewUrl.nonBlank() ?: full
	val lq = lqUrl.nonBlank() ?: preview ?: full
	val visible = preview ?: lq ?: full
	return copy(
		lqUrl = lq ?: visible,
		previewUrl = preview ?: visible,
		fullUrl = full ?: visible
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
				?: variant.previewUrl.nonBlank()
				?: variant.lqUrl.nonBlank()
		}
}

fun PostContent.previewImageUrls(): List<String> {
	return resolvedImageVariants()
		.mapNotNull { variant ->
			variant.previewUrl.nonBlank()
				?: variant.lqUrl.nonBlank()
				?: variant.fullUrl.nonBlank()
		}
}

private fun variantFromRawUrl(rawUrl: String): PostImageVariant? {
	val cleanRaw = rawUrl.nonBlank() ?: return null
	val match = VARIANT_SUFFIX_REGEX.find(cleanRaw)
	if (match == null) {
		return PostImageVariant(
			lqUrl = cleanRaw,
			previewUrl = cleanRaw,
			fullUrl = cleanRaw
		)
	}

	val stem = cleanRaw.substring(0, match.range.first)
	val kind = match.groupValues[1].lowercase()
	val extension = match.groupValues[2]
	val query = match.groupValues[3]
	fun url(targetKind: String): String = "${stem}_${targetKind}.${extension}${query}"

	return when (kind) {
		"full" -> PostImageVariant(
			lqUrl = url("lq"),
			previewUrl = url("preview"),
			fullUrl = cleanRaw
		)
		"preview" -> PostImageVariant(
			lqUrl = url("lq"),
			previewUrl = cleanRaw,
			fullUrl = url("full")
		)
		"lq" -> PostImageVariant(
			lqUrl = cleanRaw,
			previewUrl = url("preview"),
			fullUrl = url("full")
		)
		else -> PostImageVariant(
			lqUrl = cleanRaw,
			previewUrl = cleanRaw,
			fullUrl = cleanRaw
		)
	}
}

private fun PostImageVariant.hasAnyUrl(): Boolean {
	return lqUrl.nonBlank() != null || previewUrl.nonBlank() != null || fullUrl.nonBlank() != null
}

private fun String?.nonBlank(): String? = this?.takeIf(String::isNotBlank)
