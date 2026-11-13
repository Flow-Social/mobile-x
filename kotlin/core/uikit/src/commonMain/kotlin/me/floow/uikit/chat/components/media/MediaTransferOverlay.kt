package me.floow.uikit.chat.components.media

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.chat.model.MediaTransferActionIcon
import me.floow.uikit.chat.model.MediaTransferUiState

@Composable
fun MediaTransferOverlay(
	state: MediaTransferUiState,
	modifier: Modifier = Modifier,
	shape: Shape = CircleShape,
	controlSize: Dp = 72.dp,
	controlColor: Color = Color.Black.copy(alpha = 0.38f),
	contentColor: Color = Color.White,
	progressColor: Color = Color.White,
	onRetry: (() -> Unit)? = null,
	onCancel: (() -> Unit)? = null,
	onClick: (() -> Unit)? = null,
) {
	if (!state.visible) return

	val clickAction = when {
		state.canRetry && onRetry != null -> onRetry
		state.canCancel && onCancel != null -> onCancel
		else -> onClick
	}

	Box(
		modifier = modifier
			.fillMaxSize()
			.clip(shape),
		contentAlignment = Alignment.Center,
	) {
		if (state.showBlur) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.blur(8.dp),
			)
		}
		if (state.showDim) {
			Box(
				modifier = Modifier
					.fillMaxSize()
					.background(Color.Black.copy(alpha = 0.24f)),
			)
		}

		MediaTransferControl(
			state = state,
			controlSize = controlSize,
			controlColor = controlColor,
			contentColor = contentColor,
			progressColor = progressColor,
			onRetry = onRetry,
			onCancel = onCancel,
			onClick = onClick,
		)
	}
}

@Composable
private fun MediaTransferControl(
	state: MediaTransferUiState,
	controlSize: Dp,
	controlColor: Color,
	contentColor: Color,
	progressColor: Color,
	onRetry: (() -> Unit)?,
	onCancel: (() -> Unit)?,
	onClick: (() -> Unit)?,
) {
	val clickAction = when {
		state.canRetry && onRetry != null -> onRetry
		state.canCancel && onCancel != null -> onCancel
		else -> onClick
	}
	val iconSize = (controlSize * 0.48f).coerceAtLeast(14.dp)

	Column(
		horizontalAlignment = Alignment.CenterHorizontally,
		verticalArrangement = Arrangement.spacedBy(6.dp),
	) {
		Box(
			modifier = Modifier
				.size(controlSize)
				.clip(CircleShape)
				.background(controlColor)
				.then(if (clickAction != null) Modifier.clickable(onClick = clickAction) else Modifier),
			contentAlignment = Alignment.Center,
		) {
			MediaTransferProgressRing(
				progress = state.progress,
				indeterminate = state.shouldShowIndeterminateRing(),
				progressColor = progressColor,
				trackColor = Color.White.copy(alpha = 0.24f),
				modifier = Modifier.fillMaxSize(),
			)
			AnimatedContent(
				targetState = state.actionIcon,
				transitionSpec = {
					(fadeIn(animationSpec = tween(180)) + scaleIn(initialScale = 0.8f, animationSpec = tween(180)))
						.togetherWith(
							fadeOut(animationSpec = tween(140)) + scaleOut(targetScale = 0.8f, animationSpec = tween(140))
						)
				},
				label = "media_transfer_icon",
			) { targetIcon ->
				when (targetIcon) {
					MediaTransferActionIcon.Download -> {
						TelegramDownloadIcon(
							modifier = Modifier.size(iconSize),
							color = contentColor,
						)
					}
					MediaTransferActionIcon.Cancel -> {
						TelegramCancelIcon(
							modifier = Modifier.size(iconSize),
							color = contentColor,
						)
					}
					else -> {
						targetIcon.toImageVector()?.let { imageVector ->
							Icon(
								imageVector = imageVector,
								contentDescription = null,
								tint = contentColor,
								modifier = Modifier.size(iconSize),
							)
						}
					}
				}
			}
		}

		if (!state.sizeText.isNullOrBlank()) {
			Box(
				modifier = Modifier
					.clip(RoundedCornerShape(10.dp))
					.background(Color.Black.copy(alpha = 0.42f))
					.padding(horizontal = 8.dp, vertical = 2.5.dp),
				contentAlignment = Alignment.Center,
			) {
				Text(
					text = state.sizeText,
					color = Color.White,
					fontSize = 11.sp,
					fontWeight = FontWeight.Medium,
					lineHeight = 13.sp,
				)
			}
		}
	}
}

