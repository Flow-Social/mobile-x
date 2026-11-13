package me.floow.shared.profile.ui.settings

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.context.GlobalContext
import java.io.File

actual object PlatformMediaCacheManager {
    private fun getCacheDirs(context: Context): List<File> = listOf(
        File(context.cacheDir, "flow_image_cache"),
        File(context.cacheDir, "image_cache"),
        File(context.cacheDir, "chat_images"),
        File(context.cacheDir, "voice_media_downloads"),
        File(context.cacheDir, "media3_voice_cache"),
        File(context.cacheDir, "voice_media_cache"),
        File(context.cacheDir, "video_circle_media_cache"),
        File(context.cacheDir, "camera_photos"),
    )

    actual suspend fun getCacheSizeBytes(): Long = withContext(Dispatchers.IO) {
        val context = GlobalContext.getOrNull()?.getOrNull<Context>() ?: return@withContext 0L
        getCacheDirs(context).sumOf { getFolderSize(it) }
    }

    actual suspend fun clearCache() = withContext(Dispatchers.IO) {
        val context = GlobalContext.getOrNull()?.getOrNull<Context>() ?: return@withContext
        getCacheDirs(context).forEach { dir ->
            runCatching {
                if (dir.exists()) {
                    dir.listFiles()?.forEach { file ->
                        file.deleteRecursively()
                    }
                }
            }
        }
    }

    private fun getFolderSize(file: File): Long {
        if (!file.exists()) return 0L
        if (file.isFile) return file.length()
        return file.listFiles()?.sumOf { getFolderSize(it) } ?: 0L
    }
}
