package me.floow.uikit.components.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveImageRegressionTest {

    @Test
    fun test1_thumbAndTargetResolvedCorrectly() {
        val resolved = resolveProgressiveTargets(
            thumbUrl = "https://cdn.example.com/photo_lq.jpg",
            targetUrl = "https://cdn.example.com/photo_full.jpg"
        )
        assertEquals("https://cdn.example.com/photo_lq.jpg", resolved.thumb)
        assertEquals("https://cdn.example.com/photo_full.jpg", resolved.target)
    }

    @Test
    fun test2_identicalThumbAndTargetSuppressesThumb() {
        val resolved = resolveProgressiveTargets(
            thumbUrl = "https://cdn.example.com/photo_full.jpg",
            targetUrl = "https://cdn.example.com/photo_full.jpg"
        )
        assertNull(resolved.thumb)
        assertEquals("https://cdn.example.com/photo_full.jpg", resolved.target)
    }

    @Test
    fun test3_thumbOnlyFallbackSetsTargetToThumb() {
        val resolved = resolveProgressiveTargets(
            thumbUrl = "https://cdn.example.com/photo_thumb.jpg",
            targetUrl = null
        )
        assertEquals("https://cdn.example.com/photo_thumb.jpg", resolved.thumb)
        assertEquals("https://cdn.example.com/photo_thumb.jpg", resolved.target)
    }

    @Test
    fun test4_targetOnlyHasNoThumb() {
        val resolved = resolveProgressiveTargets(
            thumbUrl = null,
            targetUrl = "https://cdn.example.com/photo_full.jpg"
        )
        assertNull(resolved.thumb)
        assertEquals("https://cdn.example.com/photo_full.jpg", resolved.target)
    }

    @Test
    fun test5_blankUrlsHandledAsNull() {
        val resolved = resolveProgressiveTargets(
            thumbUrl = "   ",
            targetUrl = "   "
        )
        assertNull(resolved.thumb)
        assertNull(resolved.target)
    }

    @Test
    fun test6_listModeTierSelection() {
        val resolved = resolveProgressiveTargets(
            lqUrl = "https://cdn.example.com/lq.jpg",
            previewUrl = "https://cdn.example.com/preview.jpg",
            fullUrl = "https://cdn.example.com/full.jpg",
            mode = ProgressiveImageMode.LIST
        )
        assertEquals("https://cdn.example.com/lq.jpg", resolved.thumb)
        assertEquals("https://cdn.example.com/preview.jpg", resolved.target)
    }

    @Test
    fun test7_detailModeTierSelection() {
        val resolved = resolveProgressiveTargets(
            lqUrl = "https://cdn.example.com/lq.jpg",
            previewUrl = "https://cdn.example.com/preview.jpg",
            fullUrl = "https://cdn.example.com/full.jpg",
            mode = ProgressiveImageMode.DETAIL
        )
        assertEquals("https://cdn.example.com/lq.jpg", resolved.thumb)
        assertEquals("https://cdn.example.com/full.jpg", resolved.target)
    }

    @Test
    fun test8_resolveProgressiveTargetsDerivesThumbFromFullUrlWhenThumbAbsent() {
        // В Telegram-стандарте без thumbUrl синтетический _lq не генерируется (нет 404 на S3)
        val resolved = resolveProgressiveTargets(
            thumbUrl = null,
            targetUrl = "https://cdn.example.com/post_99_full.jpg?token=xyz"
        )
        assertNull(resolved.thumb)
        assertEquals("https://cdn.example.com/post_99_full.jpg?token=xyz", resolved.target)
    }
}
