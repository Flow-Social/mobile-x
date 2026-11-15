package me.floow.uikit.util

import androidx.compose.ui.graphics.ImageBitmap
import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi
import kotlin.time.TimeSource

enum class MicroThumbMediaType {
    POST,
    AVATAR,
    BACKGROUND,
    CHAT_ATTACHMENT,
    UNKNOWN
}

expect fun decodeAndBlurMicroThumb(bytes: ByteArray, blurRadius: Int = 6): ImageBitmap?

object MicroThumbDecoder {
    private const val PREFIX = "data:image/"
    private const val BASE64_MARKER = ";base64,"
    private const val MAX_DATA_URI_LENGTH = 2048
    private const val LRU_CAPACITY = 250

    private val lock = MicroThumbLock()
    private val lruCache = LinkedHashMap<String, ImageBitmap>(LRU_CAPACITY, 0.75f)

    private class MediaTypeMetrics {
        var requests: Long = 0
        var hits: Long = 0
        var totalDecodeDurationUs: Long = 0
        var decodeCount: Long = 0
    }

    private val metricsMap = mutableMapOf<MicroThumbMediaType, MediaTypeMetrics>()

    @OptIn(ExperimentalEncodingApi::class)
    fun decode(
        source: String?,
        mediaType: MicroThumbMediaType = MicroThumbMediaType.UNKNOWN,
        blurRadius: Int = 6
    ): ImageBitmap? {
        if (source == null || !source.startsWith(PREFIX) || source.length > MAX_DATA_URI_LENGTH) {
            return null
        }

        // 1. Check LRU Cache
        val cached = lock.withLock {
            val metrics = metricsMap.getOrPut(mediaType) { MediaTypeMetrics() }
            metrics.requests++
            val existing = lruCache.remove(source)
            if (existing != null) {
                lruCache[source] = existing
                metrics.hits++
                existing
            } else {
                null
            }
        }
        if (cached != null) {
            return cached
        }

        // 2. Extract Base64 part
        val markerIndex = source.indexOf(BASE64_MARKER)
        if (markerIndex < 0) return null
        val base64Data = source.substring(markerIndex + BASE64_MARKER.length).trim()
        if (base64Data.isEmpty()) return null

        val bytes = runCatching {
            Base64.Default.decode(base64Data)
        }.getOrNull() ?: return null

        if (bytes.isEmpty()) return null

        val decodedBytes = if (TelegramStrippedThumb.isStrippedThumb(bytes)) {
            TelegramStrippedThumb.inflate(bytes)
        } else {
            bytes
        }

        // 3. Decode & blur on CPU
        val mark = TimeSource.Monotonic.markNow()
        val bitmap = decodeAndBlurMicroThumb(decodedBytes, blurRadius) ?: return null
        val durationUs = mark.elapsedNow().inWholeMicroseconds

        // 4. Store in LRU cache & record metrics
        lock.withLock {
            val metrics = metricsMap.getOrPut(mediaType) { MediaTypeMetrics() }
            metrics.decodeCount++
            metrics.totalDecodeDurationUs += durationUs

            if (lruCache.size >= LRU_CAPACITY) {
                val iterator = lruCache.keys.iterator()
                if (iterator.hasNext()) {
                    iterator.next()
                    iterator.remove()
                }
            }
            lruCache[source] = bitmap
        }

        return bitmap
    }

    fun clearCache() {
        lock.withLock {
            lruCache.clear()
        }
    }

    fun putInCacheForTesting(source: String, bitmap: ImageBitmap) {
        lock.withLock {
            lruCache[source] = bitmap
        }
    }
}
