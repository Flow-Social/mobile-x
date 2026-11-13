package me.floow.shared.profile.ui.addpost

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.CoreGraphics.CGSizeMake
import platform.Foundation.NSFileManager
import platform.Foundation.NSSortDescriptor
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.writeToFile
import platform.Photos.PHAccessLevelReadWrite
import platform.Photos.PHAsset
import platform.Photos.PHAssetMediaTypeImage
import platform.Photos.PHAuthorizationStatusAuthorized
import platform.Photos.PHAuthorizationStatusLimited
import platform.Photos.PHFetchOptions
import platform.Photos.PHImageContentModeAspectFill
import platform.Photos.PHImageManager
import platform.Photos.PHImageRequestOptions
import platform.Photos.PHImageRequestOptionsDeliveryModeFastFormat
import platform.Photos.PHImageRequestOptionsDeliveryModeHighQualityFormat
import platform.Photos.PHImageRequestOptionsResizeModeFast
import platform.Photos.PHPhotoLibrary
import platform.UIKit.UIImage
import platform.UIKit.UIImageJPEGRepresentation
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue

internal object IosGalleryAssetRegistry {
    private val localIdBySafeId = mutableMapOf<String, String>()

    fun register(safeId: String, localId: String) {
        localIdBySafeId[safeId] = localId
    }

    fun getLocalId(safeId: String): String? = localIdBySafeId[safeId]

