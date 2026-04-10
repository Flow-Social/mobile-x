package me.floow.shared.feed.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import me.floow.domain.models.Post
import me.floow.domain.models.PostAuthor
import me.floow.domain.models.PostCategories
import me.floow.domain.models.PostContent

class SharedFeedMediaContractTest {
    @Test
    fun `returns null when post has no visible images`() {
        val request = resolveSharedFeedFullscreenOpenRequest(
            post = testPost(imageUrls = emptyList()),
            requestedPage = 0,
        )

        assertNull(request)
    }

    @Test
    fun `keeps direct fullscreen flow for multi image posts`() {
        val request = resolveSharedFeedFullscreenOpenRequest(
            post = testPost(
                imageUrls = listOf(
                    "https://example.com/first.jpg",
                    "https://example.com/second.jpg",
                    "https://example.com/third.jpg",
                ),
            ),
            requestedPage = 1,
        )

        assertEquals(SharedFeedFullscreenOpenRequest(page = 1, imageCount = 3), request)
    }

    @Test
    fun `clamps requested page to available image range`() {
        val request = resolveSharedFeedFullscreenOpenRequest(
            post = testPost(
                imageUrls = listOf(
                    "https://example.com/first.jpg",
                    "https://example.com/second.jpg",
                ),
            ),
            requestedPage = 20,
        )

        assertEquals(SharedFeedFullscreenOpenRequest(page = 1, imageCount = 2), request)
    }

    private fun testPost(imageUrls: List<String>): Post {
        return Post(
            id = "post-id",
            author = PostAuthor(
                id = "author-id",
                name = null,
                username = null,
                avatarUrl = null,
            ),
            content = PostContent(
                imageUrls = imageUrls,
                description = null,
            ),
            category = PostCategories.ART,
            createdAt = 0L,
        )
    }
}
