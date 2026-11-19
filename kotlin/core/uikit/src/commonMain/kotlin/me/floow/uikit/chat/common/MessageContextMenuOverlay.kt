package me.floow.uikit.chat.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.chat_menu_copy_outline
import flow.core.uikit.generated.resources.chat_menu_delete_outline
import flow.core.uikit.generated.resources.chat_menu_pin_outline
import flow.core.uikit.generated.resources.chat_menu_retry_outline
import flow.core.uikit.generated.resources.chat_menu_select_outline
import flow.core.uikit.generated.resources.chat_menu_unpin_outline
import flow.core.uikit.generated.resources.edit_icon
import flow.core.uikit.generated.resources.reply_out_icon
import kotlinx.coroutines.launch
import me.floow.uikit.chat.model.ChatContextMenuAction
import org.jetbrains.compose.resources.painterResource
import kotlin.math.roundToInt

@Composable
fun MessageContextMenuOverlay(
	actions: List<ChatContextMenuAction>,
	anchorRect: Rect? = null,
	highlightRect: Rect? = null,
	elevatedData: ElevatedMessageData? = null,
	isOutgoing: Boolean? = null,
	onActionClick: (ChatContextMenuAction) -> Unit,
	onDismissRequest: () -> Unit,
	modifier: Modifier = Modifier,
	aboveKeyboard: Boolean = false,
	messageViewport: Rect? = null,
) {
	if (actions.isEmpty()) return
	val labels = actions.associateWith { chatMenuLabel(it) }

	val haptic = LocalHapticFeedback.current
	val scope = rememberCoroutineScope()
	val fallbackProgress = remember { Animatable(0f) }
	val progress = elevatedData?.progress ?: fallbackProgress
	var isClosing by remember(elevatedData?.messageId) { mutableStateOf(false) }
	LaunchedEffect(progress) {
		haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
		progress.animateTo(1f, spring(dampingRatio = 0.86f, stiffness = 480f))
	}

	fun close(action: ChatContextMenuAction? = null) {
		if (isClosing) return
		isClosing = true
		scope.launch {
			progress.animateTo(0f, tween(durationMillis = 140))
			if (action == null) onDismissRequest() else onActionClick(action)
		}
	}

	val scrim = MaterialTheme.colorScheme.scrim
	val scrimOpacity = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.58f else 0.42f
	val hostDensity = LocalDensity.current
	val keyboardHeightPx = WindowInsets.ime.getBottom(hostDensity)
	val sourceBounds = elevatedData?.sourceBounds ?: highlightRect ?: anchorRect
	val foregroundViewport = messageViewport ?: highlightRect
	val settledForeground = if (elevatedData?.foregroundLayer != null && foregroundViewport != null) {
		with(hostDensity) {
			fitMessageMenuForeground(elevatedData.sourceBounds, foregroundViewport, 3.dp.toPx(), 6.dp.toPx())
		}
	} else null
	val foregroundEnabled = settledForeground != null
	DisposableEffect(elevatedData, foregroundEnabled) {
		elevatedData?.foregroundEnabled = foregroundEnabled
		onDispose { elevatedData?.foregroundEnabled = false }
	}
	val dismissBackdrop = Modifier.clickable(
		interactionSource = remember { MutableInteractionSource() },
		indication = null,
		onClick = { close() },
	)

	// Keep the scrim and the message in the same Compose root. The IME window only hosts the card.
	Canvas(Modifier.fillMaxSize().then(if (aboveKeyboard) Modifier else dismissBackdrop)) {
		val p = progress.value.coerceIn(0f, 1f)
		drawRect(scrim.copy(alpha = p * scrimOpacity))

		if (elevatedData?.isInForeground != true || sourceBounds == null) return@Canvas
		val layer = elevatedData.foregroundLayer ?: return@Canvas
		val viewport = foregroundViewport ?: return@Canvas
		val target = settledForeground ?: return@Canvas
		val scale = lerp(1f, target.width / sourceBounds.width, p)
		val travel = (target.center - sourceBounds.center) * p
		clipRect(viewport.left, viewport.top, viewport.right, viewport.bottom) {
			withTransform({
				translate(left = travel.x, top = travel.y)
				scale(scale, scale, pivot = sourceBounds.center)
				translate(left = sourceBounds.left, top = sourceBounds.top)
			}) {
				drawLayer(layer)
			}
		}
	}

	val content: @Composable () -> Unit = {
		val density = LocalDensity.current
		val statusTop = WindowInsets.systemBars.getTop(density)
		val navigationBottom = WindowInsets.systemBars.getBottom(density)
		val imeBottom = if (aboveKeyboard) 0 else WindowInsets.ime.getBottom(density)
		SubcomposeLayout(modifier) { constraints ->
			val width = constraints.maxWidth
			val height = constraints.maxHeight
			val sidePadding = 16.dp.roundToPx().coerceAtMost(width / 2)
			val bottom = (height - maxOf(navigationBottom, imeBottom) - 12.dp.roundToPx()).coerceAtLeast(0)
			val top = (statusTop + 72.dp.roundToPx()).coerceAtMost(bottom)
			val gap = 8.dp.toPx()

			val card = subcompose("card") {
				MessageContextMenuCard(labels = labels, onActionClick = { close(it) })
			}.single().measure(
				Constraints(
					maxWidth = (width - sidePadding * 2).coerceAtLeast(0),
					maxHeight = (bottom - top).coerceAtLeast(0),
				)
			)
			val viewport = messageViewport ?: Rect(0f, top.toFloat(), width.toFloat(), bottom.toFloat())
			val anchor = settledForeground ?: (highlightRect ?: sourceBounds)?.intersect(viewport)?.takeUnless { it.isEmpty }
			val tap = elevatedData?.tapOffset
			val isTall = anchor != null && anchor.height >= (bottom - top) * 0.45f
			val reference = if (isTall && tap != null && viewport.contains(tap)) Rect(tap, Size.Zero) else anchor
			val roomBelow = reference != null && reference.bottom + gap + card.height <= bottom
			val targetX = when {
				anchor == null -> (width - card.width) / 2f
				isOutgoing == true || (isOutgoing == null && anchor.center.x > width / 2f) ->
					anchor.right - card.width
				else -> anchor.left
			}
			val x = targetX.roundToInt().coerceIn(sidePadding, (width - sidePadding - card.width).coerceAtLeast(sidePadding))
			val pivotX = if (card.width > 0 && (tap != null || anchor != null)) {
				(((tap?.x ?: anchor!!.center.x) - x) / card.width).coerceIn(0.15f, 0.85f)
			} else 0.5f

			val windowBackdrop = if (aboveKeyboard) {
				subcompose("windowBackdrop") {
					Canvas(dismissBackdrop) {
						// The chat already has its own scrim; this window only shades the keyboard.
						val keyboardHeight = keyboardHeightPx.toFloat().coerceIn(0f, size.height)
						drawRect(
							color = scrim.copy(alpha = progress.value.coerceIn(0f, 1f) * scrimOpacity),
							topLeft = Offset(0f, size.height - keyboardHeight),
							size = Size(size.width, keyboardHeight),
						)
					}
				}.single().measure(Constraints.fixed(width, height))
			} else null
			layout(width, height) {
				windowBackdrop?.place(0, 0)
				val y = when {
					reference == null -> (top + bottom - card.height) / 2f
					roomBelow -> reference.bottom + gap
					else -> reference.top - gap - card.height
				}
				val menuY = y.roundToInt().coerceIn(top, (bottom - card.height).coerceAtLeast(top))
				card.placeWithLayer(x, menuY) {
					val p = progress.value.coerceIn(0f, 1f)
					alpha = (p * 5f).coerceAtMost(1f)
					scaleX = lerp(0.86f, 1f, p)
					scaleY = lerp(0.72f, 1f, p)
					transformOrigin = TransformOrigin(pivotX, if (roomBelow) 0f else 1f)
				}
			}
		}
	}
	if (aboveKeyboard) ChatModalWindow(onDismissRequest = { close() }, content = content) else content()
}

