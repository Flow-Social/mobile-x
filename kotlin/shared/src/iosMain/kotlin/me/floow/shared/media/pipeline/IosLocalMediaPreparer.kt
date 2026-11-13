package me.floow.shared.media.pipeline

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import me.floow.shared.profile.ui.addpost.IosGalleryAssetRegistry
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSURL
import platform.Foundation.base64EncodedStringWithOptions
import platform.Foundation.dataWithContentsOfURL
import platform.Foundation.writeToFile
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import kotlin.random.Random

@OptIn(ExperimentalForeignApi::class)
class IosLocalMediaPreparer : LocalMediaPreparer {

    override suspend fun prepareMedia(
        sourceUriOrPath: String,
        policy: MediaUploadPolicy,
    ): MediaPreparationResult = withContext(Dispatchers.IO) {
        if (sourceUriOrPath.isBlank()) {
            return@withContext MediaPreparationResult.Failure(MediaPreparationError.FILE_NOT_FOUND)
        }

        val resolvedPath = resolveHighQualityPathIfNeeded(sourceUriOrPath)
        val url = if (resolvedPath.startsWith("file://") || resolvedPath.startsWith("http")) {
            NSURL.URLWithString(resolvedPath)
        } else {
            NSURL.fileURLWithPath(resolvedPath)
        } ?: return@withContext MediaPreparationResult.Failure(MediaPreparationError.FILE_NOT_FOUND)

        val rawData = NSData.dataWithContentsOfURL(url)
            ?: return@withContext MediaPreparationResult.Failure(MediaPreparationError.CORRUPTED_IMAGE)

        val image = UIImage.imageWithData(rawData)
            ?: return@withContext MediaPreparationResult.Failure(MediaPreparationError.CORRUPTED_IMAGE)

        val maxDim = policy.maxDimensionPx.toDouble()
        val processedImage = resizeImageIfNeeded(image, maxDim)

        val primaryQuality = (policy.jpegQuality / 100.0).coerceIn(0.1, 1.0)
        var jpegData = UIImageJPEGRepresentation(processedImage, primaryQuality)
            ?: UIImageJPEGRepresentation(image, primaryQuality)
            ?: return@withContext MediaPreparationResult.Failure(MediaPreparationError.CORRUPTED_IMAGE)

        if (jpegData.length.toLong() > policy.maxFileSizeBytes) {
            val lowerQualityData = UIImageJPEGRepresentation(processedImage, 0.60)
            if (lowerQualityData != null && lowerQualityData.length.toLong() <= policy.maxFileSizeBytes) {
                jpegData = lowerQualityData
            } else if (lowerQualityData != null && lowerQualityData.length.toLong() > policy.maxFileSizeBytes) {
                val fallbackData = UIImageJPEGRepresentation(processedImage, 0.40)
                if (fallbackData != null && fallbackData.length.toLong() <= policy.maxFileSizeBytes) {
                    jpegData = fallbackData
                } else {
                    return@withContext MediaPreparationResult.Failure(MediaPreparationError.FILE_TOO_LARGE)
                }
            }
        }

        val originalName = url.lastPathComponent ?: "image.jpg"
        val fileName = if (originalName.contains('.')) {
            originalName.substringBeforeLast('.') + ".jpg"
        } else {
            "$originalName.jpg"
        }

        val tempDir = NSTemporaryDirectory()
        val tempFilePath = "$tempDir/media_${Random.nextLong(100000, 999999)}_$fileName"
        val written = jpegData.writeToFile(tempFilePath, true)
        if (!written) {
            return@withContext MediaPreparationResult.Failure(MediaPreparationError.CORRUPTED_IMAGE)
        }

        val finalW = processedImage.size.useContents { width.toInt() }
        val finalH = processedImage.size.useContents { height.toInt() }

        val microThumbDataUri = runCatching {
            val microImage = resizeImageIfNeeded(processedImage, 16.0)
            val microJpeg = UIImageJPEGRepresentation(microImage, 0.3)
            val base64String = microJpeg?.base64EncodedStringWithOptions(0UL)
            if (base64String != null) "data:image/jpeg;base64,$base64String" else null
        }.getOrNull()

        MediaPreparationResult.Success(
            PreparedMediaFile(
                filePath = tempFilePath,
                fileName = fileName,
                contentType = "image/jpeg",
                sizeBytes = jpegData.length.toLong(),
                width = finalW,
                height = finalH,
                microThumbnail = microThumbDataUri,
            )
        )
    }

    private fun resizeImageIfNeeded(image: UIImage, maxDimension: Double): UIImage {
        val size: CValue<CGSize> = image.size
        val width = size.useContents { this.width }
        val height = size.useContents { this.height }

        if (width <= maxDimension && height <= maxDimension) {
            return image
        }

        val scale = minOf(1.0, maxDimension / maxOf(width, height))
        val targetWidth = (width * scale).coerceAtLeast(1.0)
        val targetHeight = (height * scale).coerceAtLeast(1.0)
        val targetSize = CGSizeMake(targetWidth, targetHeight)

        UIGraphicsBeginImageContextWithOptions(targetSize, false, 1.0)
        image.drawInRect(CGRectMake(0.0, 0.0, targetWidth, targetHeight))
        val resized = UIGraphicsGetImageFromCurrentImageContext()
        UIGraphicsEndImageContext()

        return resized ?: image
    }

    private fun resolveHighQualityPathIfNeeded(uriString: String): String {
        if (!uriString.contains("post_gallery_cache") || !uriString.contains("thumb_")) {
            return uriString
        }
        val fullPath = uriString.replace("thumb_", "full_")
        if (NSFileManager.defaultManager.fileExistsAtPath(fullPath)) {
            return fullPath
        }
        val fileName = uriString.substringAfterLast('/')
        val safeId = fileName.removePrefix("thumb_").removeSuffix(".jpg")
        val localId = IosGalleryAssetRegistry.getLocalId(safeId)
        if (localId != null) {
            val exported = IosGalleryAssetRegistry.exportHighQualityAsset(localId, fullPath)
            if (exported && NSFileManager.defaultManager.fileExistsAtPath(fullPath)) {
                return fullPath
            }
        }
        return uriString
    }
}

actual fun createLocalMediaPreparer(): LocalMediaPreparer = IosLocalMediaPreparer()

@OptIn(ExperimentalForeignApi::class)
actual fun deletePreparedTempFile(filePath: String): Boolean {
    val fileManager = NSFileManager.defaultManager
    return if (fileManager.fileExistsAtPath(filePath)) {
        fileManager.removeItemAtPath(filePath, error = null)
    } else {
        false
    }
}
