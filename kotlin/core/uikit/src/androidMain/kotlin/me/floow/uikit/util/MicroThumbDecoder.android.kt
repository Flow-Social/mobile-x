package me.floow.uikit.util

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap

actual fun decodeAndBlurMicroThumb(bytes: ByteArray, blurRadius: Int): ImageBitmap? {
    if (bytes.isEmpty()) return null
    return runCatching {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        val w = bitmap.width
        val h = bitmap.height
        if (w <= 0 || h <= 0) return null

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
