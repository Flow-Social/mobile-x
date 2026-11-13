package me.floow.shared.feed.ui

import me.floow.domain.models.Post
import me.floow.domain.models.PostAuthor
import me.floow.domain.models.PostCategories
import me.floow.domain.models.PostContent
import me.floow.domain.models.PostImageVariant
import kotlin.test.Test
import kotlin.test.assertEquals

class FeedPostUiMappingsTest {

    @Test
    fun `toProfilePostItem maps thumbUrl and targetUrl correctly`() {
        val post = Post(
            id = "p1",
            author = PostAuthor(id = "u1", name = null, username = null, avatarUrl = null),
            content = PostContent(
                imageUrls = emptyList(),
                description = "hello",
                imageVariants = listOf(
                    PostImageVariant(
                        thumbUrl = "data:image/jpeg;base64,123",
                        fullUrl = "https://cdn.example.com/p1_full.jpg"
                    )
                )
            ),
            category = PostCategories.NATURE,
            createdAt = 123456789L
        )

        val item = post.toProfilePostItem()

        assertEquals("data:image/jpeg;base64,123", item.thumbUrl)
        assertEquals("https://cdn.example.com/p1_full.jpg", item.targetUrl)
        assertEquals("data:image/jpeg;base64,123", item.lqUrl)
        assertEquals("https://cdn.example.com/p1_full.jpg", item.previewUrl)
        assertEquals("https://cdn.example.com/p1_full.jpg", item.fullUrl)
    }

    @Test
    fun `prefetchUrlsForFeed prefetches remote fullUrl`() {
        val post = Post(
            id = "p2",
            author = PostAuthor(id = "u2", name = null, username = null, avatarUrl = null),
            content = PostContent(
                imageUrls = emptyList(),
                description = "test",
                imageVariants = listOf(
                    PostImageVariant(
                        thumbUrl = "data:image/jpeg;base64,123",
                        fullUrl = "https://cdn.example.com/p2_full.jpg"
                    )
                )
            ),
            category = PostCategories.ART,
            createdAt = 123456789L
        )

        val urls = post.prefetchUrlsForFeed()
        assertEquals(listOf("https://cdn.example.com/p2_full.jpg"), urls)
    }
}
