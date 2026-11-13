package me.floow.uikit.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import android.util.LruCache
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.max
import kotlin.math.roundToInt

object TinyThumbUtils {
    private const val PREFIX_JPEG = "data:image/jpeg;base64,"
    private const val PREFIX_WEBP = "data:image/webp;base64,"
    private const val MAX_THUMB_DIMENSION = 24
    private const val MAX_THUMB_BYTES = 8 * 1024

    private val bitmapCache = LruCache<String, Bitmap>(200)

    fun decodeToBitmap(source: String?): Bitmap? {
        if (source.isNullOrBlank()) return null
        val cached = bitmapCache.get(source)
        if (cached != null && !cached.isRecycled) {
            return cached
        }

        val base64Data = when {
            source.startsWith(PREFIX_JPEG) -> source.removePrefix(PREFIX_JPEG)
            source.startsWith(PREFIX_WEBP) -> source.removePrefix(PREFIX_WEBP)
            else -> return null
        }

        return runCatching {
            val bytes = Base64.decode(base64Data, Base64.DEFAULT)
            if (bytes.isEmpty()) return null
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (bitmap != null) {
                bitmapCache.put(source, bitmap)
            }
            bitmap
        }.getOrNull()
    }

    fun generateMicroThumb(bitmap: Bitmap): String? {
        return runCatching {
            val width = bitmap.width
            val height = bitmap.height
            if (width <= 0 || height <= 0) return null

            val maxDim = max(width, height).coerceAtLeast(1)
            val scale = MAX_THUMB_DIMENSION.toFloat() / maxDim.toFloat()
            val targetWidth = (width * scale).roundToInt().coerceAtLeast(1)
            val targetHeight = (height * scale).roundToInt().coerceAtLeast(1)

            val scaled = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
            ByteArrayOutputStream().use { baos ->
                var quality = 25
                do {
                    baos.reset()
                    scaled.compress(Bitmap.CompressFormat.JPEG, quality, baos)
                    quality -= 5
                } while (baos.size() > MAX_THUMB_BYTES && quality >= 5)

                if (scaled != bitmap) {
                    scaled.recycle()
                }
                val bytes = baos.toByteArray()
                PREFIX_JPEG + Base64.encodeToString(bytes, Base64.NO_WRAP)
            }
        }.getOrNull()
    }

    fun generateMicroThumbFromFile(file: File): String? {
        if (!file.exists() || file.length() == 0L) return null
        val bitmap = runCatching {
            BitmapFactory.decodeFile(file.absolutePath)
        }.getOrNull() ?: return null

        val result = generateMicroThumb(bitmap)
        bitmap.recycle()
        return result
    }
}
