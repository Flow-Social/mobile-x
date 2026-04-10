package me.floow.uikit.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.IntrinsicSize
import kotlin.math.roundToInt
import me.floow.uikit.R
import me.floow.uikit.chat.model.ChatContextMenuAction
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import kotlin.math.max

@Composable
internal fun MessageContextMenuOverlay(
	actions: List<ChatContextMenuAction>,
	anchorRect: Rect?,
	highlightRect: Rect?,
	onActionClick: (ChatContextMenuAction) -> Unit,
	onDismissRequest: () -> Unit,
	modifier: Modifier = Modifier
) {
	if (actions.isEmpty()) return

	val haptic = LocalHapticFeedback.current
	LaunchedEffect(anchorRect, actions) {
		haptic.performHapticFeedback(HapticFeedbackType.LongPress)
	}
	val visibilityState = remember {
		MutableTransitionState(false).apply { targetState = true }
	}

	BoxWithConstraints(
		modifier = modifier
			.fillMaxSize()
	) {
		Box(
			modifier = Modifier
				.fillMaxSize()
				.graphicsLayer {
					compositingStrategy = CompositingStrategy.Offscreen
				}
				.drawWithContent {
					drawRect(Color.Black.copy(alpha = 0.18f))
					highlightRect?.let { rect ->
						drawRoundRect(
							color = Color.Transparent,
							topLeft = rect.topLeft,
							size = rect.size,
							cornerRadius = CornerRadius(22.dp.toPx(), 22.dp.toPx()),
							blendMode = BlendMode.Clear
						)
					}
				}
				.clickable(
					interactionSource = remember { MutableInteractionSource() },
					indication = null,
					onClick = { visibilityState.targetState = false }
				)
		)
		val density = LocalDensity.current
		val horizontalPaddingPx = with(density) { 16.dp.toPx() }
		val verticalPaddingPx = with(density) { 16.dp.toPx() }
		val overlapWithAnchorPx = with(density) { 10.dp.toPx() }
		val viewportWidthPx = with(density) { maxWidth.toPx() }
		val viewportHeightPx = with(density) { maxHeight.toPx() }
		var menuSizePx by remember { mutableStateOf(IntSize.Zero) }
		val estimatedMenuSizePx = remember(actions, density) {
			val estimatedWidth = with(density) { 252.dp.toPx().roundToInt() }
			val itemHeight = with(density) { 52.dp.toPx().roundToInt() }
			val dividerHeight = with(density) { 1.dp.toPx().roundToInt() }
			val dividerCount = max(0, actions.size - 1)
			IntSize(
				width = estimatedWidth,
				height = (actions.size * itemHeight) + (dividerCount * dividerHeight)
			)
		}
		val menuOffset = remember(anchorRect, menuSizePx, estimatedMenuSizePx, viewportWidthPx, viewportHeightPx) {
			resolveAnchoredMenuOffset(
				anchorRect = anchorRect,
				menuSizePx = if (menuSizePx == IntSize.Zero) estimatedMenuSizePx else menuSizePx,
				viewportWidthPx = viewportWidthPx,
				viewportHeightPx = viewportHeightPx,
				horizontalPaddingPx = horizontalPaddingPx,
				verticalPaddingPx = verticalPaddingPx,
				overlapWithAnchorPx = overlapWithAnchorPx
			)
		}
		AnimatedVisibility(
			visibleState = visibilityState,
			enter = fadeIn(animationSpec = tween(160)) + scaleIn(animationSpec = tween(160), initialScale = 0.88f),
			exit = fadeOut(animationSpec = tween(120)) + scaleOut(animationSpec = tween(120), targetScale = 0.92f),
			modifier = Modifier.offset { menuOffset }
		) {
			Column(
				modifier = Modifier
					.wrapContentWidth()
					.width(IntrinsicSize.Max)
					.widthIn(min = 220.dp, max = 280.dp)
					.background(
						color = MaterialTheme.colorScheme.surfaceContainerHighest,
						shape = RoundedCornerShape(16.dp)
					)
					.clickable(
						interactionSource = remember { MutableInteractionSource() },
						indication = null,
						onClick = {}
					)
					.onSizeChanged { size -> menuSizePx = size }
			) {
				actions.forEachIndexed { index, action ->
					MessageContextMenuItem(
						action = action,
						onClick = { onActionClick(action) }
					)
					if (shouldShowDividerAfter(index, actions)) {
						HorizontalDivider(
							color = MaterialTheme.colorScheme.outlineVariant,
							thickness = 1.dp
						)
					}
				}
			}
		}
	}

	LaunchedEffect(visibilityState.currentState, visibilityState.targetState) {
		if (!visibilityState.currentState && !visibilityState.targetState) {
			onDismissRequest()
		}
	}
}

