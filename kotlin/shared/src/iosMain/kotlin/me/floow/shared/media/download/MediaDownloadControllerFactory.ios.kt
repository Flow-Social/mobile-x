package me.floow.shared.media.download

import me.floow.domain.media.MediaDownloadController

actual fun createMediaDownloadController(): MediaDownloadController {
    return IosMediaDownloadManager()
}
