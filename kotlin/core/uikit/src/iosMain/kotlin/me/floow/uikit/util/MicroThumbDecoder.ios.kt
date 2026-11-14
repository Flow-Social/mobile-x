package me.floow.uikit.util

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
actual fun decodeAndBlurMicroThumb(bytes: ByteArray, blurRadius: Int): ImageBitmap? {
    if (bytes.isEmpty()) return null
    return kotlinx.cinterop.autoreleasepool {
        runCatching {
            val skiaImage = Image.makeFromEncoded(bytes)
            val rawW = skiaImage.width
            val rawH = skiaImage.height
            if (rawW <= 0 || rawH <= 0) {
                skiaImage.close()
                return@runCatching null
            }

            if (blurRadius <= 0) {
                val result = skiaImage.toComposeImageBitmap()
                skiaImage.close()
                return@runCatching result
            }

            val (activeImage, w, h, needsActiveImageClose) = if (maxOf(rawW, rawH) > 24) {
                val scale = 24f / maxOf(rawW, rawH)
                val targetW = (rawW * scale).toInt().coerceAtLeast(1)
                val targetH = (rawH * scale).toInt().coerceAtLeast(1)
                val surface = org.jetbrains.skia.Surface.makeRasterN32Premul(targetW, targetH)
                val canvas = surface.canvas
                val dstRect = org.jetbrains.skia.Rect.makeWH(targetW.toFloat(), targetH.toFloat())
                val paint = org.jetbrains.skia.Paint().apply {
                    isAntiAlias = true
                }
                canvas.drawImageRect(skiaImage, dstRect, paint)
                val scaledImage = surface.makeImageSnapshot()
                paint.close()
                surface.close()
                skiaImage.close()
                Quadruple(scaledImage, targetW, targetH, true)
            } else {
                Quadruple(skiaImage, rawW, rawH, true)
            }

            val imageInfo = ImageInfo(
                width = w,
                height = h,
                colorType = ColorType.RGBA_8888,
                alphaType = ColorAlphaType.PREMUL
            )

            val bitmap = org.jetbrains.skia.Bitmap()
            bitmap.allocN32Pixels(w, h)
            if (!activeImage.readPixels(bitmap)) {
                bitmap.close()
                val fallback = activeImage.toComposeImageBitmap()
                if (needsActiveImageClose) activeImage.close()
                return@runCatching fallback
            }

            val pixels = IntArray(w * h)
            val byteBuffer = bitmap.readPixels(imageInfo)
            bitmap.close()
            if (byteBuffer == null) {
                val fallback = activeImage.toComposeImageBitmap()
                if (needsActiveImageClose) activeImage.close()
                return@runCatching fallback
            }

            if (needsActiveImageClose) {
                activeImage.close()
            }

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

            val finalBitmap = blurredImage.toComposeImageBitmap()
            blurredImage.close()
            finalBitmap
        }.getOrNull()
    }
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
