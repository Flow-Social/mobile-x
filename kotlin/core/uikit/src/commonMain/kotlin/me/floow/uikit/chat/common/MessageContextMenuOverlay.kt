package me.floow.uikit.chat.common

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.updateTransition
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
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.chat_menu_copy_text
import flow.core.uikit.generated.resources.chat_menu_delete
import flow.core.uikit.generated.resources.chat_menu_edit
import flow.core.uikit.generated.resources.chat_menu_pin
import flow.core.uikit.generated.resources.chat_menu_reply
import flow.core.uikit.generated.resources.chat_menu_retry
import flow.core.uikit.generated.resources.chat_menu_unpin
import flow.core.uikit.generated.resources.chat_selection_copy_icon
import flow.core.uikit.generated.resources.edit_icon
import flow.core.uikit.generated.resources.ic_delete
import flow.core.uikit.generated.resources.ic_refresh_icon
import flow.core.uikit.generated.resources.reply_out_icon
import me.floow.uikit.chat.model.ChatContextMenuAction
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import kotlin.math.max
import kotlin.math.roundToInt

@Composable
fun MessageContextMenuOverlay(
	actions: List<ChatContextMenuAction>,
	anchorRect: Rect? = null,
	highlightRect: Rect? = null,
	onActionClick: (ChatContextMenuAction) -> Unit,
	onDismissRequest: () -> Unit,
	modifier: Modifier = Modifier,
) {
	if (actions.isEmpty()) return

	val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
	LaunchedEffect(anchorRect, actions) {
		haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
	}

	val visibilityState = remember {
		MutableTransitionState(false).apply { targetState = true }
	}

	val transition = updateTransition(visibilityState, label = "ContextMenuOverlay")
	val overlayAlpha by transition.animateFloat(
		transitionSpec = {
			if (targetState) tween(160) else tween(120)
		},
		label = "overlayAlpha",
	) { isVisible ->
		if (isVisible) 1f else 0f
	}

	BoxWithConstraints(
		modifier = modifier.fillMaxSize(),
	) {
		var menuSizePx by remember { mutableStateOf(IntSize.Zero) }
		val density = LocalDensity.current
		val horizontalPaddingPx = with(density) { 16.dp.toPx() }
		val verticalPaddingPx = with(density) { 16.dp.toPx() }
		val overlapWithAnchorPx = with(density) { 14.dp.toPx() }
		val viewportWidthPx = with(density) { maxWidth.toPx() }
		val viewportHeightPx = with(density) { maxHeight.toPx() }
		val resolvedHighlightRect = highlightRect
		val resolvedCornerRadius = remember(resolvedHighlightRect, density) {
			if (resolvedHighlightRect == null) {
				CornerRadius.Zero
			} else {
				val w = resolvedHighlightRect.width
				val h = resolvedHighlightRect.height
				if (kotlin.math.abs(w - h) < 6f) {
					val minDim = minOf(w, h)
					CornerRadius(minDim / 2f, minDim / 2f)
				} else {
					val r = minOf(h / 2f, with(density) { 20.dp.toPx() })
					CornerRadius(r, r)
				}
			}
		}
		val scrimColor = MaterialTheme.colorScheme.scrim.copy(alpha = 0.25f)
		val estimatedMenuSizePx = remember(actions, density) {
			val estimatedWidth = with(density) { 224.dp.toPx().roundToInt() }
			val itemHeight = with(density) { 52.dp.toPx().roundToInt() }
			val dividerHeight = with(density) { 1.dp.toPx().roundToInt() }
			IntSize(
				width = estimatedWidth,
				height = (actions.size * itemHeight) + (max(0, actions.size - 1) * dividerHeight),
			)
		}
		val menuOffset = remember(anchorRect, menuSizePx, estimatedMenuSizePx, viewportWidthPx, viewportHeightPx, overlapWithAnchorPx) {
			resolveAnchoredMenuOffset(
				anchorRect = anchorRect,
				menuSizePx = if (menuSizePx == IntSize.Zero) estimatedMenuSizePx else menuSizePx,
				viewportWidthPx = viewportWidthPx,
				viewportHeightPx = viewportHeightPx,
				horizontalPaddingPx = horizontalPaddingPx,
				verticalPaddingPx = verticalPaddingPx,
				overlapWithAnchorPx = overlapWithAnchorPx,
			)
		}
		Box(
			modifier = Modifier
				.fillMaxSize()
				.graphicsLayer {
					alpha = overlayAlpha
					compositingStrategy = CompositingStrategy.Offscreen
				}
				.drawWithContent {
					drawRect(scrimColor)
					resolvedHighlightRect?.let { rect ->
						drawRoundRect(
							color = Color.Transparent,
							topLeft = rect.topLeft,
							size = rect.size,
							cornerRadius = resolvedCornerRadius,
							blendMode = BlendMode.Clear,
						)
					}
				}
				.clickable(
					interactionSource = remember { MutableInteractionSource() },
					indication = null,
					onClick = { visibilityState.targetState = false },
				),
		)

		AnimatedVisibility(
			visibleState = visibilityState,
			enter = fadeIn(animationSpec = tween(160)) + scaleIn(animationSpec = tween(160), initialScale = 0.88f),
			exit = fadeOut(animationSpec = tween(120)) + scaleOut(animationSpec = tween(120), targetScale = 0.92f),
			modifier = Modifier.offset { menuOffset },
		) {
			Column(
				modifier = Modifier
					.width(224.dp)
					.shadow(
						elevation = 16.dp,
						shape = RoundedCornerShape(16.dp),
					)
					.background(
						color = MaterialTheme.colorScheme.surfaceContainerHighest,
						shape = RoundedCornerShape(16.dp),
					)
					.clickable(
						interactionSource = remember { MutableInteractionSource() },
						indication = null,
						onClick = {},
					)
					.onSizeChanged { menuSizePx = it },
			) {
				actions.forEachIndexed { index, action ->
					MessageContextMenuItem(
						action = action,
						onClick = {
							visibilityState.targetState = false
							onActionClick(action)
						},
					)
					if (shouldShowDividerAfter(index, actions)) {
						HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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

@Composable
private fun MessageContextMenuItem(
	action: ChatContextMenuAction,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val isDelete = action == ChatContextMenuAction.Delete
	val iconTint = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
	val textColor = if (isDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
	Row(
		modifier = modifier
			.fillMaxWidth()
			.clickable(onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 14.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(10.dp),
	) {
		when (action) {
			ChatContextMenuAction.Reply -> Icon(
				painter = painterResource(Res.drawable.reply_out_icon),
				contentDescription = null,
				tint = iconTint,
				modifier = Modifier.size(24.dp).graphicsLayer(scaleX = -1f),
			)
			ChatContextMenuAction.CopyText -> Icon(
				painter = painterResource(Res.drawable.chat_selection_copy_icon),
				contentDescription = null,
				tint = iconTint,
				modifier = Modifier.size(24.dp),
			)
			ChatContextMenuAction.Edit -> Icon(
				painter = painterResource(Res.drawable.edit_icon),
				contentDescription = null,
				tint = iconTint,
				modifier = Modifier.size(24.dp),
			)
			ChatContextMenuAction.Delete -> Icon(
				painter = painterResource(Res.drawable.ic_delete),
				contentDescription = null,
				tint = iconTint,
				modifier = Modifier.size(24.dp),
			)
			ChatContextMenuAction.Retry -> Icon(
				painter = painterResource(Res.drawable.ic_refresh_icon),
				contentDescription = null,
				tint = iconTint,
				modifier = Modifier.size(24.dp),
			)
			else -> Icon(
				imageVector = actionIcon(action)!!,
				contentDescription = null,
				tint = iconTint,
				modifier = Modifier.size(24.dp),
			)
		}
		Text(
			text = actionLabel(action),
			color = textColor,
			style = MaterialTheme.typography.bodyMedium.copy(
				fontSize = 14.sp,
				fontWeight = FontWeight.Medium,
			),
		)
	}
}

private fun actionIcon(action: ChatContextMenuAction) = when (action) {
	ChatContextMenuAction.Pin,
	ChatContextMenuAction.Unpin -> Icons.Default.PushPin
	ChatContextMenuAction.CopyText -> null
	ChatContextMenuAction.Edit -> null
	ChatContextMenuAction.Delete -> null
	ChatContextMenuAction.Reply,
	ChatContextMenuAction.Retry -> null
}

@Composable
private fun actionLabel(action: ChatContextMenuAction) = when (action) {
	ChatContextMenuAction.Reply -> stringResource(Res.string.chat_menu_reply)
	ChatContextMenuAction.Pin -> stringResource(Res.string.chat_menu_pin)
	ChatContextMenuAction.Unpin -> stringResource(Res.string.chat_menu_unpin)
	ChatContextMenuAction.CopyText -> stringResource(Res.string.chat_menu_copy_text)
	ChatContextMenuAction.Edit -> stringResource(Res.string.chat_menu_edit)
	ChatContextMenuAction.Delete -> stringResource(Res.string.chat_menu_delete)
	ChatContextMenuAction.Retry -> stringResource(Res.string.chat_menu_retry)
}

private fun shouldShowDividerAfter(index: Int, actions: List<ChatContextMenuAction>): Boolean {
	if (index >= actions.lastIndex) return false
	val current = actions[index]
	val next = actions[index + 1]
	if (current == ChatContextMenuAction.Reply) return true
	if (next == ChatContextMenuAction.Delete) return true
	return false
}

private fun resolveAnchoredMenuOffset(
	anchorRect: Rect?,
	menuSizePx: IntSize,
	viewportWidthPx: Float,
	viewportHeightPx: Float,
	horizontalPaddingPx: Float,
	verticalPaddingPx: Float,
	overlapWithAnchorPx: Float,
): IntOffset {
	if (menuSizePx == IntSize.Zero || anchorRect == null) {
		val centeredX = ((viewportWidthPx - menuSizePx.width) / 2f).coerceAtLeast(horizontalPaddingPx)
		val centeredY = ((viewportHeightPx - menuSizePx.height) / 2f).coerceAtLeast(verticalPaddingPx)
		return IntOffset(centeredX.roundToInt(), centeredY.roundToInt())
	}

	val isOutgoing = (viewportWidthPx - anchorRect.right) < anchorRect.left || anchorRect.center.x > (viewportWidthPx / 2f)
	val desiredX = if (isOutgoing) {
		anchorRect.right - menuSizePx.width
	} else {
		anchorRect.left
	}
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
		else -> ((viewportHeightPx - menuSizePx.height) / 2f).coerceIn(minY, maxY)
	}.coerceIn(minY, maxY)

	return IntOffset(resolvedX.roundToInt(), resolvedY.roundToInt())
}
