package me.floow.uikit.chat.components.videocircle

import androidx.compose.ui.graphics.ImageBitmap

private const val ThumbnailCacheCapacity = 6

private val thumbnailCacheMap = mutableMapOf<String, ImageBitmap>()
private val thumbnailCacheKeys = mutableListOf<String>()

fun cachedThumbnail(videoPath: String): ImageBitmap? {
    return thumbnailCacheMap[videoPath]
}

fun storeCachedThumbnail(videoPath: String, bitmap: ImageBitmap) {
    if (thumbnailCacheMap.containsKey(videoPath)) {
        thumbnailCacheMap[videoPath] = bitmap
        return
    }
    if (thumbnailCacheKeys.size >= ThumbnailCacheCapacity) {
        val oldest = thumbnailCacheKeys.removeAt(0)
        thumbnailCacheMap.remove(oldest)
    }
    thumbnailCacheKeys.add(videoPath)
    thumbnailCacheMap[videoPath] = bitmap
}
