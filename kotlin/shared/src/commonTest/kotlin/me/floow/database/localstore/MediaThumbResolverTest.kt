package me.floow.database.localstore

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class MediaThumbResolverTest {

    @Test
    fun testWhenNewUrlIsNullOrBlankReturnsNullEvenIfCachedThumbExists() {
        // User deleted photo -> preview must be cleared
        assertNull(resolveMergedThumb(newUrl = null, newThumb = null, cachedUrl = "https://example.com/pic.jpg", cachedThumb = "data:image/jpeg;base64,AAA"))
        assertNull(resolveMergedThumb(newUrl = "", newThumb = null, cachedUrl = "https://example.com/pic.jpg", cachedThumb = "data:image/jpeg;base64,AAA"))
        assertNull(resolveMergedThumb(newUrl = "   ", newThumb = "data:image/jpeg;base64,BBB", cachedUrl = "https://example.com/pic.jpg", cachedThumb = "data:image/jpeg;base64,AAA"))
    }

    @Test
    fun testWhenNewThumbIsPresentReturnsNewThumb() {
        val freshThumb = "data:image/jpeg;base64,FRESH"
        val oldThumb = "data:image/jpeg;base64,OLD"
        val result = resolveMergedThumb(
            newUrl = "https://example.com/new.jpg",
            newThumb = freshThumb,
            cachedUrl = "https://example.com/old.jpg",
            cachedThumb = oldThumb
        )
        assertEquals(freshThumb, result)
    }

    @Test
    fun testWhenNewUrlEqualsCachedUrlAndNewThumbMissingPreservesCachedThumb() {
        // Partial DTO update for same image
        val cachedThumb = "data:image/jpeg;base64,SAME"
        val result = resolveMergedThumb(
            newUrl = "https://example.com/same.jpg",
            newThumb = null,
            cachedUrl = "https://example.com/same.jpg",
            cachedThumb = cachedThumb
        )
        assertEquals(cachedThumb, result)

        val resultBlank = resolveMergedThumb(
            newUrl = "https://example.com/same.jpg",
            newThumb = "",
            cachedUrl = "https://example.com/same.jpg",
            cachedThumb = cachedThumb
        )
        assertEquals(cachedThumb, resultBlank)
    }

    @Test
    fun testWhenNewUrlDiffersFromCachedUrlAndNewThumbMissingReturnsNull() {
        // Image changed to a new URL, but no new preview arrived -> old preview cannot be shown
        val oldThumb = "data:image/jpeg;base64,OLD"
        val result = resolveMergedThumb(
            newUrl = "https://example.com/brand_new.jpg",
            newThumb = null,
            cachedUrl = "https://example.com/old.jpg",
            cachedThumb = oldThumb
        )
        assertNull(result)
    }
}