@Composable
private fun TelegramDownloadIcon(
	modifier: Modifier = Modifier,
	color: Color = Color.White,
) {
	Canvas(modifier = modifier) {
		val strokeW = (size.minDimension * 0.12f).coerceIn(2.2.dp.toPx(), 3.2.dp.toPx())
		val cx = size.width / 2f
		val cy = size.height / 2f
		val halfH = size.height * 0.36f
		val wingW = size.width * 0.32f
		val wingH = size.height * 0.30f
		val yStart = cy - halfH
		val yEnd = cy + halfH

		// Vertical stem
		drawLine(
			color = color,
			start = Offset(cx, yStart),
			end = Offset(cx, yEnd),
			strokeWidth = strokeW,
			cap = StrokeCap.Round,
		)
		// Left wing
		drawLine(
			color = color,
			start = Offset(cx - wingW, yEnd - wingH),
			end = Offset(cx, yEnd),
			strokeWidth = strokeW,
			cap = StrokeCap.Round,
		)
		// Right wing
		drawLine(
			color = color,
			start = Offset(cx + wingW, yEnd - wingH),
			end = Offset(cx, yEnd),
			strokeWidth = strokeW,
			cap = StrokeCap.Round,
		)
	}
}

@Composable
private fun TelegramCancelIcon(
	modifier: Modifier = Modifier,
	color: Color = Color.White,
) {
	Canvas(modifier = modifier) {
		val strokeW = (size.minDimension * 0.12f).coerceIn(2.2.dp.toPx(), 3.2.dp.toPx())
		val cx = size.width / 2f
		val cy = size.height / 2f
		val d = size.minDimension * 0.28f

		drawLine(
			color = color,
			start = Offset(cx - d, cy - d),
			end = Offset(cx + d, cy + d),
			strokeWidth = strokeW,
			cap = StrokeCap.Round,
		)
		drawLine(
			color = color,
			start = Offset(cx + d, cy - d),
			end = Offset(cx - d, cy + d),
			strokeWidth = strokeW,
			cap = StrokeCap.Round,
		)
	}
}

@Composable
private fun MediaTransferProgressRing(
	progress: Float?,
	indeterminate: Boolean,
	progressColor: Color,
	trackColor: Color,
	modifier: Modifier = Modifier,
) {
	if (progress == null && !indeterminate) return

	val transition = rememberInfiniteTransition(label = "media_transfer_ring")
	val rotation by transition.animateFloat(
		initialValue = 0f,
		targetValue = 360f,
		animationSpec = infiniteRepeatable(animation = tween(durationMillis = 950, easing = LinearEasing)),
		label = "media_transfer_ring_rotation",
	)
	val smoothProgress by animateFloatAsState(
		targetValue = progress ?: 0f,
		animationSpec = tween(durationMillis = 150, easing = LinearEasing),
		label = "media_transfer_smooth_progress",
	)
	Canvas(modifier = modifier) {
		val strokeWidth = (size.minDimension * 0.055f).coerceIn(2.dp.toPx(), 4.dp.toPx())
		val inset = strokeWidth / 2f
		val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
		val topLeft = Offset(inset, inset)
		drawArc(
			color = trackColor,
			startAngle = -90f,
			sweepAngle = 360f,
			useCenter = false,
			topLeft = topLeft,
			size = arcSize,
			style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
		)
		when {
			progress != null -> drawArc(
				color = progressColor,
				startAngle = -90f,
				sweepAngle = 360f * smoothProgress.coerceIn(0f, 1f),
				useCenter = false,
				topLeft = topLeft,
				size = arcSize,
				style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
			)
			indeterminate -> drawArc(
				color = progressColor,
				startAngle = rotation - 90f,
				sweepAngle = 78f,
				useCenter = false,
				topLeft = topLeft,
				size = arcSize,
				style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
			)
		}
	}
}

private fun MediaTransferActionIcon.toImageVector(): ImageVector? {
	return when (this) {
		MediaTransferActionIcon.None,
		MediaTransferActionIcon.Loader -> null
		MediaTransferActionIcon.Upload -> Icons.Filled.KeyboardArrowUp
		MediaTransferActionIcon.Download -> Icons.Filled.KeyboardArrowDown
		MediaTransferActionIcon.Retry -> Icons.Filled.Refresh
		MediaTransferActionIcon.Cancel -> Icons.Filled.Close
		MediaTransferActionIcon.Play -> Icons.Filled.PlayArrow
	}
}

private fun MediaTransferUiState.shouldShowIndeterminateRing(): Boolean {
	if (progress != null) return false
	return when (actionIcon) {
		MediaTransferActionIcon.Loader,
		MediaTransferActionIcon.Upload,
		MediaTransferActionIcon.Cancel -> true
		MediaTransferActionIcon.Download,
		MediaTransferActionIcon.None,
		MediaTransferActionIcon.Retry,
		MediaTransferActionIcon.Play -> false
	}
}
