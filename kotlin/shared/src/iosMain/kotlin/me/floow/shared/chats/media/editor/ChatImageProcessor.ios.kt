package me.floow.shared.chats.media.editor

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Rect
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.cValue
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.floow.domain.utils.currentTimeMillis
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextBeginPath
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextRestoreGState
import platform.CoreGraphics.CGContextRotateCTM
import platform.CoreGraphics.CGContextSaveGState
import platform.CoreGraphics.CGContextSetLineCap
import platform.CoreGraphics.CGContextSetLineJoin
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetStrokeColorWithColor
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGImageCreateWithImageInRect
import platform.CoreGraphics.CGRect
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGLineCap
import platform.CoreGraphics.CGLineJoin
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.writeToFile
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import kotlin.math.PI
import kotlin.random.Random

@OptIn(ExperimentalForeignApi::class)
class IosChatImageProcessor : ChatImageProcessor {
    override suspend fun processAndSaveImage(
        sourcePath: String,
        rotationDegrees: Float,
        strokes: List<DrawStroke>,
        cropRectNormalized: Rect,
        maxDimension: Int,
    ): String? = withContext(Dispatchers.IO) {
        try {
            val cleanPath = sourcePath.removePrefix("file://")
            val image = UIImage.imageWithContentsOfFile(cleanPath) ?: return@withContext null

            val origW = image.size.useContents { width }
            val origH = image.size.useContents { height }

            val isRotated90or270 = rotationDegrees == 90f || rotationDegrees == 270f
            val targetWidth = if (isRotated90or270) origH else origW
            val targetHeight = if (isRotated90or270) origW else origH

            val targetSize = cValue<CGSize> {
                width = targetWidth
                height = targetHeight
            }

            UIGraphicsBeginImageContextWithOptions(targetSize, false, 1.0)
            val ctx = UIGraphicsGetCurrentContext() ?: run {
                UIGraphicsEndImageContext()
                return@withContext null
            }

            // 1. Rotation and draw original image
            CGContextSaveGState(ctx)
            CGContextTranslateCTM(ctx, targetWidth / 2.0, targetHeight / 2.0)
            CGContextRotateCTM(ctx, (rotationDegrees.toDouble() * PI / 180.0))
            val drawRect = cValue<CGRect> {
                origin.x = -origW / 2.0
                origin.y = -origH / 2.0
                size.width = origW
                size.height = origH
            }
            image.drawInRect(drawRect)
            CGContextRestoreGState(ctx)

            // 2. Draw strokes on rotated canvas
            if (strokes.isNotEmpty()) {
                for (stroke in strokes) {
                    if (stroke.points.size < 2) continue
                    val uiColor = UIColor(
                        red = stroke.color.red.toDouble(),
                        green = stroke.color.green.toDouble(),
                        blue = stroke.color.blue.toDouble(),
                        alpha = stroke.color.alpha.toDouble()
                    )
                    CGContextSetStrokeColorWithColor(ctx, uiColor.CGColor)
                    CGContextSetLineWidth(ctx, stroke.relativeStrokeWidth.toDouble() * targetWidth)
                    CGContextSetLineCap(ctx, CGLineCap.kCGLineCapRound)
                    CGContextSetLineJoin(ctx, CGLineJoin.kCGLineJoinRound)
                    CGContextBeginPath(ctx)
                    val p0 = stroke.points[0]
                    CGContextMoveToPoint(ctx, p0.x.toDouble() * targetWidth, p0.y.toDouble() * targetHeight)
                    for (i in 1 until stroke.points.size) {
                        val pt = stroke.points[i]
                        CGContextAddLineToPoint(ctx, pt.x.toDouble() * targetWidth, pt.y.toDouble() * targetHeight)
                    }
                    CGContextStrokePath(ctx)
                }
            }

            val fullImage = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()

            val cgImage = fullImage?.CGImage ?: return@withContext null

            // 3. Crop
            val cropX = (cropRectNormalized.left.toDouble() * targetWidth).coerceIn(0.0, targetWidth - 1.0)
            val cropY = (cropRectNormalized.top.toDouble() * targetHeight).coerceIn(0.0, targetHeight - 1.0)
            val cropW = (cropRectNormalized.width.toDouble() * targetWidth).coerceIn(1.0, targetWidth - cropX)
            val cropH = (cropRectNormalized.height.toDouble() * targetHeight).coerceIn(1.0, targetHeight - cropY)

            val cropRect = cValue<CGRect> {
                origin.x = cropX
                origin.y = cropY
                size.width = cropW
                size.height = cropH
            }
            val croppedCg = CGImageCreateWithImageInRect(cgImage, cropRect) ?: return@withContext null
            val croppedImage = UIImage.imageWithCGImage(croppedCg)

            // 3.5 Downscale proportionally if exceeding maxDimension (Telegram 1920px standard)
            val cropMaxSide = maxOf(cropW, cropH)
            val finalImage = if (maxDimension > 0 && cropMaxSide > maxDimension.toDouble()) {
                val scale = maxDimension.toDouble() / cropMaxSide
                val scaledW = (cropW * scale).coerceAtLeast(1.0)
                val scaledH = (cropH * scale).coerceAtLeast(1.0)
                val scaledSize = cValue<CGSize> {
                    width = scaledW
                    height = scaledH
                }
                UIGraphicsBeginImageContextWithOptions(scaledSize, false, 1.0)
                val scaleDrawRect = cValue<CGRect> {
                    origin.x = 0.0
                    origin.y = 0.0
                    size.width = scaledW
                    size.height = scaledH
                }
                croppedImage.drawInRect(scaleDrawRect)
                val scaledImg = UIGraphicsGetImageFromCurrentImageContext()
                UIGraphicsEndImageContext()
                scaledImg ?: croppedImage
            } else {
                croppedImage
            }

            // 4. Save to temporary directory
            val jpegData = UIImageJPEGRepresentation(finalImage, 0.82) ?: return@withContext null
            val outPath = NSTemporaryDirectory() + "edited_${currentTimeMillis()}_${Random.nextInt(10000)}.jpg"
            jpegData.writeToFile(outPath, atomically = true)

            outPath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override suspend fun getImageDimensions(sourcePath: String): Pair<Int, Int> = withContext(Dispatchers.IO) {
        try {
            val cleanPath = sourcePath.removePrefix("file://")
            val uiImage = UIImage.imageWithContentsOfFile(cleanPath)
            if (uiImage != null) {
                Pair(uiImage.size.useContents { width.toInt() }, uiImage.size.useContents { height.toInt() })
            } else {
                Pair(0, 0)
            }
        } catch (e: Exception) {
            Pair(0, 0)
        }
    }
}

@Composable
actual fun rememberChatImageProcessor(): ChatImageProcessor {
    return remember { IosChatImageProcessor() }
}
