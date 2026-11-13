package me.floow.shared.media.download

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import me.floow.domain.media.DownloadableMediaType
import me.floow.domain.media.MediaDownloadController
import me.floow.domain.media.MediaDownloadPriority
import me.floow.domain.media.MediaDownloadState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MediaDownloadControllerTest {

    private class FakeMediaDownloadController : MediaDownloadController {
        private val _states = MutableStateFlow<Map<String, MediaDownloadState>>(emptyMap())
        override val states: StateFlow<Map<String, MediaDownloadState>> = _states.asStateFlow()

        private val downloadedFiles = mutableMapOf<String, String>()

        override fun getDownloadState(url: String): MediaDownloadState {
            val state = _states.value[url]
            if (state != null) return state
            val local = downloadedFiles[url]
            return if (local != null) MediaDownloadState.Completed(local) else MediaDownloadState.Idle
        }

        override fun isDownloaded(url: String, mediaType: DownloadableMediaType): Boolean {
            return downloadedFiles.containsKey(url)
        }

        override fun getLocalPathIfDownloaded(url: String, mediaType: DownloadableMediaType): String? {
            return downloadedFiles[url]
        }

        override fun registerLocalFile(url: String, localPath: String, mediaType: DownloadableMediaType) {
            downloadedFiles[url] = localPath
            _states.update { it + (url to MediaDownloadState.Completed(localPath)) }
        }

        override fun enqueue(url: String, mediaType: DownloadableMediaType, priority: MediaDownloadPriority) {
            _states.update { it + (url to MediaDownloadState.Downloading(0.5f, 500L, 1000L)) }
        }

        override fun enqueueAutoDownload(url: String, mediaType: DownloadableMediaType, sizeBytes: Long) {
            enqueue(url, mediaType, MediaDownloadPriority.BACKGROUND)
        }

        override fun cancel(url: String) {
            _states.update { it - url }
        }

        override fun resetAll() {
            downloadedFiles.clear()
            _states.value = emptyMap()
        }
    }

    @Test
    fun initialState_forUnknownUrl_isIdle() {
        val controller = FakeMediaDownloadController()
        val state = controller.getDownloadState("https://flow.me/photo1.jpg")
        assertEquals(MediaDownloadState.Idle, state)
        assertFalse(controller.isDownloaded("https://flow.me/photo1.jpg", DownloadableMediaType.PHOTO))
        assertNull(controller.getLocalPathIfDownloaded("https://flow.me/photo1.jpg", DownloadableMediaType.PHOTO))
    }

    @Test
    fun registerLocalFile_marksDownloaded_andEmitsCompletedState() {
        val controller = FakeMediaDownloadController()
        val url = "https://flow.me/photo1.jpg"
        val localPath = "/cache/photo1.jpg"

        controller.registerLocalFile(url, localPath, DownloadableMediaType.PHOTO)

        assertTrue(controller.isDownloaded(url, DownloadableMediaType.PHOTO))
        assertEquals(localPath, controller.getLocalPathIfDownloaded(url, DownloadableMediaType.PHOTO))
        val state = controller.getDownloadState(url)
        assertTrue(state is MediaDownloadState.Completed)
        assertEquals(localPath, (state as MediaDownloadState.Completed).localPath)
    }

    @Test
    fun enqueue_transitionsToDownloadingState() {
        val controller = FakeMediaDownloadController()
        val url = "https://flow.me/photo2.jpg"

        controller.enqueue(url, DownloadableMediaType.PHOTO)

        val state = controller.getDownloadState(url)
        assertTrue(state is MediaDownloadState.Downloading)
        assertEquals(0.5f, (state as MediaDownloadState.Downloading).progress)
    }

    @Test
    fun cancel_clearsState() {
        val controller = FakeMediaDownloadController()
        val url = "https://flow.me/photo2.jpg"

        controller.enqueue(url, DownloadableMediaType.PHOTO)
        controller.cancel(url)

        val state = controller.getDownloadState(url)
        assertEquals(MediaDownloadState.Idle, state)
    }

    // Тест правила показа иконки скачивания
    @Test
    fun downloadOverlayVisibility_ruleEvaluation() {
        fun shouldShowDownloadOverlay(
            isImageLoaded: Boolean,
            hasLocalFile: Boolean,
            downloadState: MediaDownloadState?,
            remoteUrl: String?,
        ): Boolean {
            if (isImageLoaded || hasLocalFile) return false
            if (remoteUrl.isNullOrBlank()) return false
            return downloadState == null || downloadState is MediaDownloadState.Idle
        }

        // 1. Картинка УЖЕ загружена Coil (isImageLoaded = true) -> иконка НЕ должна показываться!
        assertFalse(
            shouldShowDownloadOverlay(
                isImageLoaded = true,
                hasLocalFile = false,
                downloadState = null,
                remoteUrl = "https://flow.me/photo.jpg",
            ),
            "Download overlay MUST NOT show if image is already loaded and displayed"
        )

        // 2. Файл есть на диске -> иконка НЕ должна показываться!
        assertFalse(
            shouldShowDownloadOverlay(
                isImageLoaded = false,
                hasLocalFile = true,
                downloadState = null,
                remoteUrl = "https://flow.me/photo.jpg",
            ),
            "Download overlay MUST NOT show if local file physically exists"
        )

        // 3. Файла нет на диске и картинка еще не загружена -> иконка ДОЛЖНА показываться!
        assertTrue(
            shouldShowDownloadOverlay(
                isImageLoaded = false,
                hasLocalFile = false,
                downloadState = null,
                remoteUrl = "https://flow.me/photo.jpg",
            ),
            "Download overlay MUST show when file is not yet downloaded or rendered"
        )

        // 4. Картинка в процессе загрузки -> оверлей скачивания в режиме Downloading, а не Idle Download
        assertFalse(
            shouldShowDownloadOverlay(
                isImageLoaded = false,
                hasLocalFile = false,
                downloadState = MediaDownloadState.Downloading(0.2f, 20L, 100L),
                remoteUrl = "https://flow.me/photo.jpg",
            ),
            "Idle download overlay must not show when active downloading is in progress"
        )
    }
}
