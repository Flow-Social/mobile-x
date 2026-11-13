package me.floow.shared.media.voice

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

@OptIn(UnstableApi::class)
internal class VoiceMediaCacheProvider(context: Context) {

    private val appContext = context.applicationContext

    val cache: SimpleCache
        get() = sharedCache(appContext)

    fun dataSourceFactory(): DataSource.Factory {
        val upstream = DefaultDataSource.Factory(appContext)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstream)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    companion object {
        private const val CacheDirName = "media3_voice_cache"
        private const val CacheSizeBytes = 128L * 1024L * 1024L

        private val cacheLock = Any()
        @Volatile
        private var processCache: SimpleCache? = null

        private fun sharedCache(context: Context): SimpleCache {
            processCache?.let { return it }
            return synchronized(cacheLock) {
                processCache ?: SimpleCache(
                    File(context.cacheDir, CacheDirName),
                    LeastRecentlyUsedCacheEvictor(CacheSizeBytes),
                    StandaloneDatabaseProvider(context),
                ).also { processCache = it }
            }
        }
    }
}
