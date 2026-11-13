package me.floow.uikit.chat.components.videocircle

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.floow.uikit.chat.model.FlightPhase
import me.floow.uikit.chat.model.FlightState

private val RecordingOverlayDimColor = Color(0x5B0F1015)
private val RecordingOverlayGradientTop = Color(0x48FFFFFF)
private val RecordingOverlayGradientMiddle = Color(0x4E121213)
private val RecordingOverlayGradientBottom = Color(0x7A121213)

@Composable
fun VideoCircleFlightOverlay(
    flightState: FlightState?,
    targetBoundsProvider: () -> Rect?,
    onFlightFinished: () -> Unit,
    onLanded: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (flightState == null || flightState.phase != FlightPhase.InFlight) return

    val currentOnFlightFinished by rememberUpdatedState(onFlightFinished)
    val currentOnLanded by rememberUpdatedState(onLanded)
    val density = LocalDensity.current

    val defaultSourceDiameterPx = with(density) { 340.dp.toPx() }
    val defaultTargetDiameterPx = with(density) { 228.dp.toPx() }
    val marginPx = with(density) { 14.dp.toPx() }
    val bottomInsetEstPx = with(density) { 88.dp.toPx() }

    val previewPath = flightState.thumbnailPath ?: flightState.videoPath.orEmpty()
    var thumbnailBitmap by remember(previewPath) {
        mutableStateOf<ImageBitmap?>(
            cachedThumbnail(previewPath)
                ?: flightState.videoPath?.let { cachedThumbnail(it) }
                ?: flightState.thumbnailPath?.let { cachedThumbnail(it) }
        )
    }

    LaunchedEffect(previewPath) {
        if (thumbnailBitmap == null) {
            var counter = 0
            while (thumbnailBitmap == null && counter < 10) {
                val found = cachedThumbnail(previewPath)
                    ?: flightState.videoPath?.let { cachedThumbnail(it) }
                    ?: flightState.thumbnailPath?.let { cachedThumbnail(it) }
                if (found != null) {
                    thumbnailBitmap = found
                    break
                }
                counter++
                withFrameNanos { }
            }
        }
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val screenWidthPx = with(density) { maxWidth.toPx() }
        val screenHeightPx = with(density) { maxHeight.toPx() }

        val sourceBounds = flightState.sourceBounds
        val sourceDiameterPx = remember(flightState.messageUiKey) {
            sourceBounds?.width?.takeIf { it > 0f } ?: defaultSourceDiameterPx
        }
        val sourceCenterXPx = remember(flightState.messageUiKey) {
            sourceBounds?.let { (it.left + it.right) / 2f }?.takeIf { it > 0f } ?: (screenWidthPx / 2f)
        }
        val sourceCenterYPx = remember(flightState.messageUiKey) {
            sourceBounds?.let { (it.top + it.bottom) / 2f }?.takeIf { it > 0f }
                ?: (screenHeightPx / 2f)
        }

        val defaultFallbackTarget = remember(flightState.messageUiKey, screenWidthPx, screenHeightPx) {
            Rect(
                left = screenWidthPx - marginPx - defaultTargetDiameterPx,
                top = screenHeightPx - bottomInsetEstPx - defaultTargetDiameterPx,
                right = screenWidthPx - marginPx,
                bottom = screenHeightPx - bottomInsetEstPx,
            )
        }

        val targetBoundsState = remember(flightState.messageUiKey) {
            val initial = flightState.targetBounds
                ?: targetBoundsProvider()?.takeIf {
                    it.width > 0f &&
                        it.height >= defaultTargetDiameterPx * 0.70f &&
                        it.width <= defaultTargetDiameterPx * 1.45f &&
                        it.right >= screenWidthPx * 0.4f
                }
                ?: defaultFallbackTarget
            mutableStateOf(initial)
        }

        val flightProgress = remember(flightState.messageUiKey) { Animatable(0f) }
        val landingFadeProgress = remember(flightState.messageUiKey) { Animatable(0f) }

        LaunchedEffect(flightState.messageUiKey) {
            while (flightProgress.value < 1f) {
                val liveBounds = targetBoundsProvider()
                if (liveBounds != null &&
                    liveBounds.width > 0f &&
                    liveBounds.height >= defaultTargetDiameterPx * 0.70f &&
                    liveBounds.width <= defaultTargetDiameterPx * 1.45f &&
                    liveBounds.right >= screenWidthPx * 0.4f
                ) {
                    if (targetBoundsState.value != liveBounds) {
                        targetBoundsState.value = liveBounds
                    }
                }
                withFrameNanos { }
            }
        }

        LaunchedEffect(flightState.messageUiKey) {
            flightProgress.animateTo(
                targetValue = 1f,
                animationSpec = VideoCircleMotion.FlightSpec,
            )

            currentOnLanded()
            landingFadeProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = VideoCircleMotion.LandingFadeDurationMs,
                    easing = FastOutLinearInEasing,
                ),
            )
            currentOnFlightFinished()
        }

        val landingAlpha = (1f - landingFadeProgress.value).coerceIn(0f, 1f)

        Box(
            modifier = Modifier.fillMaxSize(),
        ) {
            // Backdrop dim
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fadeP = VideoCircleMotion.FlightFadeEasing.transform(flightProgress.value)
                        alpha = ((1f - fadeP) * landingAlpha).coerceIn(0f, 1f)
                    }
                    .background(RecordingOverlayDimColor),
            )

            // Backdrop gradient
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val fadeP = VideoCircleMotion.FlightFadeEasing.transform(flightProgress.value)
                        alpha = ((1f - fadeP) * landingAlpha).coerceIn(0f, 1f)
                    }
                    .background(
                        brush = Brush.verticalGradient(
                            colors = listOf(
                                RecordingOverlayGradientTop,
                                RecordingOverlayGradientMiddle,
                                RecordingOverlayGradientBottom,
                            ),
                        ),
                    ),
            )

            // Flying Circle with zero re-layout
            Box(
                modifier = Modifier
                    .size(340.dp)
                    .align(Alignment.TopStart)
                    .graphicsLayer {
                        val p = flightProgress.value
                        val landingFade = landingFadeProgress.value

                        val pX = VideoCircleMotion.FlightArcX.transform(p)
                        val pY = VideoCircleMotion.FlightArcY.transform(p)

                        val liveTarget = targetBoundsState.value
                        val targetDiameterPx = liveTarget.width.takeIf { it > 0f && it <= defaultTargetDiameterPx * 1.35f }
                            ?: defaultTargetDiameterPx
                        val targetCenterXPx = (liveTarget.left + liveTarget.right) / 2f
                        val targetCenterYPx = (liveTarget.top + liveTarget.bottom) / 2f

                        val scaleP = VideoCircleMotion.FlightScaleEasing.transform(p)
                        val curDiameter = sourceDiameterPx + (targetDiameterPx - sourceDiameterPx) * scaleP

                        val curCenterX = sourceCenterXPx + (targetCenterXPx - sourceCenterXPx) * pX
                        val curCenterY = sourceCenterYPx + (targetCenterYPx - sourceCenterYPx) * pY

                        val baseScale = if (defaultSourceDiameterPx > 0f) curDiameter / defaultSourceDiameterPx else 1f

                        scaleX = baseScale
                        scaleY = baseScale
                        translationX = curCenterX - (defaultSourceDiameterPx / 2f)
                        translationY = curCenterY - (defaultSourceDiameterPx / 2f)
                        alpha = (1f - landingFade).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin.Center
                        clip = true
                        shape = CircleShape
                    },
            ) {
                val currentBitmap = thumbnailBitmap
                if (currentBitmap != null) {
                    Image(
                        bitmap = currentBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else if (previewPath.isNotBlank()) {
                    AsyncImage(
                        model = previewPath,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    VideoCircleShell(size = 340.dp)
                }
            }
        }
    }
}
