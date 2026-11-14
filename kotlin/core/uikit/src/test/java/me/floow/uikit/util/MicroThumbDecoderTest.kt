package me.floow.uikit.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.colorspace.ColorSpace
import androidx.compose.ui.graphics.colorspace.ColorSpaces
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class MicroThumbDecoderTest {

    @Before
    fun setUp() {
        MicroThumbDecoder.clearCache()
    }

    @Test
    fun testRejectsInvalidStrings() {
        assertNull(MicroThumbDecoder.decode(null))
        assertNull(MicroThumbDecoder.decode(""))
        assertNull(MicroThumbDecoder.decode("   "))
        assertNull(MicroThumbDecoder.decode("https://example.com/image.jpg"))
        assertNull(MicroThumbDecoder.decode("content://media/external/123"))
    }

    @Test
    fun testRejectsBloatedStringsOver2048Chars() {
        val bloatedString = "data:image/jpeg;base64," + "A".repeat(2049)
        assertNull(MicroThumbDecoder.decode(bloatedString))
    }

    @Test
    fun testLruCacheHitReturnsCachedBitmap() {
        val fakeBitmap = object : ImageBitmap {
            override val width: Int = 8
            override val height: Int = 8
            override val colorSpace: ColorSpace = ColorSpaces.Srgb
            override val hasAlpha: Boolean = false
            override val config: ImageBitmapConfig = ImageBitmapConfig.Argb8888
            override fun prepareToDraw() {}
            override fun readPixels(
                buffer: IntArray,
                startX: Int,
                startY: Int,
                width: Int,
                height: Int,
                bufferOffset: Int,
                stride: Int
            ) {}
        }

        val dataUri = "data:image/jpeg;base64,/9j/4AAQSkZJRg=="
        MicroThumbDecoder.putInCacheForTesting(dataUri, fakeBitmap)

        val retrieved = MicroThumbDecoder.decode(dataUri, MicroThumbMediaType.AVATAR)
        assertNotNull(retrieved)
        assertEquals(fakeBitmap, retrieved)
        assertEquals(8, retrieved!!.width)
        assertEquals(8, retrieved.height)
    }

    @Test
    fun testStackBlurModifiesPixels() {
        val pixels = IntArray(16 * 16) { i ->
            if (i < 128) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val originalCopy = pixels.clone()

        StackBlur.blur(pixels, 16, 16, radius = 4)

        // Pixels near the boundary should have been blurred (not identical to original)
        var changedCount = 0
        for (i in pixels.indices) {
            if (pixels[i] != originalCopy[i]) {
                changedCount++
            }
        }
        assertTrue("StackBlur should modify boundary pixels", changedCount > 0)
    }
}
