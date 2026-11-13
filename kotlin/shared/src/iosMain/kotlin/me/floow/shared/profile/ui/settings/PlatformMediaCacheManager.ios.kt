package me.floow.shared.profile.ui.settings

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSFileSize
import platform.Foundation.NSFileType
import platform.Foundation.NSFileTypeRegular
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask

@OptIn(ExperimentalForeignApi::class)
actual object PlatformMediaCacheManager {

    private fun getCacheDirectories(): List<String> {
        val caches = (NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() as? String) ?: NSTemporaryDirectory()

        val temp = NSTemporaryDirectory()

        return listOf(
            if (caches.endsWith("/")) "${caches}flow_image_cache" else "$caches/flow_image_cache",
            if (caches.endsWith("/")) "${caches}flow_media_cache" else "$caches/flow_media_cache",
            if (temp.endsWith("/")) "${temp}chat_media_cache" else "$temp/chat_media_cache",
            if (temp.endsWith("/")) "${temp}post_gallery_cache" else "$temp/post_gallery_cache",
        )
    }

    actual suspend fun getCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        val fileManager = NSFileManager.defaultManager
        getCacheDirectories().sumOf { dirPath ->
            getDirectorySizeBytes(dirPath, fileManager)
        }
    }

    actual suspend fun clearCache() = withContext(Dispatchers.IO) {
        val fileManager = NSFileManager.defaultManager
        getCacheDirectories().forEach { dirPath ->
            val contents = fileManager.contentsOfDirectoryAtPath(dirPath, null) as? List<*> ?: return@forEach
            for (item in contents) {
                val name = item as? String ?: continue
                val fullPath = if (dirPath.endsWith("/")) "$dirPath$name" else "$dirPath/$name"
                fileManager.removeItemAtPath(fullPath, null)
            }
        }
    }

    private fun getDirectorySizeBytes(path: String, fileManager: NSFileManager): Long {
        if (!fileManager.fileExistsAtPath(path)) return 0L
        val enumerator = fileManager.enumeratorAtPath(path) ?: return 0L
        var total = 0L
        while (true) {
            val subpath = enumerator.nextObject() as? String ?: break
            val fullPath = if (path.endsWith("/")) "$path$subpath" else "$path/$subpath"
            val attrs = fileManager.attributesOfItemAtPath(fullPath, null) ?: continue
            val fileType = attrs[NSFileType]
            if (fileType == NSFileTypeRegular) {
                val size = attrs[NSFileSize] as? Long ?: (attrs[NSFileSize] as? Number)?.toLong() ?: 0L
                total += size
            }
        }
        return total
    }
}
