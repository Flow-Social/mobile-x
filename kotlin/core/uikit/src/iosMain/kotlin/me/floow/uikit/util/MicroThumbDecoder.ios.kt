package me.floow.uikit.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.Foundation.NSData
import platform.Foundation.dataWithBytes

@OptIn(ExperimentalForeignApi::class)
actual fun decodeAndBlurMicroThumb(bytes: ByteArray, blurRadius: Int): ImageBitmap? {
    if (bytes.isEmpty()) return null
    return runCatching {
        // NSData verification
        val nsData = bytes.usePinned { pinned ->
            NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
        }
        if (nsData.length.toInt() == 0) return null

        val skiaImage = Image.makeFromEncoded(bytes) ?: return null
        val w = skiaImage.width
        val h = skiaImage.height
        if (w <= 0 || h <= 0) return null

        if (blurRadius <= 0) {
            return skiaImage.toComposeImageBitmap()
        }

        val imageInfo = ImageInfo(
            width = w,
            height = h,
            colorType = ColorType.RGBA_8888,
            alphaType = ColorAlphaType.PREMUL
        )

        val bitmap = org.jetbrains.skia.Bitmap()
        bitmap.allocN32Pixels(w, h)
        if (!skiaImage.readPixels(bitmap)) {
            return skiaImage.toComposeImageBitmap()
        }

        val pixels = IntArray(w * h)
        val byteBuffer = bitmap.readPixels(imageInfo) ?: return skiaImage.toComposeImageBitmap()
        for (i in 0 until (w * h)) {
            val offset = i * 4
            val r = byteBuffer[offset].toInt() and 0xFF
            val g = byteBuffer[offset + 1].toInt() and 0xFF
            val b = byteBuffer[offset + 2].toInt() and 0xFF
            val a = byteBuffer[offset + 3].toInt() and 0xFF
            pixels[i] = (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        StackBlur.blur(pixels, w, h, blurRadius)

        val blurredBytes = ByteArray(w * h * 4)
        for (i in 0 until (w * h)) {
            val offset = i * 4
            val p = pixels[i]
            blurredBytes[offset] = ((p shr 16) and 0xFF).toByte()
            blurredBytes[offset + 1] = ((p shr 8) and 0xFF).toByte()
            blurredBytes[offset + 2] = (p and 0xFF).toByte()
            blurredBytes[offset + 3] = ((p shr 24) and 0xFF).toByte()
        }

        val blurredImage = Image.makeRaster(
            imageInfo = imageInfo,
            bytes = blurredBytes,
            rowBytes = w * 4
        )

        blurredImage?.toComposeImageBitmap()
    }.getOrNull()
}
