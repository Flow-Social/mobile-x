package me.floow.uikit.chat.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import me.floow.uikit.chat.model.ChatDraftImage
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyListState

import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import me.floow.uikit.util.overlayHorizontalSwipeZone

private const val ChatDraftCardRotation = -4.5f
private val ChatDraftCardSize = 78.dp
private val ChatDraftCardCornerRadius = 16.dp
private val ChatDraftCardInnerCornerRadius = 14.dp
private val ChatDraftCardImagePadding = 3.dp
private val ChatDraftCardBorderWidth = 3.2.dp
private val ChatDraftCardDashLength = 9.dp
private val ChatDraftCardDashGap = 6.dp
private const val ChatDraftDraggingCardScale = 1.1f
private val ChatDraftDraggingCardShadow = 16.dp
private val ChatDraftRemoveButtonSize = 24.dp
private val ChatDraftRemoveButtonPadding = 4.dp
private val ChatDraftRemoveIconSize = 14.dp

@Composable
fun ChatDraftImageStrip(
	images: List<ChatDraftImage>,
	onAddClick: () -> Unit,
	onRemoveClick: (String) -> Unit,
	onOrderCommit: (List<String>) -> Unit,
	modifier: Modifier = Modifier,
) {
	if (images.isEmpty()) return

	var draftImages by remember(images) { mutableStateOf(images) }
	val listState = rememberLazyListState()
	val isMediaListAtStart by remember(listState) {
		derivedStateOf {
			listState.firstVisibleItemIndex == 0 && listState.firstVisibleItemScrollOffset == 0
		}
	}
	val haptics = LocalHapticFeedback.current
	val reorderState = rememberReorderableLazyListState(listState) { from, to ->
		val fromIndex = (from.key as? String)?.let { key -> draftImages.indexOfFirst { it.id == key } }
			?.takeIf { it >= 0 } ?: from.index
		val toIndex = (to.key as? String)?.let { key -> draftImages.indexOfFirst { it.id == key } }
			?.takeIf { it >= 0 } ?: to.index
		if (fromIndex in draftImages.indices && toIndex in draftImages.indices && fromIndex != toIndex) {
			val updated = draftImages.toMutableList()
			val moved = updated.removeAt(fromIndex)
			updated.add(toIndex, moved)
			draftImages = updated
		}
	}
	val isDragging by remember(reorderState) { derivedStateOf { reorderState.isAnyItemDragging } }
	val currentIds = images.map(ChatDraftImage::id)
	val draftIds = draftImages.map(ChatDraftImage::id)

	var prevImagesCount by remember { mutableStateOf(images.size) }
	LaunchedEffect(images.size) {
		if (images.size > prevImagesCount) {
			listState.animateScrollToItem(draftImages.size)
		}
		prevImagesCount = images.size
	}

	LaunchedEffect(images, isDragging) {
		if (!isDragging && (currentIds.toSet() != draftIds.toSet() || currentIds.size != draftIds.size)) {
			draftImages = images
		}
	}

	val isDark = isSystemInDarkTheme()
	val solidBorderColor = if (isDark) {
		Color.White.copy(alpha = 0.95f)
	} else {
		Color(0xFF5E647D)
	}
	val dashedBorderColor = if (isDark) {
		Color(0xFFA7AACC).copy(alpha = 0.95f)
	} else {
		Color(0xFF646A84)
	}
	val ambientShadow = if (isDark) Color.Black.copy(alpha = 0.55f) else Color(0x35000000)
	val spotShadow = if (isDark) Color.Black.copy(alpha = 0.75f) else Color(0x30000000)

	AnimatedVisibility(
		visible = images.isNotEmpty(),
		enter = fadeIn(tween(220)) + slideInVertically(
			initialOffsetY = { it / 3 },
			animationSpec = tween(220, easing = FastOutSlowInEasing),
		),
		exit = fadeOut(tween(120)),
		modifier = modifier.graphicsLayer { clip = false },
	) {
		LazyRow(
			state = listState,
			contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
			horizontalArrangement = Arrangement.spacedBy(10.dp),
			modifier = Modifier
				.fillMaxWidth()
				.graphicsLayer { clip = false }
				.overlayHorizontalSwipeZone(
					zoneKey = "chat_draft_media_row_swipe_zone",
					atStart = isMediaListAtStart,
					blockOverlay = isDragging,
				),
		) {
			itemsIndexed(draftImages, key = { _, image -> image.id }) { _, image ->
				ReorderableItem(state = reorderState, key = image.id) { isDraggingItem ->
					val dragHandleModifier = with(this) {
						Modifier.longPressDraggableHandle(
							onDragStarted = { haptics.performHapticFeedback(HapticFeedbackType.GestureThresholdActivate) },
							onDragStopped = {
								onOrderCommit(draftImages.map(ChatDraftImage::id))
								haptics.performHapticFeedback(HapticFeedbackType.GestureEnd)
							},
						)
					}
					Box(
						modifier = Modifier
							.size(ChatDraftCardSize)
							.graphicsLayer {
								rotationZ = ChatDraftCardRotation
								scaleX = if (isDraggingItem) ChatDraftDraggingCardScale else 1f
								scaleY = if (isDraggingItem) ChatDraftDraggingCardScale else 1f
								shadowElevation = if (isDraggingItem) ChatDraftDraggingCardShadow.toPx() else 4.dp.toPx()
								shape = RoundedCornerShape(ChatDraftCardCornerRadius)
								clip = false
								ambientShadowColor = ambientShadow
								spotShadowColor = spotShadow
							}
							.background(
								color = solidBorderColor,
								shape = RoundedCornerShape(ChatDraftCardCornerRadius),
							),
					) {
						Box(
							modifier = Modifier
								.then(dragHandleModifier)
								.padding(ChatDraftCardBorderWidth)
								.fillMaxSize()
								.clip(RoundedCornerShape(ChatDraftCardInnerCornerRadius))
								.background(MaterialTheme.colorScheme.surfaceVariant),
						) {
							AsyncImage(
								model = image.uri,
								contentDescription = "Выбранное изображение",
								contentScale = ContentScale.Crop,
								modifier = Modifier.fillMaxSize(),
							)
						}
						Box(
							modifier = Modifier
								.align(Alignment.TopEnd)
								.padding(ChatDraftRemoveButtonPadding)
								.size(ChatDraftRemoveButtonSize)
								.clip(CircleShape)
								.background(Color.Black.copy(alpha = 0.65f))
								.clickable { onRemoveClick(image.id) },
							contentAlignment = Alignment.Center,
						) {
							Icon(
								Icons.Default.Close,
								contentDescription = "Удалить изображение",
								tint = Color.White,
								modifier = Modifier.size(ChatDraftRemoveIconSize),
							)
						}
					}
				}
			}
			item(key = "add-image") {
				Box(
					modifier = Modifier
						.size(ChatDraftCardSize)
						.graphicsLayer {
							rotationZ = ChatDraftCardRotation
							shadowElevation = 3.dp.toPx()
							shape = RoundedCornerShape(ChatDraftCardCornerRadius)
							clip = false
							ambientShadowColor = ambientShadow
							spotShadowColor = spotShadow
						}
						.background(
							color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.6f else 0.85f),
							shape = RoundedCornerShape(ChatDraftCardCornerRadius),
						)
						.dashedRoundedBorder(
							color = dashedBorderColor,
							cornerRadius = ChatDraftCardCornerRadius,
							strokeWidth = ChatDraftCardBorderWidth,
							dashLength = ChatDraftCardDashLength,
							gapLength = ChatDraftCardDashGap,
						)
						.clickable(onClick = onAddClick),
					contentAlignment = Alignment.Center,
				) {
					Icon(
						Icons.Default.Add,
						contentDescription = "Добавить изображение",
						tint = MaterialTheme.colorScheme.onSurfaceVariant,
						modifier = Modifier.size(28.dp),
					)
				}
			}
		}
	}
}

private fun Modifier.dashedRoundedBorder(
	color: Color,
	cornerRadius: Dp,
	strokeWidth: Dp = 1.dp,
	dashLength: Dp = 8.dp,
	gapLength: Dp = 7.dp,
): Modifier = drawWithContent {
	drawContent()
	val half = strokeWidth.toPx() / 2f
	drawRoundRect(
		color = color,
		topLeft = androidx.compose.ui.geometry.Offset(half, half),
		size = androidx.compose.ui.geometry.Size(size.width - strokeWidth.toPx(), size.height - strokeWidth.toPx()),
		style = Stroke(
			width = strokeWidth.toPx(),
			pathEffect = PathEffect.dashPathEffect(
				intervals = floatArrayOf(dashLength.toPx(), gapLength.toPx()),
			),
		),
		cornerRadius = androidx.compose.ui.geometry.CornerRadius(cornerRadius.toPx(), cornerRadius.toPx()),
	)
}
