package me.floow.shared.image

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.IntSize
import coil3.SingletonImageLoader
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest

@Composable
internal fun PrefetchImageUrlsEffect(
    urls: List<String>,
    size: IntSize?,
) {
    val context = LocalPlatformContext.current
    val imageLoader = remember(context) { SingletonImageLoader.get(context) }
    val normalizedUrls = remember(urls) {
        urls.mapNotNull { it.trim().takeIf(String::isNotBlank) }.distinct()
    }

    LaunchedEffect(normalizedUrls, size) {
        if (normalizedUrls.isEmpty()) return@LaunchedEffect
        normalizedUrls.forEach { url ->
            val requestBuilder = ImageRequest.Builder(context)
                .data(url)
            if (size != null) {
                requestBuilder.size(size.width, size.height)
            }
            imageLoader.enqueue(requestBuilder.build())
        }
    }
}
