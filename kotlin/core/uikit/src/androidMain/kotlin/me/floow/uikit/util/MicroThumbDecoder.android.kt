package me.floow.uikit.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeAndBlurMicroThumb(bytes: ByteArray, blurRadius: Int): ImageBitmap? {
    if (bytes.isEmpty()) return null
    return runCatching {
        val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val rawW = rawBitmap.width
        val rawH = rawBitmap.height
        if (rawW <= 0 || rawH <= 0) return null

        val (bitmap, w, h) = if (maxOf(rawW, rawH) > 24) {
            val scale = 24f / maxOf(rawW, rawH)
            val targetW = (rawW * scale).toInt().coerceAtLeast(1)
            val targetH = (rawH * scale).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(rawBitmap, targetW, targetH, true)
            if (scaled !== rawBitmap) {
                rawBitmap.recycle()
            }
            Triple(scaled, targetW, targetH)
        } else {
            Triple(rawBitmap, rawW, rawH)
        }

        val pixels = IntArray(w * h)
        bitmap.getPixels(pixels, 0, w, 0, 0, w, h)
        if (blurRadius > 0) {
            StackBlur.blur(pixels, w, h, blurRadius)
        }
        val blurredBitmap = Bitmap.createBitmap(pixels, w, h, Bitmap.Config.ARGB_8888)
        bitmap.recycle()
        blurredBitmap.asImageBitmap()
    }.getOrNull()
}
