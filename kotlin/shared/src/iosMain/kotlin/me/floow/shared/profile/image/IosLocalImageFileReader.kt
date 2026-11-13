package me.floow.shared.profile.image

import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.useContents
import kotlinx.cinterop.usePinned
import me.floow.shared.profile.ui.addpost.IosGalleryAssetRegistry
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSize
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSData
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.dataWithContentsOfURL
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.posix.memcpy

@OptIn(ExperimentalForeignApi::class)
class IosLocalImageFileReader : LocalImageFileReader {
    private val mediaPreparer = me.floow.shared.media.pipeline.IosLocalMediaPreparer()

    override suspend fun read(uriString: String): LocalImageFile? {
        return when (val result = readPost(uriString)) {
            is LocalImageReadResult.Success -> result.file
            is LocalImageReadResult.Failure -> null
        }
    }

    override suspend fun readPost(uriString: String): LocalImageReadResult {
        return prepareWithPolicy(uriString, me.floow.shared.media.pipeline.MediaUploadPolicy.POST)
    }

    override suspend fun readAvatar(uriString: String): LocalImageReadResult {
        return prepareWithPolicy(uriString, me.floow.shared.media.pipeline.MediaUploadPolicy.AVATAR)
    }

    override suspend fun readBackground(uriString: String): LocalImageReadResult {
        return prepareWithPolicy(uriString, me.floow.shared.media.pipeline.MediaUploadPolicy.BACKGROUND)
    }

    private suspend fun prepareWithPolicy(
        uriString: String,
        policy: me.floow.shared.media.pipeline.MediaUploadPolicy,
    ): LocalImageReadResult {
        return when (val prep = mediaPreparer.prepareMedia(uriString, policy)) {
            is me.floow.shared.media.pipeline.MediaPreparationResult.Success -> {
                val media = prep.media
                LocalImageReadResult.Success(
                    LocalImageFile(
                        fileName = media.fileName,
                        contentType = media.contentType,
                        sizeBytes = media.sizeBytes,
                        bytes = ByteArray(0),
                        filePath = media.filePath,
                    )
                )
            }
            is me.floow.shared.media.pipeline.MediaPreparationResult.Failure -> {
                val err = when (prep.error) {
                    me.floow.shared.media.pipeline.MediaPreparationError.FILE_NOT_FOUND -> LocalImageReadError.CORRUPTED_IMAGE
                    me.floow.shared.media.pipeline.MediaPreparationError.UNSUPPORTED_TYPE -> LocalImageReadError.UNSUPPORTED_TYPE
                    me.floow.shared.media.pipeline.MediaPreparationError.FILE_TOO_LARGE -> LocalImageReadError.FILE_TOO_LARGE
                    me.floow.shared.media.pipeline.MediaPreparationError.CORRUPTED_IMAGE -> LocalImageReadError.CORRUPTED_IMAGE
                }
                LocalImageReadResult.Failure(err)
            }
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
private fun NSData.toByteArray(): ByteArray {
    val size = length.toInt()
    if (size == 0) return ByteArray(0)
    val array = ByteArray(size)
    array.usePinned { pinned ->
        memcpy(pinned.addressOf(0), bytes, length)
    }
    return array
}

actual fun createLocalImageFileReader(): LocalImageFileReader = IosLocalImageFileReader()
