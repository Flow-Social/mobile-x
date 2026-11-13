package me.floow.shared.feed.ui

import me.floow.domain.models.Post
import me.floow.domain.models.previewImageUrls
import me.floow.domain.models.resolvedImageVariants
import me.floow.domain.models.viewerImageUrls
import me.floow.shared.profile.ui.model.ProfilePostItem

internal fun Post.toProfilePostItem(): ProfilePostItem {
    val variants = content.resolvedImageVariants()
    val fallbackUrls = content.imageUrls.filter(String::isNotBlank)
    val firstVariant = variants.firstOrNull()
    val resolvedThumb = firstVariant?.thumbUrl ?: fallbackUrls.firstOrNull()
    val resolvedTarget = firstVariant?.targetUrl ?: fallbackUrls.firstOrNull()
    return ProfilePostItem(
        id = id,
        description = content.description,
        thumbUrl = resolvedThumb,
        targetUrl = resolvedTarget,
        lqUrl = resolvedThumb,
        previewUrl = resolvedTarget,
        fullUrl = firstVariant?.fullUrl ?: fallbackUrls.firstOrNull(),
        viewerUrls = content.viewerImageUrls().ifEmpty { fallbackUrls },
        previewUrls = content.previewImageUrls().ifEmpty { fallbackUrls },
        likesCount = likesCount,
        commentsCount = commentsCount,
        commentersPreview = commentersPreview,
        category = category,
        createdAtMillis = createdAt,
    )
}

internal fun Post.prefetchUrlsForFeed(): List<String> {
    return content
        .resolvedImageVariants()
        .take(3)
        .mapNotNull { variant ->
            variant.fullUrl?.takeIf(String::isNotBlank)
        }
}
