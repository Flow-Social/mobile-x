package me.floow.uikit.components.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveImageModelTest {

    @Test
    fun `resolveProgressiveTargets returns thumb and target correctly`() {
        val resolved = resolveProgressiveTargets(
            thumbUrl = "https://cdn.example.com/thumb.jpg",
            targetUrl = "https://cdn.example.com/target.jpg"
        )
        assertEquals("https://cdn.example.com/thumb.jpg", resolved.thumb)
        assertEquals("https://cdn.example.com/target.jpg", resolved.target)
    }

    @Test
    fun `resolveProgressiveTargets legacy 3-tier fallback maps lq and preview-full to 2-tier`() {
        val listMode = resolveProgressiveTargets(
            lqUrl = "https://cdn.example.com/lq.jpg",
            previewUrl = "https://cdn.example.com/preview.jpg",
            fullUrl = "https://cdn.example.com/full.jpg",
            mode = ProgressiveImageMode.LIST
        )
        assertEquals("https://cdn.example.com/lq.jpg", listMode.thumb)
        assertEquals("https://cdn.example.com/preview.jpg", listMode.target)

        val detailMode = resolveProgressiveTargets(
            lqUrl = "https://cdn.example.com/lq.jpg",
            previewUrl = "https://cdn.example.com/preview.jpg",
            fullUrl = "https://cdn.example.com/full.jpg",
            mode = ProgressiveImageMode.DETAIL
        )
        assertEquals("https://cdn.example.com/lq.jpg", detailMode.thumb)
        assertEquals("https://cdn.example.com/full.jpg", detailMode.target)
    }

    @Test
    fun `resolveProgressiveTargets ignores thumb if identical to target`() {
        val resolved = resolveProgressiveTargets(
            thumbUrl = "https://cdn.example.com/target.jpg",
            targetUrl = "https://cdn.example.com/target.jpg"
        )
        assertNull(resolved.thumb)
        assertEquals("https://cdn.example.com/target.jpg", resolved.target)
    }

    @Test
    fun `resolveProgressiveTargets handles blank urls`() {
        val resolved = resolveProgressiveTargets(
            thumbUrl = "   ",
            targetUrl = "https://cdn.example.com/target.jpg"
        )
        assertNull(resolved.thumb)
        assertEquals("https://cdn.example.com/target.jpg", resolved.target)
    }

    @Test
    fun `FlowImageSource of normalizes blanks and identifies empty correctly`() {
        val emptySource = FlowImageSource.of(null, null)
        assertEquals(FlowImageSource.Empty, emptySource)
        assertTrue(emptySource.isEmpty)

        val blankSource = FlowImageSource.of("   ", "   ")
        assertEquals(FlowImageSource.Empty, blankSource)
        assertTrue(blankSource.isEmpty)

        val validSource = FlowImageSource.of(" https://cdn.example.com/pic.jpg ", " data:image/jpeg;base64,abc ")
        assertEquals("https://cdn.example.com/pic.jpg", validSource.url)
        assertEquals("data:image/jpeg;base64,abc", validSource.microThumb)
        assertFalse(validSource.isEmpty)
    }
}
