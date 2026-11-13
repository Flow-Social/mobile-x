package me.floow.shared.chats.media.editor

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

class AndroidChatImageProcessor(private val context: Context) : ChatImageProcessor {
    override suspend fun processAndSaveImage(
        sourcePath: String,
        rotationDegrees: Float,
        strokes: List<DrawStroke>,
        cropRectNormalized: Rect,
        maxDimension: Int,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val boundsOptions = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (sourcePath.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(sourcePath))?.use {
                    BitmapFactory.decodeStream(it, null, boundsOptions)
                }
            } else {
                val cleanPath = sourcePath.removePrefix("file://")
                BitmapFactory.decodeFile(cleanPath, boundsOptions)
            }

            var sampleSize = 1
            val origMax = maxOf(boundsOptions.outWidth, boundsOptions.outHeight)
            if (maxDimension > 0 && origMax > 0) {
                while (origMax / (sampleSize * 2) >= maxDimension) {
                    sampleSize *= 2
                }
            }

            val decodeOptions = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            val originalBitmap = if (sourcePath.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(sourcePath))?.use {
                    BitmapFactory.decodeStream(it, null, decodeOptions)
                }
            } else {
                val cleanPath = sourcePath.removePrefix("file://")
                BitmapFactory.decodeFile(cleanPath, decodeOptions)
            } ?: return@withContext null

            // 1. Rotation
            val matrix = Matrix().apply {
                if (rotationDegrees != 0f) postRotate(rotationDegrees)
            }
            var workingBitmap = if (rotationDegrees == 0f) {
                originalBitmap.copy(Bitmap.Config.ARGB_8888, true)
            } else {
                Bitmap.createBitmap(
                    originalBitmap, 0, 0, originalBitmap.width, originalBitmap.height, matrix, true
                )
            }

            // 2. Overlay strokes
            if (strokes.isNotEmpty()) {
                val mutableBmp = workingBitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutableBmp)
                val paint = Paint().apply {
                    isAntiAlias = true
                    style = Paint.Style.STROKE
                    strokeCap = Paint.Cap.ROUND
                    strokeJoin = Paint.Join.ROUND
                }
                val w = mutableBmp.width.toFloat()
                val h = mutableBmp.height.toFloat()

                for (stroke in strokes) {
                    if (stroke.points.size < 2) continue
                    paint.color = stroke.color.toArgb()
                    paint.strokeWidth = stroke.relativeStrokeWidth * w
                    for (i in 1 until stroke.points.size) {
                        val p0 = stroke.points[i - 1]
                        val p1 = stroke.points[i]
                        canvas.drawLine(p0.x * w, p0.y * h, p1.x * w, p1.y * h, paint)
                    }
                }
                if (workingBitmap != originalBitmap) {
                    workingBitmap.recycle()
                }
                workingBitmap = mutableBmp
            }

            // 3. Crop
            val cropX = (cropRectNormalized.left * workingBitmap.width).toInt().coerceIn(0, workingBitmap.width - 1)
            val cropY = (cropRectNormalized.top * workingBitmap.height).toInt().coerceIn(0, workingBitmap.height - 1)
            val cropW = (cropRectNormalized.width * workingBitmap.width).toInt().coerceIn(1, workingBitmap.width - cropX)
            val cropH = (cropRectNormalized.height * workingBitmap.height).toInt().coerceIn(1, workingBitmap.height - cropY)

            val cropped = Bitmap.createBitmap(workingBitmap, cropX, cropY, cropW, cropH)
            if (cropped != workingBitmap && workingBitmap != originalBitmap) {
                workingBitmap.recycle()
            }
            if (originalBitmap != workingBitmap && originalBitmap != cropped) {
                originalBitmap.recycle()
            }

            // 3.5. Downscale proportionally if exceeding maxDimension (Telegram 1920px standard)
            val cropMaxSide = maxOf(cropped.width, cropped.height)
            val finalBitmap = if (maxDimension > 0 && cropMaxSide > maxDimension) {
                val scale = maxDimension.toFloat() / cropMaxSide.toFloat()
                val targetW = (cropped.width * scale).toInt().coerceAtLeast(1)
                val targetH = (cropped.height * scale).toInt().coerceAtLeast(1)
                val scaled = Bitmap.createScaledBitmap(cropped, targetW, targetH, true)
                if (scaled != cropped) {
                    cropped.recycle()
                }
                scaled
            } else {
                cropped
            }

            // 4. Save to temporary cache
            val outputDir = File(context.cacheDir, "chat_media_outbox/editor_processed").apply { mkdirs() }
            val now = System.currentTimeMillis()
            outputDir.listFiles()?.forEach { oldFile ->
                if (now - oldFile.lastModified() > 3600_000L) {
                    oldFile.delete()
                }
            }
            val outputFile = File(outputDir, "edited_${UUID.randomUUID()}.jpg")
            FileOutputStream(outputFile).use { out ->
                finalBitmap.compress(Bitmap.CompressFormat.JPEG, 82, out)
            }
            finalBitmap.recycle()

            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getImageDimensions(sourcePath: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        try {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            if (sourcePath.startsWith("content://")) {
                context.contentResolver.openInputStream(Uri.parse(sourcePath))?.use {
                    BitmapFactory.decodeStream(it, null, options)
                }
            } else {
                val cleanPath = sourcePath.removePrefix("file://")
                BitmapFactory.decodeFile(cleanPath, options)
            }
            Pair(options.outWidth.coerceAtLeast(0), options.outHeight.coerceAtLeast(0))
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }
}

@Composable
actual fun rememberChatImageProcessor(): ChatImageProcessor {
    val context = LocalContext.current
    return remember(context) { AndroidChatImageProcessor(context) }
}
