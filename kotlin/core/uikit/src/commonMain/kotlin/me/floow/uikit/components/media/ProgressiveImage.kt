package me.floow.uikit.components.media

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.memory.MemoryCache
import me.floow.uikit.util.MicroThumbDecoder
import me.floow.uikit.util.MicroThumbMediaType

internal expect val useDirectAsyncImageFallback: Boolean

enum class ProgressiveImageMode {
    LIST,
    DETAIL
}

data class ProgressiveTargets(
    val thumb: String?,
    val target: String?
)

fun deriveProgressiveThumb(targetUrl: String?): String? {
    return null
}

fun resolveProgressiveTargets(
    thumbUrl: String?,
    targetUrl: String?
): ProgressiveTargets {
    val cleanTarget = targetUrl?.trim()?.takeIf { it.isNotEmpty() }
    val explicitThumb = thumbUrl?.trim()?.takeIf { it.isNotEmpty() && it != cleanTarget }
    val cleanThumb = explicitThumb ?: deriveProgressiveThumb(cleanTarget)
    return ProgressiveTargets(
        thumb = cleanThumb?.takeIf { it != cleanTarget },
        target = cleanTarget ?: cleanThumb
    )
}

fun resolveProgressiveTargets(
    lqUrl: String?,
    previewUrl: String?,
    fullUrl: String?,
    mode: ProgressiveImageMode = ProgressiveImageMode.LIST
): ProgressiveTargets {
    val resolvedTarget = when (mode) {
        ProgressiveImageMode.LIST -> previewUrl ?: fullUrl ?: lqUrl
        ProgressiveImageMode.DETAIL -> fullUrl ?: previewUrl ?: lqUrl
    }
    val resolvedThumb = lqUrl ?: previewUrl ?: fullUrl
    return resolveProgressiveTargets(
        thumbUrl = resolvedThumb,
        targetUrl = resolvedTarget
    )
}

class ProgressiveImageCoordinator(
    thumbUrl: String? = null,
    targetUrl: String? = null
) {
    var thumbUrl: String? = thumbUrl
        private set
    var targetUrl: String? = targetUrl
        private set

    var isThumbLoaded: Boolean = false
        private set
    var isTargetLoaded: Boolean = false
        private set
    var targetAlpha: Float = 0f
        private set
    var crossfadeProgress: Float = 0f
        private set
    var retainedPreviewModel: String? = null
        private set

    val hasRetainedPreview: Boolean
        get() = retainedPreviewModel != null

    val shouldRenderThumb: Boolean
        get() {
            if (hasRetainedPreview) return true
            if (targetAlpha >= 1f && isTargetLoaded) return false
            val effectiveThumb = effectiveThumbModel
            return !effectiveThumb.isNullOrBlank() && effectiveThumb != targetUrl
        }

    val effectiveThumbModel: String?
        get() = retainedPreviewModel ?: thumbUrl

    val hasBlackFrame: Boolean
        get() {
            val hasVisibleTarget = isTargetLoaded && targetAlpha > 0f
            val hasVisibleThumb = shouldRenderThumb && isThumbLoaded
            val hasVisibleRetained = hasRetainedPreview
            return !hasVisibleTarget && !hasVisibleThumb && !hasVisibleRetained
        }

    fun onThumbLoaded() {
        isThumbLoaded = true
    }

    fun onTargetLoading() {
        // Keeps thumb visible
    }

    fun onTargetLoaded() {
        isTargetLoaded = true
        if (retainedPreviewModel == null && !isThumbLoaded) {
            targetAlpha = 1f
        }
    }

    fun onTargetImmediateCacheHit() {
        isTargetLoaded = true
        targetAlpha = 1f
        retainedPreviewModel = null
    }

    fun onTargetFailed() {
        isTargetLoaded = false
        targetAlpha = 0f
    }

    fun updateCrossfadeProgress(progress: Float) {
        crossfadeProgress = progress.coerceIn(0f, 1f)
        targetAlpha = crossfadeProgress
        if (targetAlpha >= 1f) {
            retainedPreviewModel = null
        }
    }

    fun updateModels(newThumbUrl: String?, newTargetUrl: String?) {
        if (newTargetUrl != targetUrl) {
            if (isTargetLoaded && targetUrl != null) {
                retainedPreviewModel = targetUrl
            } else if (isThumbLoaded && thumbUrl != null) {
                retainedPreviewModel = thumbUrl
            }
            targetUrl = newTargetUrl
            thumbUrl = newThumbUrl
            isTargetLoaded = false
            targetAlpha = 0f
            crossfadeProgress = 0f
        } else if (newThumbUrl != thumbUrl) {
            thumbUrl = newThumbUrl
        }
    }
}

