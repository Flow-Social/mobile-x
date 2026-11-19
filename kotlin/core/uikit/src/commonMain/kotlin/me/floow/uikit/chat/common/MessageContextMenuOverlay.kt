package me.floow.uikit.chat.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.chat_menu_copy_text
import flow.core.uikit.generated.resources.chat_menu_delete
import flow.core.uikit.generated.resources.chat_menu_edit
import flow.core.uikit.generated.resources.chat_menu_pin
import flow.core.uikit.generated.resources.chat_menu_reply
import flow.core.uikit.generated.resources.chat_menu_retry
import flow.core.uikit.generated.resources.chat_menu_select
import flow.core.uikit.generated.resources.chat_menu_unpin
import flow.core.uikit.generated.resources.chat_selection_copy_icon
import flow.core.uikit.generated.resources.done_icon
import flow.core.uikit.generated.resources.edit_icon
import flow.core.uikit.generated.resources.ic_delete
import flow.core.uikit.generated.resources.ic_refresh_icon
import flow.core.uikit.generated.resources.reply_out_icon
import kotlinx.coroutines.launch
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
	elevatedData: ElevatedMessageData? = null,
	isCircle: Boolean = false,
	isOutgoing: Boolean? = null,
	onActionClick: (ChatContextMenuAction) -> Unit,
	onDismissRequest: () -> Unit,
	modifier: Modifier = Modifier,
) {
	if (actions.isEmpty()) return

	val haptic = LocalHapticFeedback.current
	val scope = rememberCoroutineScope()
	val progress = remember { Animatable(0f) }
	val bubbleScale = remember { Animatable(elevatedData?.startScale ?: 1f) }

	LaunchedEffect(elevatedData?.messageId) {
		val active = elevatedData ?: return@LaunchedEffect
		haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
		bubbleScale.snapTo(active.startScale)

		launch {
			bubbleScale.animateTo(
				targetValue = 1.03f,
				animationSpec = spring(
					dampingRatio = 0.88f,
					stiffness = 440f,
				),
			)
		}

		progress.snapTo(0f)
		progress.animateTo(
			targetValue = 1f,
			animationSpec = spring(
				dampingRatio = 0.82f,
				stiffness = 550f,
			),
		)
	}

	fun dismissAnimated(onEnd: (() -> Unit)? = null) {
		scope.launch {
			val returnScaleJob = launch {
				bubbleScale.animateTo(
					targetValue = 1.0f,
					animationSpec = spring(
						dampingRatio = 0.9f,
						stiffness = 500f,
					),
				)
			}
			val returnProgressJob = launch {
				progress.animateTo(
					targetValue = 0f,
					animationSpec = tween(durationMillis = 140),
				)
			}
			returnScaleJob.join()
			returnProgressJob.join()
			onEnd?.invoke()
			onDismissRequest()
		}
	}

	val p = progress.value
	val active = elevatedData
	val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
	val targetMaxAlpha = if (isDark) 0.58f else 0.42f
	val scrimAlpha = (p * targetMaxAlpha).coerceIn(0f, 1f)

	var hoveredAction by remember { mutableStateOf<ChatContextMenuAction?>(null) }
	val actionBoundsMap = remember { mutableMapOf<ChatContextMenuAction, Rect>() }

	BoxWithConstraints(
		modifier = modifier
			.fillMaxSize()
			.pointerInput(Unit) {
				awaitEachGesture {
					val down = awaitFirstDown(requireUnconsumed = false)
					var currentDragPos = down.position
					hoveredAction = actionBoundsMap.entries.firstOrNull { it.value.contains(currentDragPos) }?.key

					while (true) {
						val event = awaitPointerEvent()
						val change = event.changes.firstOrNull { it.id == down.id } ?: break
						currentDragPos = change.position
						hoveredAction = actionBoundsMap.entries.firstOrNull { it.value.contains(currentDragPos) }?.key

						if (!change.pressed) {
							// Палец отпущен (Phase 4 release)
							val actionToExecute = hoveredAction
							if (actionToExecute != null) {
								change.consume()
								dismissAnimated {
									onActionClick(actionToExecute)
								}
							} else {
								// Если отпустили за пределами меню — закрываем оверлей
								dismissAnimated()
							}
							break
						}
					}
				}
			},
	) {
		val density = LocalDensity.current
		var menuSizePx by remember { mutableStateOf(IntSize.Zero) }

		val statusBarTopPx = WindowInsets.systemBars.getTop(density).toFloat()
		val headerBarHeightPx = with(density) { 64.dp.toPx() }
		val topSafeInsetPx = statusBarTopPx + headerBarHeightPx + with(density) { 8.dp.toPx() }
		val bottomInsetPx = WindowInsets.systemBars.getBottom(density).toFloat()
		val horizontalPaddingPx = with(density) { 16.dp.toPx() }
		val gapPx = with(density) { 8.dp.toPx() }
		val fingerGapPx = with(density) { 12.dp.toPx() }
		val viewportWidthPx = constraints.maxWidth.toFloat()
		val viewportHeightPx = constraints.maxHeight.toFloat()

		val estimatedMenuSizePx = remember(actions, density) {
			val estimatedWidth = with(density) { 224.dp.toPx().roundToInt() }
			val itemHeight = with(density) { 50.dp.toPx().roundToInt() }
			val dividerHeight = with(density) { 1.dp.toPx().roundToInt() }
			val hasDelete = actions.contains(ChatContextMenuAction.Delete)
			val extraDeleteSpacing = if (hasDelete) with(density) { 6.dp.toPx().roundToInt() } else 0
			IntSize(
				width = estimatedWidth,
				height = (actions.size * itemHeight) + (max(0, actions.size - 1) * dividerHeight) + extraDeleteSpacing,
			)
		}

		val effectiveMenuSize = if (menuSizePx == IntSize.Zero) estimatedMenuSizePx else menuSizePx
		val isVideoNote = elevatedData?.isVideoNote == true || isCircle

		val effectiveRootPosition = when {
			elevatedData != null && elevatedData.rootPosition != Offset.Zero -> elevatedData.rootPosition
			highlightRect != null -> highlightRect.topLeft
			anchorRect != null -> anchorRect.topLeft
			else -> Offset.Zero
		}
		val effectiveItemSize = when {
			elevatedData != null && elevatedData.size != IntSize.Zero -> elevatedData.size
			highlightRect != null -> IntSize(highlightRect.width.roundToInt(), highlightRect.height.roundToInt())
			anchorRect != null -> IntSize(anchorRect.width.roundToInt(), anchorRect.height.roundToInt())
			else -> IntSize.Zero
		}

		// Минимальный вертикальный сдвиг (calculateAutoShiftY по Telegram iOS parity)
		val targetShiftY = if (elevatedData != null || highlightRect != null || anchorRect != null) {
			calculateAutoShiftY(
				isVideoNote = isVideoNote,
				itemTop = effectiveRootPosition.y,
				itemHeight = effectiveItemSize.height,
				screenHeight = viewportHeightPx,
				topSafeInset = topSafeInsetPx,
				bottomSafeInset = bottomInsetPx + with(density) { 12.dp.toPx() },
				reactionsBarHeightPx = 0f,
				menuHeightPx = effectiveMenuSize.height.toFloat(),
				verticalGapPx = gapPx,
			)
		} else {
			0f
		}
		val currentShiftY = lerp(0f, targetShiftY, p)

		// Холст: сплошное затемнение + поднятый GraphicsLayer сообщения (без BlendMode.Clear!)
		Canvas(
			modifier = Modifier.fillMaxSize(),
		) {
			drawRect(color = Color.Black.copy(alpha = scrimAlpha))

			if (elevatedData != null && !isVideoNote) {
				val pivotX = effectiveRootPosition.x + effectiveItemSize.width / 2f
				val pivotY = effectiveRootPosition.y + effectiveItemSize.height / 2f + currentShiftY

				withTransform({
					scale(
						scaleX = bubbleScale.value,
						scaleY = bubbleScale.value,
						pivot = Offset(pivotX, pivotY),
					)
					translate(
						left = effectiveRootPosition.x,
						top = effectiveRootPosition.y + currentShiftY,
					)
				}) {
					drawLayer(elevatedData.layer)
				}
			}
		}

		// Карточка контекстного меню действий
		val effectiveAnchorRect = if (effectiveItemSize != IntSize.Zero) {
			Rect(
				offset = Offset(effectiveRootPosition.x, effectiveRootPosition.y + currentShiftY),
				size = androidx.compose.ui.geometry.Size(effectiveItemSize.width.toFloat(), effectiveItemSize.height.toFloat()),
			)
		} else {
			anchorRect
		}

		val (menuOffset, transformOrigin) = remember(
			effectiveAnchorRect,
			elevatedData?.tapOffset,
			menuSizePx,
			estimatedMenuSizePx,
			viewportWidthPx,
			viewportHeightPx,
			topSafeInsetPx,
			bottomInsetPx,
			gapPx,
			fingerGapPx,
			horizontalPaddingPx,
		) {
			resolveAnchoredMenuOffset(
				anchorRect = effectiveAnchorRect,
				tapOffset = elevatedData?.tapOffset,
				menuSizePx = effectiveMenuSize,
				viewportWidthPx = viewportWidthPx,
				viewportHeightPx = viewportHeightPx,
				topSafeInsetPx = topSafeInsetPx,
				bottomSafeInsetPx = bottomInsetPx,
				gapPx = gapPx,
				fingerGapPx = fingerGapPx,
				horizontalPaddingPx = horizontalPaddingPx,
			)
		}

		val isBelow = transformOrigin.pivotFractionY == 0f
		val rawMenuP = progress.value.coerceIn(0f, 1f)
		val menuScaleX = lerp(0.7f, 1f, rawMenuP)
		val menuScaleY = lerp(0.45f, 1f, rawMenuP)
		val initialTranslateY = if (isBelow) -10f else 10f
		val menuTranslateY = with(density) { lerp(initialTranslateY, 0f, rawMenuP).dp.toPx() }
		val menuAlpha = (rawMenuP / 0.2f).coerceIn(0f, 1f)

		Box(
			modifier = Modifier
				.offset { IntOffset(menuOffset.x, (menuOffset.y + menuTranslateY).roundToInt()) }
				.graphicsLayer {
					alpha = menuAlpha
					scaleX = menuScaleX
					scaleY = menuScaleY
					this.transformOrigin = transformOrigin
				},
		) {
			TelegramStyleContextMenuCard(
				actions = actions,
				isDark = isDark,
				rawMenuP = rawMenuP,
				hoveredAction = hoveredAction,
				onActionHoverBounds = { action, bounds ->
					actionBoundsMap[action] = bounds
				},
				onActionClick = { action ->
					dismissAnimated {
						onActionClick(action)
					}
				},
				onSizeChanged = { menuSizePx = it },
			)
		}
	}
}

