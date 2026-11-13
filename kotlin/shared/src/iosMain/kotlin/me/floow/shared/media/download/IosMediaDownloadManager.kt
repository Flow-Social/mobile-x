package me.floow.shared.media.download

import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import me.floow.domain.media.DownloadableMediaType
import me.floow.domain.media.MediaDownloadController
import me.floow.domain.media.MediaDownloadPriority
import me.floow.domain.media.MediaDownloadState
import platform.Foundation.NSCachesDirectory
import platform.Foundation.NSData
import platform.Foundation.NSFileHandle
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUserDomainMask
import platform.Foundation.closeFile
import platform.Foundation.create
import platform.Foundation.fileHandleForWritingAtPath
import platform.Foundation.writeData

@OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)
class IosMediaDownloadManager(
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) : MediaDownloadController {

    private val httpClient = HttpClient(Darwin)

    private val _states = MutableStateFlow<Map<String, MediaDownloadState>>(emptyMap())
    override val states: StateFlow<Map<String, MediaDownloadState>> = _states.asStateFlow()

    private val activeJobs = mutableMapOf<String, Job>()
    private val fileManager = NSFileManager.defaultManager

    private val cacheBaseDir: String by lazy {
        val caches = (NSSearchPathForDirectoriesInDomains(
            NSCachesDirectory,
            NSUserDomainMask,
            true
        ).firstOrNull() as? String) ?: NSTemporaryDirectory()
        val path = if (caches.endsWith("/")) "${caches}flow_media_cache/" else "$caches/flow_media_cache/"
        fileManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        path
    }

    override fun getDownloadState(url: String): MediaDownloadState {
        val current = _states.value[url]
        if (current != null) {
            if (current is MediaDownloadState.Completed) {
                if (!fileManager.fileExistsAtPath(current.localPath)) {
                    _states.update { it - url }
                    return MediaDownloadState.Idle
                }
            }
            return current
        }

        for (type in DownloadableMediaType.values()) {
            val targetPath = getTargetFilePath(url, type)
            if (fileManager.fileExistsAtPath(targetPath)) {
                val completed = MediaDownloadState.Completed(targetPath)
                _states.update { it + (url to completed) }
                return completed
            }
        }
        return MediaDownloadState.Idle
    }

    override fun isDownloaded(url: String, mediaType: DownloadableMediaType): Boolean {
        val path = getTargetFilePath(url, mediaType)
        return fileManager.fileExistsAtPath(path)
    }

    override fun getLocalPathIfDownloaded(url: String, mediaType: DownloadableMediaType): String? {
        val path = getTargetFilePath(url, mediaType)
        if (fileManager.fileExistsAtPath(path)) {
            if (_states.value[url] !is MediaDownloadState.Completed) {
                _states.update { it + (url to MediaDownloadState.Completed(path)) }
            }
            return path
        }
        return null
    }

    override fun registerLocalFile(url: String, localPath: String, mediaType: DownloadableMediaType) {
        if (url.isBlank() || localPath.isBlank()) return
        val cleanPath = if (localPath.startsWith("file://")) localPath.removePrefix("file://") else localPath
        if (!fileManager.fileExistsAtPath(cleanPath)) return

        activeJobs.remove(url)?.cancel()

        val targetPath = getTargetFilePath(url, mediaType)
        if (cleanPath != targetPath) {
            fileManager.removeItemAtPath(targetPath, error = null)
            fileManager.copyItemAtPath(cleanPath, toPath = targetPath, error = null)
        }
        val finalPath = if (fileManager.fileExistsAtPath(targetPath)) targetPath else cleanPath
        _states.update { it + (url to MediaDownloadState.Completed(finalPath)) }
    }

    override fun enqueue(url: String, mediaType: DownloadableMediaType, priority: MediaDownloadPriority) {
        if (url.isBlank() || url.startsWith("file://")) return

        val targetPath = getTargetFilePath(url, mediaType)
        if (fileManager.fileExistsAtPath(targetPath)) {
            _states.update { it + (url to MediaDownloadState.Completed(targetPath)) }
            return
        }

        if (activeJobs.containsKey(url)) return

        _states.update { it + (url to MediaDownloadState.Downloading(0f, 0L, 0L)) }

        val job = scope.launch(Dispatchers.IO) {
            val tempPath = "$targetPath.tmp"
            try {
                val effectiveUrl = when {
                    url.startsWith("//") -> "https:$url"
                    url.startsWith("/") -> "https://floow.me$url"
                    else -> url
                }

                fileManager.removeItemAtPath(tempPath, error = null)
                fileManager.createFileAtPath(tempPath, contents = null, attributes = null)
                val handle = NSFileHandle.fileHandleForWritingAtPath(tempPath)
                    ?: error("Failed to open file for writing at $tempPath")

                try {
                    httpClient.prepareGet(effectiveUrl).execute { response ->
                        if (!response.status.isSuccess()) {
                            error("HTTP ${response.status.value} from $effectiveUrl")
                        }
                        val channel = response.bodyAsChannel()
                        val buffer = ByteArray(64 * 1024)
                        while (!channel.isClosedForRead) {
                            val read = channel.readAvailable(buffer, 0, buffer.size)
                            if (read <= 0) break
                            val chunkData = buffer.usePinned { pinned ->
                                NSData.create(bytes = pinned.addressOf(0), length = read.toULong())
                            }
                            handle.writeData(chunkData)
                        }
                    }
                } finally {
                    handle.closeFile()
                }

                fileManager.removeItemAtPath(targetPath, error = null)
                fileManager.moveItemAtPath(tempPath, toPath = targetPath, error = null)

                _states.update { it + (url to MediaDownloadState.Completed(targetPath)) }
            } catch (e: CancellationException) {
                fileManager.removeItemAtPath(tempPath, error = null)
                throw e
            } catch (e: Exception) {
                fileManager.removeItemAtPath(tempPath, error = null)
                if (priority == MediaDownloadPriority.BACKGROUND) {
                    _states.update { it - url }
                } else {
                    _states.update { it + (url to MediaDownloadState.Failed(e.message ?: "Download failed")) }
                }
            } finally {
                activeJobs.remove(url)
            }
        }
        activeJobs[url] = job
    }

    override fun enqueueAutoDownload(url: String, mediaType: DownloadableMediaType, sizeBytes: Long) {
        enqueue(url, mediaType, MediaDownloadPriority.BACKGROUND)
    }

    override fun cancel(url: String) {
        activeJobs.remove(url)?.cancel()
        _states.update { it - url }
    }

    override fun resetAll() {
        activeJobs.values.forEach { it.cancel() }
        activeJobs.clear()
        _states.value = emptyMap()
    }

    private fun getTargetFilePath(url: String, mediaType: DownloadableMediaType): String {
        val ext = when (mediaType) {
            DownloadableMediaType.PHOTO -> ".jpg"
            DownloadableMediaType.VOICE -> ".ogg"
            DownloadableMediaType.VIDEO_CIRCLE -> ".mp4"
        }
        val safeHash = (url.hashCode().toLong() and 0xFFFFFFFFL).toString(16)
        return "${cacheBaseDir}media_${safeHash}$ext"
    }
}