@Composable
fun ProgressiveImage(
    thumbUrl: String?,
    targetUrl: String?,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = Color(0xFF1A1A1A),
    onPainterChanged: ((Painter?) -> Unit)? = null,
    onError: (() -> Unit)? = null
) {
    val targets = remember(thumbUrl, targetUrl) {
        resolveProgressiveTargets(thumbUrl = thumbUrl, targetUrl = targetUrl)
    }

    if (useDirectAsyncImageFallback) {
        WasmSafeProgressiveImage(
            lqUrl = targets.thumb,
            previewUrl = targets.target,
            fullUrl = null,
            mode = ProgressiveImageMode.LIST,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            placeholderColor = placeholderColor,
            onPainterChanged = onPainterChanged
        )
        return
    }

    val resolvedTarget = targets.target
    val resolvedThumb = targets.thumb

    val platformContext = LocalPlatformContext.current
    val imageLoader = remember(platformContext) { SingletonImageLoader.get(platformContext) }
    val isTargetInMemory = remember(resolvedTarget, imageLoader) {
        if (resolvedTarget.isNullOrBlank()) false
        else {
            imageLoader.memoryCache?.get(MemoryCache.Key(resolvedTarget)) != null
        }
    }

    val isMicroThumb = remember(resolvedThumb) {
        resolvedThumb?.startsWith("data:image/") == true
    }

    val microThumbBitmap = remember(resolvedThumb) {
        if (isMicroThumb) {
            MicroThumbDecoder.decode(resolvedThumb)
        } else {
            null
        }
    }

    // Если target уже в памяти Coil, thumb отсутствует или совпадает с target, целевая картинка видна сразу (alpha=1f)
    val shouldShowImmediately = isTargetInMemory || resolvedThumb.isNullOrBlank() || resolvedThumb == resolvedTarget
    var isTargetLoaded by remember(resolvedTarget) { mutableStateOf(shouldShowImmediately) }
    var retainedPainter by remember { mutableStateOf<Painter?>(null) }
    var latestRenderedPainter by remember { mutableStateOf<Painter?>(null) }
    var previousTarget by remember { mutableStateOf(resolvedTarget) }

    if (previousTarget != resolvedTarget) {
        if (latestRenderedPainter != null) {
            retainedPainter = latestRenderedPainter
        }
        previousTarget = resolvedTarget
    }

    // Если target уже загрузился или у нас уже есть стабильный painter для этого URL, альфа сразу 1f
    val targetAlpha by animateFloatAsState(
        targetValue = if (isTargetLoaded) 1f else 0f,
        animationSpec = if ((isTargetLoaded && retainedPainter == null && latestRenderedPainter != null) || shouldShowImmediately) {
            androidx.compose.animation.core.snap()
        } else {
            tween(durationMillis = 200)
        },
        label = "progressive_image_fade"
    )

    LaunchedEffect(targetAlpha, isTargetLoaded) {
        if (targetAlpha >= 1f && isTargetLoaded) {
            retainedPainter = null
        }
    }

    Box(
        modifier = modifier.background(placeholderColor)
    ) {
        // Слой 1: Retained preview (сохраняет изображение при переходах local preview -> remote)
        val activeRetained = retainedPainter ?: if (!isTargetLoaded) latestRenderedPainter else null
        if (activeRetained != null && (targetAlpha < 1f || !isTargetLoaded)) {
            androidx.compose.foundation.Image(
                painter = activeRetained,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Слой 2: Thumb (микро-превью синхронно декодируется в кадр 0, обычный thumb через AsyncImage)
        if (targetAlpha < 1f && !resolvedThumb.isNullOrBlank() && resolvedThumb != resolvedTarget) {
            if (microThumbBitmap != null) {
                androidx.compose.foundation.Image(
                    bitmap = microThumbBitmap,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                AsyncImage(
                    model = resolvedThumb,
                    contentDescription = contentDescription,
                    contentScale = contentScale,
                    modifier = Modifier.fillMaxSize(),
                    onSuccess = { state ->
                        if (!isTargetLoaded) {
                            latestRenderedPainter = state.painter
                            onPainterChanged?.invoke(state.painter)
                        }
                    }
                )
            }
        }

        // Слой 3: Target (проявляется поверх thumb / retained)
        if (!resolvedTarget.isNullOrBlank()) {
            AsyncImage(
                model = resolvedTarget,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = targetAlpha },
                onSuccess = { state ->
                    latestRenderedPainter = state.painter
                    isTargetLoaded = true
                    onPainterChanged?.invoke(state.painter)
                },
                onError = {
                    onError?.invoke()
                    if (resolvedThumb.isNullOrBlank() || resolvedThumb == resolvedTarget) {
                        if (retainedPainter == null && latestRenderedPainter == null) {
                            onPainterChanged?.invoke(null)
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ProgressiveImage(
    lqUrl: String?,
    previewUrl: String?,
    fullUrl: String?,
    mode: ProgressiveImageMode,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = Color(0xFF1A1A1A),
    onPainterChanged: ((Painter?) -> Unit)? = null
) {
    val targets = remember(lqUrl, previewUrl, fullUrl, mode) {
        resolveProgressiveTargets(
            lqUrl = lqUrl,
            previewUrl = previewUrl,
            fullUrl = fullUrl,
            mode = mode
        )
    }

    ProgressiveImage(
        thumbUrl = targets.thumb,
        targetUrl = targets.target,
        contentDescription = contentDescription,
        modifier = modifier,
        contentScale = contentScale,
        placeholderColor = placeholderColor,
        onPainterChanged = onPainterChanged
    )
}

@Composable
private fun WasmSafeProgressiveImage(
    lqUrl: String?,
    previewUrl: String?,
    fullUrl: String?,
    mode: ProgressiveImageMode,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
    placeholderColor: Color = Color(0xFF1A1A1A),
    onPainterChanged: ((Painter?) -> Unit)? = null
) {
    val urlCandidates = remember(lqUrl, previewUrl, fullUrl, mode) {
        when (mode) {
            ProgressiveImageMode.LIST -> listOf(lqUrl, previewUrl, fullUrl)
            ProgressiveImageMode.DETAIL -> listOf(fullUrl, previewUrl, lqUrl)
        }.mapNotNull { it?.takeIf(String::isNotBlank) }.distinct()
    }
    var currentIndex by remember(urlCandidates) { mutableStateOf(0) }

    val currentUrl = urlCandidates.getOrNull(currentIndex)

    LaunchedEffect(currentUrl) {
        if (currentUrl == null) {
            onPainterChanged?.invoke(null)
        }
    }

    Box(modifier = modifier.background(placeholderColor)) {
        if (currentUrl != null) {
            AsyncImage(
                model = currentUrl,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize(),
                onSuccess = { state ->
                    onPainterChanged?.invoke(state.painter)
                },
                onError = {
                    if (currentIndex < urlCandidates.lastIndex) {
                        currentIndex += 1
                    } else {
                        onPainterChanged?.invoke(null)
                    }
                }
            )
        }
    }
}