fun Modifier.chatBackgroundBlur(
	active: ElevatedMessageData?,
	p: Float,
): Modifier {
	if (active == null || p <= 0.01f) {
		return this
	}
	val blurRadius = (20f * p).dp
	return this.blur(blurRadius)
}

@Composable
fun TelegramStyleContextMenuCard(
	actions: List<ChatContextMenuAction>,
	isDark: Boolean,
	rawMenuP: Float,
	hoveredAction: ChatContextMenuAction? = null,
	onActionHoverBounds: ((ChatContextMenuAction, Rect) -> Unit)? = null,
	onActionClick: (ChatContextMenuAction) -> Unit,
	onSizeChanged: (IntSize) -> Unit,
	modifier: Modifier = Modifier,
) {
	val cardBgColor = if (isDark) Color(0xFF252528).copy(alpha = 0.94f) else Color(0xFFF9F9FB).copy(alpha = 0.94f)
	val borderColor = if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.06f)
	val dividerColor = if (isDark) Color.White.copy(alpha = 0.10f) else Color.Black.copy(alpha = 0.08f)
	val cardShape = RoundedCornerShape(16.dp)

	Column(
		modifier = modifier
			.width(224.dp)
			.shadow(
				elevation = 24.dp,
				shape = cardShape,
			)
			.clip(cardShape)
			.background(
				color = cardBgColor,
				shape = cardShape,
			)
			.border(
				width = 0.5.dp,
				color = borderColor,
				shape = cardShape,
			)
			.clickable(
				interactionSource = remember { MutableInteractionSource() },
				indication = null,
				onClick = {},
			)
			.onSizeChanged(onSizeChanged),
	) {
		val density = LocalDensity.current
		actions.forEachIndexed { index, action ->
			val rowDelay = index * 0.03f
			val rowP = ((rawMenuP - rowDelay) / (1f - rowDelay).coerceAtLeast(0.01f)).coerceIn(0f, 1f)
			val rowAlpha = rowP
			val rowOffsetYPx = with(density) { lerp(8f, 0f, rowP).dp.toPx() }
			val iconScale = lerp(0.7f, 1f, rowP)

			val isDelete = action == ChatContextMenuAction.Delete
			if (isDelete && index > 0) {
				HorizontalDivider(
					color = dividerColor,
					thickness = 0.5.dp,
				)
				Spacer(modifier = Modifier.height(6.dp))
			}

			TelegramStyleMenuItemRow(
				action = action,
				isDark = isDark,
				isHovered = hoveredAction == action,
				rowAlpha = rowAlpha,
				rowOffsetYPx = rowOffsetYPx,
				iconScale = iconScale,
				onBoundsChanged = { bounds ->
					onActionHoverBounds?.invoke(action, bounds)
				},
				onClick = { onActionClick(action) },
			)

			val isNextDelete = (index + 1 < actions.size) && (actions[index + 1] == ChatContextMenuAction.Delete)
			if (!isDelete && !isNextDelete && shouldShowDividerAfter(index, actions)) {
				HorizontalDivider(
					color = dividerColor,
					thickness = 0.5.dp,
				)
			}
		}
	}
}

