package me.floow.shared.media.download

import android.content.Context
import me.floow.domain.media.MediaDownloadController
import me.floow.shared.auth.AndroidTokenContext
import org.koin.mp.KoinPlatformTools

actual fun createMediaDownloadController(): MediaDownloadController {
    val context = AndroidTokenContext.get()
        ?: KoinPlatformTools.defaultContext().getOrNull()?.getOrNull<Context>()
    return if (context != null) {
        me.floow.data.media.MediaDownloadManager(context)
    } else {
        NoOpMediaDownloadController()
    }
}
