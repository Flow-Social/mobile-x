package me.floow.uikit.components.media

import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage

internal expect val useDirectAsyncImageFallback: Boolean

enum class ProgressiveImageMode {
    LIST,
    DETAIL
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
    if (useDirectAsyncImageFallback) {
        WasmSafeProgressiveImage(
            lqUrl = lqUrl,
            previewUrl = previewUrl,
            fullUrl = fullUrl,
            mode = mode,
            contentDescription = contentDescription,
            modifier = modifier,
            contentScale = contentScale,
            placeholderColor = placeholderColor,
            onPainterChanged = onPainterChanged
        )
        return
    }

    var lqPainter by remember(lqUrl) { mutableStateOf<Painter?>(null) }
    var lqFailed by remember(lqUrl) { mutableStateOf(false) }
    var previewPainter by remember(previewUrl) { mutableStateOf<Painter?>(null) }
    var fullPainter by remember(fullUrl) { mutableStateOf<Painter?>(null) }

    val displayPainter = when (mode) {
        ProgressiveImageMode.LIST -> {
            when {
                !lqUrl.isNullOrBlank() && !lqFailed && lqPainter == null -> previewPainter
                !lqUrl.isNullOrBlank() && !lqFailed -> previewPainter ?: lqPainter
                else -> previewPainter ?: lqPainter
            }
        }

        ProgressiveImageMode.DETAIL -> fullPainter ?: previewPainter ?: lqPainter
    }

    LaunchedEffect(displayPainter) {
        onPainterChanged?.invoke(displayPainter)
    }

    Box(
        modifier = modifier.background(placeholderColor)
    ) {
        if (displayPainter != null) {
            Image(
                painter = displayPainter,
                contentDescription = contentDescription,
                contentScale = contentScale,
                modifier = Modifier.fillMaxSize()
            )
        }
    }

    if (!lqUrl.isNullOrBlank()) {
        AsyncImage(
            model = lqUrl,
            contentDescription = null,
            contentScale = contentScale,
            alpha = 0f,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { state ->
                lqFailed = false
                if (lqPainter == null) {
                    lqPainter = state.painter
                }
            },
            onError = {
                lqFailed = true
            }
        )
    }

    val previewTarget = when (mode) {
        ProgressiveImageMode.LIST -> previewUrl
        ProgressiveImageMode.DETAIL -> previewUrl ?: fullUrl
    }
    if (!previewTarget.isNullOrBlank()) {
        AsyncImage(
            model = previewTarget,
            contentDescription = null,
            contentScale = contentScale,
            alpha = 0f,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { state ->
                previewPainter = state.painter
            }
        )
    }

    if (mode == ProgressiveImageMode.DETAIL && !fullUrl.isNullOrBlank() && fullUrl != previewTarget) {
        AsyncImage(
            model = fullUrl,
            contentDescription = null,
            contentScale = contentScale,
            alpha = 0f,
            modifier = Modifier.fillMaxSize(),
            onSuccess = { state ->
                fullPainter = state.painter
            }
        )
    }
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
