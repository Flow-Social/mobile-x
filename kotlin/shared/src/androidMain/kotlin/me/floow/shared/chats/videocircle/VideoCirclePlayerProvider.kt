package me.floow.shared.chats.videocircle

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
object VideoCirclePlayerProvider {
    @Volatile
    private var instance: VideoCirclePlayerManager? = null

    fun get(context: Context): VideoCirclePlayerManager {
        return instance ?: synchronized(this) {
            instance ?: run {
                val appContext = context.applicationContext
                val cacheProvider = VideoCircleCacheProvider(appContext)
                lateinit var manager: VideoCirclePlayerManager
                val audioFocus = VideoCircleAudioFocusController(
                    context = appContext,
                    onTransientLoss = { manager.pause() },
                    onPermanentLoss = { manager.pause() },
                )
                manager = VideoCirclePlayerManager(appContext, cacheProvider, audioFocus)
                instance = manager
                manager
            }
        }
    }

    fun release() {
        synchronized(this) {
            instance?.release()
            instance = null
        }
    }
}