@Composable
private fun MessageContextMenuCard(
	labels: Map<ChatContextMenuAction, String>,
	onActionClick: (ChatContextMenuAction) -> Unit,
) {
	val colors = MaterialTheme.colorScheme
	val shape = RoundedCornerShape(16.dp)
	Column(
		modifier = Modifier
			.width(240.dp)
			.shadow(24.dp, shape)
			.clip(shape)
			.background(colors.surfaceContainerHigh)
			.border(0.5.dp, colors.outlineVariant, shape)
			.verticalScroll(rememberScrollState()),
	) {
		var previous: ChatContextMenuAction? = null
		labels.forEach { (action, label) ->
			if (previous != null && (previous == ChatContextMenuAction.Reply || action == ChatContextMenuAction.Delete)) {
				HorizontalDivider(thickness = 0.5.dp, color = colors.outlineVariant)
			}
			MessageContextMenuItem(action, label, onClick = { onActionClick(action) })
			previous = action
		}
	}
}

@Composable
private fun MessageContextMenuItem(
	action: ChatContextMenuAction,
	label: String,
	onClick: () -> Unit,
) {
	val colors = MaterialTheme.colorScheme
	val color = if (action == ChatContextMenuAction.Delete) colors.error else colors.onSurface
	Row(
		modifier = Modifier
			.fillMaxWidth()
			.heightIn(min = 48.dp)
			.clickable(onClick = onClick)
			.padding(horizontal = 16.dp, vertical = 10.dp),
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(12.dp),
	) {
		Icon(
			painter = painterResource(actionIcon(action)),
			contentDescription = null,
			tint = color,
			modifier = Modifier.size(24.dp).then(
				if (action == ChatContextMenuAction.Reply) Modifier.graphicsLayer(scaleX = -1f)
				else Modifier
			),
		)
		Text(
			text = label,
			style = MaterialTheme.typography.bodyLarge,
			color = color,
		)
	}
}

private fun actionIcon(action: ChatContextMenuAction) = when (action) {
	ChatContextMenuAction.Reply -> Res.drawable.reply_out_icon
	ChatContextMenuAction.Pin -> Res.drawable.chat_menu_pin_outline
	ChatContextMenuAction.Unpin -> Res.drawable.chat_menu_unpin_outline
	ChatContextMenuAction.CopyText -> Res.drawable.chat_menu_copy_outline
	ChatContextMenuAction.Edit -> Res.drawable.edit_icon
	ChatContextMenuAction.Delete -> Res.drawable.chat_menu_delete_outline
	ChatContextMenuAction.Retry -> Res.drawable.chat_menu_retry_outline
	ChatContextMenuAction.Select -> Res.drawable.chat_menu_select_outline
}

@Composable
internal expect fun chatMenuLabel(action: ChatContextMenuAction): String
