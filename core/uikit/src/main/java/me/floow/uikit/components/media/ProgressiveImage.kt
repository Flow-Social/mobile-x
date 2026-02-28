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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntSize
import coil.compose.AsyncImage
import coil.compose.AsyncImagePainter
import coil.request.ImageRequest

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
	val context = LocalContext.current
	var targetSize by remember { mutableStateOf(IntSize.Zero) }
	val hasTargetSize = targetSize.width > 0 && targetSize.height > 0
	val requestModifier = Modifier.fillMaxSize()
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
		modifier = modifier
			.background(placeholderColor)
			.onSizeChanged { targetSize = it }
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

	if (hasTargetSize && !lqUrl.isNullOrBlank()) {
		val lqRequest = remember(lqUrl, targetSize) {
			ImageRequest.Builder(context)
				.data(lqUrl)
				.size(targetSize.width, targetSize.height)
				.build()
		}
		AsyncImage(
			model = lqRequest,
			contentDescription = null,
			contentScale = contentScale,
			alpha = 0f,
			modifier = requestModifier,
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
	if (hasTargetSize && !previewTarget.isNullOrBlank()) {
		val previewRequest = remember(previewTarget, targetSize) {
			ImageRequest.Builder(context)
				.data(previewTarget)
				.size(targetSize.width, targetSize.height)
				.build()
		}
		AsyncImage(
			model = previewRequest,
			contentDescription = null,
			contentScale = contentScale,
			alpha = 0f,
			modifier = requestModifier,
			onSuccess = { state ->
				previewPainter = state.painter
			}
		)
	}

	if (
		hasTargetSize &&
		mode == ProgressiveImageMode.DETAIL &&
		!fullUrl.isNullOrBlank() &&
		fullUrl != previewTarget
	) {
		val fullRequest = remember(fullUrl, targetSize) {
			ImageRequest.Builder(context)
				.data(fullUrl)
				.size(targetSize.width, targetSize.height)
				.build()
		}
		AsyncImage(
			model = fullRequest,
			contentDescription = null,
			contentScale = contentScale,
			alpha = 0f,
			modifier = requestModifier,
			onSuccess = { state: AsyncImagePainter.State.Success ->
				fullPainter = state.painter
			}
		)
	}
}
