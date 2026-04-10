package me.floow.shared.feed.ui

import me.floow.domain.models.Post
import me.floow.domain.models.viewerImageUrls

internal data class SharedFeedFullscreenOpenRequest(
    val page: Int,
    val imageCount: Int,
)

// Keep the richer ImageOverlayGrid flow Android-only for now. Shared/web feed opens media
// directly in the fullscreen viewer so this policy stays explicit and testable.
internal fun resolveSharedFeedFullscreenOpenRequest(
    post: Post,
    requestedPage: Int,
): SharedFeedFullscreenOpenRequest? {
    val images = post.content.viewerImageUrls()
    if (images.isEmpty()) return null

    return SharedFeedFullscreenOpenRequest(
        page = requestedPage.coerceIn(0, images.lastIndex),
        imageCount = images.size,
    )
}