@Composable
fun TelegramStyleMenuItemRow(
	action: ChatContextMenuAction,
	isDark: Boolean,
	isHovered: Boolean = false,
	rowAlpha: Float,
	rowOffsetYPx: Float,
	iconScale: Float,
	onBoundsChanged: ((Rect) -> Unit)? = null,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val isDelete = action == ChatContextMenuAction.Delete
	val deleteColor = if (isDark) Color(0xFFFF453A) else Color(0xFFFF3B30)
	val contentColor = if (isDelete) deleteColor else if (isDark) Color.White else Color(0xFF1C1C1E)
	val hoverBgColor = if (isHovered) {
		if (isDark) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
	} else {
		Color.Transparent
	}

	Row(
		modifier = modifier
			.fillMaxWidth()
			.graphicsLayer {
				alpha = rowAlpha
				translationY = rowOffsetYPx
			}
			.onGloballyPositioned { coordinates ->
				onBoundsChanged?.invoke(coordinates.boundsInRoot())
			}
			.background(hoverBgColor)
			.clickable(
				interactionSource = remember { MutableInteractionSource() },
				indication = null,
				onClick = onClick,
			)
			.padding(horizontal = 16.dp, vertical = 12.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.SpaceBetween,
	) {
		Text(
			text = actionLabel(action),
			color = contentColor,
			style = MaterialTheme.typography.bodyMedium.copy(
				fontSize = 15.sp,
				fontWeight = FontWeight.Medium,
			),
		)
		Box(
			modifier = Modifier.graphicsLayer {
				scaleX = iconScale
				scaleY = iconScale
			},
			contentAlignment = Alignment.Center,
		) {
			ContextActionIcon(action = action, tint = contentColor)
		}
	}
}

@Composable
fun MessageContextMenuItem(
	action: ChatContextMenuAction,
	onClick: () -> Unit,
	modifier: Modifier = Modifier,
) {
	val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
	TelegramStyleMenuItemRow(
		action = action,
		isDark = isDark,
		isHovered = false,
		rowAlpha = 1f,
		rowOffsetYPx = 0f,
		iconScale = 1f,
		onClick = onClick,
		modifier = modifier,
	)
}

@Composable
private fun ContextActionIcon(
	action: ChatContextMenuAction,
	tint: Color,
	modifier: Modifier = Modifier,
) {
	when (action) {
		ChatContextMenuAction.Reply -> Icon(
			painter = painterResource(Res.drawable.reply_out_icon),
			contentDescription = null,
			tint = tint,
			modifier = modifier.size(22.dp).graphicsLayer(scaleX = -1f),
		)
		ChatContextMenuAction.CopyText -> Icon(
			painter = painterResource(Res.drawable.chat_selection_copy_icon),
			contentDescription = null,
			tint = tint,
			modifier = modifier.size(22.dp),
		)
		ChatContextMenuAction.Edit -> Icon(
			painter = painterResource(Res.drawable.edit_icon),
			contentDescription = null,
			tint = tint,
			modifier = modifier.size(22.dp),
		)
		ChatContextMenuAction.Delete -> Icon(
			painter = painterResource(Res.drawable.ic_delete),
			contentDescription = null,
			tint = tint,
			modifier = modifier.size(22.dp),
		)
		ChatContextMenuAction.Retry -> Icon(
			painter = painterResource(Res.drawable.ic_refresh_icon),
			contentDescription = null,
			tint = tint,
			modifier = modifier.size(22.dp),
		)
		ChatContextMenuAction.Select -> Icon(
			painter = painterResource(Res.drawable.done_icon),
			contentDescription = null,
			tint = tint,
			modifier = modifier.size(22.dp),
		)
		else -> actionIcon(action)?.let { iconVec ->
			Icon(
				imageVector = iconVec,
				contentDescription = null,
				tint = tint,
				modifier = modifier.size(22.dp),
			)
		}
	}
}

private fun actionIcon(action: ChatContextMenuAction) = when (action) {
	ChatContextMenuAction.Pin,
	ChatContextMenuAction.Unpin -> Icons.Default.PushPin
	ChatContextMenuAction.CopyText -> null
	ChatContextMenuAction.Edit -> null
	ChatContextMenuAction.Delete -> null
	ChatContextMenuAction.Reply,
	ChatContextMenuAction.Retry,
	ChatContextMenuAction.Select -> null
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
	ChatContextMenuAction.Select -> stringResource(Res.string.chat_menu_select)
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
	tapOffset: Offset?,
	menuSizePx: IntSize,
	viewportWidthPx: Float,
	viewportHeightPx: Float,
	topSafeInsetPx: Float,
	bottomSafeInsetPx: Float,
	gapPx: Float,
	fingerGapPx: Float,
	horizontalPaddingPx: Float,
): Pair<IntOffset, TransformOrigin> {
	val menuWidth = menuSizePx.width.toFloat()
	val menuHeight = menuSizePx.height.toFloat()

	if (menuSizePx == IntSize.Zero || anchorRect == null) {
		val centeredX = ((viewportWidthPx - menuWidth) / 2f).coerceAtLeast(horizontalPaddingPx)
		val centeredY = ((viewportHeightPx - menuHeight) / 2f).coerceAtLeast(topSafeInsetPx)
		return Pair(IntOffset(centeredX.roundToInt(), centeredY.roundToInt()), TransformOrigin(0.5f, 0.5f))
	}

	// Позиционирование по X от точки тапа (tapOffset)
	val desiredLeft = if (tapOffset != null) {
		if (tapOffset.x > viewportWidthPx / 2f) {
			tapOffset.x - menuWidth + 24f * (viewportWidthPx / 390f).coerceIn(1f, 1.2f)
		} else {
			tapOffset.x - 24f * (viewportWidthPx / 390f).coerceIn(1f, 1.2f)
		}
	} else {
		val isOutgoing = (viewportWidthPx - anchorRect.right) < anchorRect.left || anchorRect.center.x > (viewportWidthPx / 2f)
		if (isOutgoing) {
			anchorRect.right - menuWidth
		} else {
			anchorRect.left
		}
	}

	val minX = horizontalPaddingPx
	val maxX = (viewportWidthPx - menuWidth - horizontalPaddingPx).coerceAtLeast(minX)
	val menuLeft = desiredLeft.coerceIn(minX, maxX)

	val pivotX = if (tapOffset != null && menuWidth > 0f) {
		((tapOffset.x - menuLeft) / menuWidth).coerceIn(0.15f, 0.85f)
	} else {
		val isOutgoing = (viewportWidthPx - anchorRect.right) < anchorRect.left || anchorRect.center.x > (viewportWidthPx / 2f)
		if (isOutgoing) 0.85f else 0.15f
	}

	// Позиционирование по Y:
	val isTallBubble = anchorRect.height >= viewportHeightPx * 0.45f
	val isBelow: Boolean
	val menuTop: Float

	if (isTallBubble && tapOffset != null) {
		// Высокий бабл (>= 45% экрана): спавним меню рядом с tapOffset.y (под пальцем со сдвигом 12.dp, либо над пальцем при нехватке места)
		val menuTopBelowFinger = tapOffset.y + fingerGapPx
		val fitsBelow = (menuTopBelowFinger + menuHeight <= viewportHeightPx - bottomSafeInsetPx)
		if (fitsBelow) {
			menuTop = menuTopBelowFinger
			isBelow = true
		} else {
			menuTop = (tapOffset.y - fingerGapPx - menuHeight).coerceAtLeast(topSafeInsetPx)
			isBelow = false
		}
	} else {
		// Обычный бабл (< 45% экрана): строгий зазор 8.dp без перекрытия
		val menuTopBelow = anchorRect.bottom + gapPx
		val fitsBelow = (menuTopBelow + menuHeight <= viewportHeightPx - bottomSafeInsetPx)
		if (fitsBelow) {
			menuTop = menuTopBelow
			isBelow = true
		} else {
			menuTop = (anchorRect.top - gapPx - menuHeight).coerceAtLeast(topSafeInsetPx)
			isBelow = false
		}
	}

	val pivotY = if (isBelow) 0f else 1f

	return Pair(
		IntOffset(menuLeft.roundToInt(), menuTop.roundToInt()),
		TransformOrigin(pivotX, pivotY)
	)
}
