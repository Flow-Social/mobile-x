package me.floow.domain.media

import kotlinx.coroutines.flow.StateFlow

enum class MediaDownloadPriority(val weight: Int) {
    BACKGROUND(1),
    PRELOAD(2),
    VISIBLE(3),
    ACTIVE_STREAM(4),
}

enum class DownloadableMediaType {
    PHOTO,
    VOICE,
    VIDEO_CIRCLE,
}

sealed interface MediaDownloadState {
    object Idle : MediaDownloadState
    data class Downloading(val progress: Float, val bytesDownloaded: Long, val totalBytes: Long) : MediaDownloadState
    data class Completed(val localPath: String) : MediaDownloadState
    data class Failed(val error: String) : MediaDownloadState
}

interface MediaDownloadController {
    val states: StateFlow<Map<String, MediaDownloadState>>
    fun getDownloadState(url: String): MediaDownloadState
    fun isDownloaded(url: String, mediaType: DownloadableMediaType): Boolean
    fun getLocalPathIfDownloaded(url: String, mediaType: DownloadableMediaType): String?
    fun registerLocalFile(url: String, localPath: String, mediaType: DownloadableMediaType)
    fun enqueue(url: String, mediaType: DownloadableMediaType, priority: MediaDownloadPriority = MediaDownloadPriority.VISIBLE)
    fun enqueueAutoDownload(url: String, mediaType: DownloadableMediaType, sizeBytes: Long)
    fun cancel(url: String)
    fun resetAll()
}
