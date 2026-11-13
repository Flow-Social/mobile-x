package me.floow.shared.post.ui

import androidx.compose.runtime.Immutable
import me.floow.domain.models.PostImageVariant

@Immutable
internal data class ProfileModalCarouselItem(
    val thumbUrl: String?,
    val targetUrl: String?,
    val width: Int?,
    val height: Int?,
) {
    val aspectRatio: Float
        get() = width?.takeIf { it > 0 }
            ?.let { safeWidth ->
                height?.takeIf { it > 0 }?.let { safeHeight ->
                    safeWidth.toFloat() / safeHeight.toFloat()
                }
            }
            ?: (3f / 4f)
}

internal fun buildProfileModalCarouselItems(
    imageUrls: List<String>,
    imageVariants: List<PostImageVariant>,
): List<ProfileModalCarouselItem> {
    return imageUrls.indices.mapNotNull { index ->
        val variant = imageVariants.getOrNull(index)
        val fallback = imageUrls.getOrNull(index)?.trim()?.takeIf(String::isNotEmpty)
        val thumbUrl = variant?.thumbUrl ?: fallback
        val targetUrl = variant?.targetUrl ?: variant?.fullUrl ?: fallback

        if (thumbUrl == null && targetUrl == null) {
            null
        } else {
            ProfileModalCarouselItem(
                thumbUrl = thumbUrl,
                targetUrl = targetUrl,
                width = variant?.width,
                height = variant?.height,
            )
        }
    }
}