private fun resolveAnchoredMenuOffset(
	anchorRect: Rect?,
	menuSizePx: IntSize,
	viewportWidthPx: Float,
	viewportHeightPx: Float,
	horizontalPaddingPx: Float,
	verticalPaddingPx: Float,
	overlapWithAnchorPx: Float
): IntOffset {
	if (menuSizePx == IntSize.Zero || anchorRect == null) {
		val centeredX = ((viewportWidthPx - menuSizePx.width) / 2f).coerceAtLeast(horizontalPaddingPx)
		val centeredY = ((viewportHeightPx - menuSizePx.height) / 2f).coerceAtLeast(verticalPaddingPx)
		return IntOffset(centeredX.roundToInt(), centeredY.roundToInt())
	}

	val desiredX = anchorRect.center.x - (menuSizePx.width / 2f)
	val minX = horizontalPaddingPx
	val maxX = (viewportWidthPx - menuSizePx.width - horizontalPaddingPx).coerceAtLeast(minX)
	val resolvedX = desiredX.coerceIn(minX, maxX)

	val minY = verticalPaddingPx
	val maxY = (viewportHeightPx - menuSizePx.height - verticalPaddingPx).coerceAtLeast(minY)
	val preferredTopY = anchorRect.top - menuSizePx.height + overlapWithAnchorPx
	val fallbackBottomY = anchorRect.bottom - overlapWithAnchorPx
	val resolvedY = when {
		preferredTopY >= minY -> preferredTopY
		fallbackBottomY <= maxY -> fallbackBottomY
		else -> minY
	}.coerceIn(minY, maxY)

	return IntOffset(resolvedX.roundToInt(), resolvedY.roundToInt())
}

@Composable
private fun MessageContextMenuItem(
	action: ChatContextMenuAction,
	onClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	val isDelete = action == ChatContextMenuAction.Delete
	val iconTint = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
	val textColor = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
		modifier = modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 14.dp)
	) {
		if (action == ChatContextMenuAction.Reply) {
			Icon(
				painter = painterResource(R.drawable.reply_out_icon),
				contentDescription = null,
				tint = iconTint,
				modifier = Modifier
					.size(24.dp)
					.graphicsLayer(scaleX = -1f)
			)
		} else if (action == ChatContextMenuAction.Retry) {
			Icon(
				painter = painterResource(R.drawable.ic_refresh),
				contentDescription = null,
				tint = iconTint,
				modifier = Modifier.size(24.dp)
			)
		} else {
			Icon(
				imageVector = action.icon,
				contentDescription = null,
				tint = iconTint,
				modifier = Modifier.size(24.dp)
			)
		}
		Text(
			text = stringResource(action.labelResId),
			color = textColor,
			style = MaterialTheme.typography.bodyMedium.copy(
				fontSize = 14.sp,
				fontWeight = FontWeight.Medium
			)
		)
	}
}

private val ChatContextMenuAction.icon: ImageVector
	get() = when (this) {
		ChatContextMenuAction.Reply -> Icons.Outlined.ContentCopy
		ChatContextMenuAction.Pin,
		ChatContextMenuAction.Unpin -> Icons.Outlined.PushPin
		ChatContextMenuAction.CopyText -> Icons.Outlined.ContentCopy
		ChatContextMenuAction.Edit -> Icons.Outlined.Edit
		ChatContextMenuAction.Delete -> Icons.Outlined.DeleteOutline
		ChatContextMenuAction.Retry -> Icons.Outlined.Edit // Fallback icon, actual rendering replaced above
	}

private val ChatContextMenuAction.labelResId: Int
	get() = when (this) {
		ChatContextMenuAction.Reply -> R.string.chat_menu_reply
		ChatContextMenuAction.Pin -> R.string.chat_menu_pin
		ChatContextMenuAction.Unpin -> R.string.chat_menu_unpin
		ChatContextMenuAction.CopyText -> R.string.chat_menu_copy_text
		ChatContextMenuAction.Edit -> R.string.chat_menu_edit
		ChatContextMenuAction.Delete -> R.string.chat_menu_delete
		ChatContextMenuAction.Retry -> R.string.chat_menu_retry
	}

private fun shouldShowDividerAfter(index: Int, actions: List<ChatContextMenuAction>): Boolean {
	if (index >= actions.lastIndex) return false
	val current = actions[index]
	val next = actions[index + 1]
	if (current == ChatContextMenuAction.Reply) return true
	if (next == ChatContextMenuAction.Delete) return true
	return false
}
