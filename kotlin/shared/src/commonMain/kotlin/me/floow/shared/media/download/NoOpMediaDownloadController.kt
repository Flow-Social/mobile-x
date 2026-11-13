package me.floow.shared.media.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.floow.domain.media.DownloadableMediaType
import me.floow.domain.media.MediaDownloadController
import me.floow.domain.media.MediaDownloadPriority
import me.floow.domain.media.MediaDownloadState

class NoOpMediaDownloadController : MediaDownloadController {
    private val _states = MutableStateFlow<Map<String, MediaDownloadState>>(emptyMap())
    override val states: StateFlow<Map<String, MediaDownloadState>> = _states.asStateFlow()

    private val localFiles = mutableMapOf<String, String>()

    override fun getDownloadState(url: String): MediaDownloadState {
        val state = _states.value[url]
        if (state != null) return state
        val path = localFiles[url]
        return if (path != null) MediaDownloadState.Completed(path) else MediaDownloadState.Idle
    }

    override fun isDownloaded(url: String, mediaType: DownloadableMediaType): Boolean {
        return localFiles.containsKey(url)
    }

    override fun getLocalPathIfDownloaded(url: String, mediaType: DownloadableMediaType): String? {
        return localFiles[url]
    }

    override fun registerLocalFile(url: String, localPath: String, mediaType: DownloadableMediaType) {
        if (url.isBlank() || localPath.isBlank()) return
        localFiles[url] = localPath
        _states.update { it + (url to MediaDownloadState.Completed(localPath)) }
    }

    override fun enqueue(url: String, mediaType: DownloadableMediaType, priority: MediaDownloadPriority) {
        // Fallback no-op
    }

    override fun enqueueAutoDownload(url: String, mediaType: DownloadableMediaType, sizeBytes: Long) {
        // Fallback no-op
    }

    override fun cancel(url: String) {
        _states.update { it - url }
    }

    override fun resetAll() {
        localFiles.clear()
        _states.value = emptyMap()
    }
}