    @OptIn(ExperimentalForeignApi::class)
    fun exportHighQualityAsset(localIdentifier: String, targetFullPath: String): Boolean {
        val fetchResult = PHAsset.fetchAssetsWithLocalIdentifiers(listOf(localIdentifier), options = null)
        val asset = fetchResult.firstObject() as? PHAsset ?: return false
        val reqOptions = PHImageRequestOptions().apply {
            synchronous = true
            deliveryMode = PHImageRequestOptionsDeliveryModeHighQualityFormat
            networkAccessAllowed = true
        }
        var written = false
        PHImageManager.defaultManager().requestImageDataAndOrientationForAsset(asset, reqOptions) { data, _, _, _ ->
            if (data != null) {
                written = data.writeToFile(targetFullPath, atomically = true)
            }
        }
        return written
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun rememberCreatePostGalleryState(): CreatePostGalleryState {
    var images by remember { mutableStateOf(emptyList<CreatePostGalleryImage>()) }
    var isLoading by remember { mutableStateOf(false) }
    var hasPermission by remember { mutableStateOf(checkIosPhotosPermission()) }

    LaunchedEffect(Unit) {
        val perm = checkIosPhotosPermission()
        hasPermission = perm
    }

    LaunchedEffect(hasPermission) {
        platform.Foundation.NSLog("FlowGallery: LaunchedEffect hasPermission=%s", hasPermission.toString())
        if (!hasPermission) return@LaunchedEffect
        isLoading = true
        loadIosGalleryImages { batch ->
            images = batch
        }
        isLoading = false
    }

    return remember(images, isLoading, hasPermission) {
        CreatePostGalleryState(
            images = images,
            isLoading = isLoading,
            hasPermission = hasPermission,
            requestPermission = {
                platform.Foundation.NSLog("FlowGallery: requestPermission called")
                PHPhotoLibrary.requestAuthorizationForAccessLevel(PHAccessLevelReadWrite) { status ->
                    val granted = status == PHAuthorizationStatusAuthorized || status == PHAuthorizationStatusLimited
                    platform.Foundation.NSLog("FlowGallery: requestAuthorization result status=%ld granted=%s", status, granted.toString())
                    dispatch_async(dispatch_get_main_queue()) {
                        hasPermission = granted
                    }
                }
            },
        )
    }
}

private fun checkIosPhotosPermission(): Boolean {
    val statusLevel = PHPhotoLibrary.authorizationStatusForAccessLevel(PHAccessLevelReadWrite)
    val statusLegacy = PHPhotoLibrary.authorizationStatus()
    val granted = statusLevel == PHAuthorizationStatusAuthorized || 
        statusLevel == PHAuthorizationStatusLimited ||
        statusLegacy == PHAuthorizationStatusAuthorized ||
        statusLegacy == PHAuthorizationStatusLimited
    platform.Foundation.NSLog("FlowGallery: checkIosPhotosPermission level=%ld legacy=%ld granted=%s", statusLevel, statusLegacy, granted.toString())
    return granted
}

@OptIn(ExperimentalForeignApi::class)
private suspend fun loadIosGalleryImages(
    onBatchLoaded: (List<CreatePostGalleryImage>) -> Unit
): List<CreatePostGalleryImage> = withContext(Dispatchers.IO) {
    val results = mutableListOf<CreatePostGalleryImage>()
    try {
        val options = PHFetchOptions().apply {
            sortDescriptors = listOf(NSSortDescriptor.sortDescriptorWithKey("creationDate", ascending = false))
            fetchLimit = 150.toULong()
        }
        val assets = PHAsset.fetchAssetsWithMediaType(PHAssetMediaTypeImage, options)
        val manager = PHImageManager.defaultManager()
        val thumbOptions = PHImageRequestOptions().apply {
            synchronous = true
            deliveryMode = PHImageRequestOptionsDeliveryModeFastFormat
            resizeMode = PHImageRequestOptionsResizeModeFast
            networkAccessAllowed = true
        }
        val tempDir = NSTemporaryDirectory() + "post_gallery_cache/"
        NSFileManager.defaultManager.createDirectoryAtPath(tempDir, withIntermediateDirectories = true, attributes = null, error = null)

        val count = assets.count.toInt()
        val batchSize = 12
        platform.Foundation.NSLog("FlowGallery: found %d assets", count)

        for (i in 0 until count) {
            val asset = assets.objectAtIndex(i.toULong()) as? PHAsset ?: continue
            val localId = asset.localIdentifier
            val safeId = localId.replace("/", "_").replace(":", "_")
            val safeName = "thumb_$safeId.jpg"
            val targetPath = tempDir + safeName

            IosGalleryAssetRegistry.register(safeId, localId)

            if (!NSFileManager.defaultManager.fileExistsAtPath(targetPath)) {
                var imageWritten = false
                manager.requestImageForAsset(
                    asset = asset,
                    targetSize = CGSizeMake(240.0, 240.0),
                    contentMode = PHImageContentModeAspectFill,
                    options = thumbOptions,
                    resultHandler = { uiImage: UIImage?, _ ->
                        if (uiImage != null) {
                            val data = UIImageJPEGRepresentation(uiImage, 0.75)
                            if (data != null && data.writeToFile(targetPath, atomically = true)) {
                                imageWritten = true
                            }
                        }
                    }
                )

                // Fallback to full data if fast thumb request returned nil
                if (!imageWritten) {
                    val dataOptions = PHImageRequestOptions().apply {
                        synchronous = true
                        networkAccessAllowed = true
                    }
                    manager.requestImageDataAndOrientationForAsset(asset, dataOptions) { data, _, _, _ ->
                        if (data != null) {
                            data.writeToFile(targetPath, atomically = true)
                        }
                    }
                }
            }

            if (NSFileManager.defaultManager.fileExistsAtPath(targetPath)) {
                results.add(
                    CreatePostGalleryImage(
                        uri = targetPath,
                        displayName = safeName,
                        mimeType = "image/jpeg",
                        sizeBytes = 0L,
                    )
                )
            }

            if (results.size % batchSize == 0 || i == count - 1) {
                val snapshot = results.toList()
                withContext(Dispatchers.Main) {
                    onBatchLoaded(snapshot)
                }
            }
        }
    } catch (e: Throwable) {
        platform.Foundation.NSLog("FlowGallery: error loading images: %@", e.message ?: "unknown")
        e.printStackTrace()
    }
    results
}
