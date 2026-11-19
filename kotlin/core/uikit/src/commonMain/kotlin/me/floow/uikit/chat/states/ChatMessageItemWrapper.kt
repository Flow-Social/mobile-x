package me.floow.uikit.chat.states

import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.withTimeoutOrNull
import androidx.compose.ui.graphics.graphicsLayer
import me.floow.uikit.chat.model.currentChatTimeMillis
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.chat.common.ElevatedMessageData
import me.floow.uikit.chat.components.ChatAlbumBubble
import me.floow.uikit.chat.components.ReplyPill
import me.floow.uikit.chat.components.ChatMessageTimestamp
import me.floow.uikit.chat.components.ChatTailVector
import me.floow.uikit.chat.components.chatMessageTimestampPadding
import me.floow.uikit.chat.components.replyable.ReplyableChatContent
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatImageItem
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatScreenConfig
import me.floow.uikit.chat.model.ImageMessage
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.chat.model.PrimaryInMessage
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyInMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.chat.model.VideoCircleOutMessage
import me.floow.uikit.chat.model.VoiceMessage
import me.floow.uikit.chat.model.formatChatClockTime

val LocalContextMenuElevation = compositionLocalOf<ElevatedMessageData?> { null }

/**
 * Message item row wrapper handling swipe-to-reply, multi-selection mode (checkboxes / borders),
 * context menu coordinate tracking, bubble / media content dispatch, and spacing / dividers.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LazyItemScope.ChatMessageItemWrapper(
	message: ChatMessage,
    entryTimeMillis: Long?,
    entryOrigin: Offset?,
    playedEntrances: MutableMap<String, Long>,
	layoutState: ChatMessageLayoutState?,
	isReverseLayout: Boolean,
	unreadBoundaryMessageId: Long?,
	isRowHighlighted: Boolean,
	isSelected: Boolean,
	isSelectionMode: Boolean,
	selectedMessageIds: Set<Long>,
	showUnreadIndicators: Boolean,
	peerLastReadMessageId: Long?,
	config: ChatScreenConfig,
	isScrollBusy: Boolean,
	shouldMeasureBounds: Boolean,
	rowBoundsByMessageKey: MutableMap<String, Rect>,
	bubbleBoundsByMessageKey: MutableMap<String, Rect>,
	rowBoundsRegistry: RowBoundsRegistry,
	hiddenPostImageIndex: Int?,
	hiddenPostImageRevealProgress: Float,
	onChatBubbleClick: (ChatMessage) -> Unit,
	onChatBubbleLongClick: (ChatMessage) -> Unit,
	onToggleSelection: (Long) -> Unit,
	onMessageActionClick: ((ChatMessage) -> Unit)?,
	onAvatarClick: (ChatMessage) -> Unit,
	onReply: (ChatMessage) -> Unit,
	onReplyClick: (ChatMessage) -> Unit,
	onRetrySendClick: ((ChatMessage) -> Unit)?,
	onImageRetry: ((String) -> Unit)?,
	onImageCancel: ((String) -> Unit)?,
	onPostImageClick: (PostPreviewMessage, Int, Rect?, Painter?) -> Unit,
	onPostImageBoundsChanged: ((PostPreviewMessage, Int, Rect, Painter?) -> Unit)?,
	resolveContextMenuActions: (ChatMessage) -> List<ChatContextMenuAction>,
	onContextMenuOpenRequest: (ChatMessage, Rect?, Rect?, ElevatedMessageData?) -> Unit,
	onContextMenuDismissRequest: () -> Unit,
	videoCircleContent: @Composable (VideoCircleOutMessage, Boolean, Dp, () -> Unit) -> Unit,
	voiceContent: @Composable (VoiceMessage, Boolean, () -> Unit) -> Unit,
	imageContent: @Composable (
		message: ImageMessage,
		isSelectionMode: Boolean,
		selectedImageIds: Set<Long>,
		onToggleImageSelection: (Long) -> Unit,
		onClick: () -> Unit,
		onLongClick: () -> Unit,
		onRetry: (String) -> Unit,
		onCancel: (String) -> Unit,
	) -> Unit,
	modifier: Modifier = Modifier,
) {
	val interactionPolicy = config.interactionPolicy
	val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f

	val activeElevation = LocalContextMenuElevation.current?.takeIf { it.messageId == message.id }
	var bubbleRect by remember { mutableStateOf<Rect?>(null) }
	var visibleBubbleRect by remember { mutableStateOf<Rect?>(null) }

	val isFirstInGroup = layoutState?.isFirstInCluster ?: true
	val isLastInGroup = layoutState?.isLastInCluster ?: true
	val showTail = layoutState?.showTail ?: isLastInGroup
	val showTimestamp = layoutState?.showTimestamp ?: isLastInGroup
	val showAuthorHeader = layoutState?.showAuthorHeader ?: false
	val showAvatar = layoutState?.showAvatar ?: false

    val isUnreadBoundary = unreadBoundaryMessageId != null && (unreadBoundaryMessageId == message.id ||
        (message as? ImageMessage)?.images?.any { it.id == unreadBoundaryMessageId } == true)
    val shouldShowUnreadBoundaryBeforeMessage = !isReverseLayout && isUnreadBoundary
    val shouldShowUnreadBoundaryAfterMessage = isReverseLayout && isUnreadBoundary

	val isPostPreview = message is PostPreviewMessage
	val isVideoCircle = message is VideoCircleOutMessage ||
		(message as? VideoCircleOutMessage) != null ||
		message::class.simpleName?.contains("VideoCircle", ignoreCase = true) == true
	val elevationLayer = rememberGraphicsLayer()
	val isVoice = message is VoiceMessage
	val isImage = message is ImageMessage
	val isPhoto = isImage || (message is PostPreviewMessage && message.imageVariants.isNotEmpty())
	val isOut = message is PrimaryOutMessage || message is ReplyOutMessage ||
		(message is ImageMessage && message.isOutgoing) ||
		((message as? VideoCircleOutMessage)?.isOutgoing == true) ||
		(message is VoiceMessage && message.isOutgoing)

    val isTextEntry = !isImage && !isVideoCircle && !isVoice && !isPostPreview
    val entrance = remember(message.uiKey, entryTimeMillis) {
        if (isTextEntry && entryTimeMillis != null &&
            currentChatTimeMillis() - entryTimeMillis in 0..650L &&
            playedEntrances[message.uiKey] != entryTimeMillis
        ) Animatable(0f) else null
    }
    var entryDelta by remember(entrance) { mutableStateOf<Offset?>(null) }
    LaunchedEffect(entrance) {
        if (entrance != null && entryTimeMillis != null) {
            if (entryOrigin != null && isOut) {
                val destination = withTimeoutOrNull(120L) { snapshotFlow { bubbleRect }.filterNotNull().first() }
                if (destination != null) {
                    entryDelta = Offset(
                        entryOrigin.x - destination.left,
                        entryOrigin.y - destination.center.y,
                    )
                }
            }
            playedEntrances.entries.removeAll { currentChatTimeMillis() - it.value > 1_000L }
            playedEntrances[message.uiKey] = entryTimeMillis
            entrance.animateTo(1f, spring(dampingRatio = if (isOut) 0.78f else 0.9f, stiffness = 550f))
        }
    }

	val showUnreadDot = isOut &&
		showUnreadIndicators &&
		message.id > 0L &&
		(peerLastReadMessageId == null || message.id > peerLastReadMessageId)
	val showIncomingAvatar = !isOut && interactionPolicy.showAuthorHeaderForInMessages
	val showMessageAction = !isSelectionMode && !isOut && !isPostPreview && onMessageActionClick != null

	val contextMenuAnchorModifier = if (shouldMeasureBounds) {
		Modifier.onGloballyPositioned { coordinates ->
			if (!isScrollBusy || isVideoCircle) {
				val bounds = coordinates.boundsInRoot()
				rowBoundsByMessageKey[message.uiKey] = bounds
				message.clientMessageId?.takeIf(String::isNotBlank)?.let { cmid ->
					rowBoundsByMessageKey["cmid_$cmid"] = bounds
				}
			}
		}
	} else {
		Modifier
	}

	val haptic = LocalHapticFeedback.current
	val scope = rememberCoroutineScope()
	val pressScaleAnimatable = remember { Animatable(1f) }

	val contextMenuHighlightModifier = Modifier
		.onGloballyPositioned { coordinates ->
			val size = coordinates.size
			bubbleRect = Rect(
				coordinates.positionInRoot(),
				androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()),
			)
			val bounds = coordinates.boundsInRoot()
			visibleBubbleRect = bounds
			if (!isScrollBusy || isVideoCircle) {
				bubbleBoundsByMessageKey[message.uiKey] = bounds
				bubbleBoundsByMessageKey["msg_${message.id}"] = bounds
				bubbleBoundsByMessageKey[message.id.toString()] = bounds
				message.clientMessageId?.takeIf(String::isNotBlank)?.let { cmid ->
					bubbleBoundsByMessageKey["cmid_$cmid"] = bounds
				}
			}
		}
        .then(if (entrance != null && entryOrigin != null) Modifier.graphicsLayer {
            val delta = entryDelta ?: return@graphicsLayer
            val remaining = 1f - entrance.value
            translationX = (delta.x * 0.45f).coerceIn(-100.dp.toPx(), 100.dp.toPx()) * remaining
            translationY = delta.y.coerceIn(0f, 72.dp.toPx()) * remaining
        } else Modifier)
		.drawWithContent {
			if (activeElevation != null && activeElevation.foregroundLayer === elevationLayer) {
				// Capture only the active bubble, in the same Compose root as the backdrop.
				elevationLayer.record { this@drawWithContent.drawContent() }
				if (!activeElevation.isInForeground) drawLayer(elevationLayer)
				return@drawWithContent
			}
			val scale = if (isPhoto || isVideoCircle) 1f else pressScaleAnimatable.value
			if (scale == 1f) {
				drawContent()
				return@drawWithContent
			}
			withTransform({
				scale(scale, scale, pivot = center)
			}) {
				this@drawWithContent.drawContent()
			}
		}

	var rowRootPosition by remember { mutableStateOf(Offset.Zero) }

	val openMessageMenu by rememberUpdatedState<(Offset?) -> Boolean> { tapInRoot ->
		if (resolveContextMenuActions(message).isEmpty()) {
			false
		} else {
			val highlight = visibleBubbleRect ?: rowBoundsByMessageKey[message.uiKey]
			val elevated = bubbleRect?.let {
				ElevatedMessageData(
					messageId = message.id,
					sourceBounds = it,
					progress = Animatable(0f),
					tapOffset = tapInRoot,
					foregroundLayer = elevationLayer,
				)
			}
			onContextMenuOpenRequest(message, highlight, highlight, elevated)
			true
		}
	}
	val selectMessage by rememberUpdatedState { onChatBubbleLongClick(message) }
	val toggleSelection by rememberUpdatedState { onToggleSelection(message.id) }
	val tapMessage by rememberUpdatedState { onChatBubbleClick(message) }
	val selectionActive by rememberUpdatedState(isSelectionMode)

	val rowGestureModifier = Modifier
		.onGloballyPositioned { rowRootPosition = it.positionInRoot() }
		.pointerInput(message.uiKey) {
			awaitEachGesture {
				val down = awaitFirstDown(requireUnconsumed = false)
				val startedInSelection = selectionActive
				val pressJob = if (startedInSelection) null else scope.launch {
					delay(120L)
					pressScaleAnimatable.animateTo(0.97f, tween(durationMillis = 140))
				}
				var tapPosition: Offset? = null
				val longPress = try {
					withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
						while (true) {
							val event = awaitPointerEvent()
							val change = event.changes.firstOrNull { it.id == down.id } ?: break
							val moved = (change.position - down.position).getDistance() > viewConfiguration.touchSlop
							if (change.isConsumed || moved || event.changes.count { it.pressed } > 1) break
							if (!change.pressed) {
								change.consume()
								tapPosition = change.position
								break
							}
							awaitPointerEvent(PointerEventPass.Final)
							if (change.isConsumed) break
						}
						true
					} == null
				} finally {
					pressJob?.cancel()
					tapPosition?.let {
						if (startedInSelection) toggleSelection()
						else if (!openMessageMenu(rowRootPosition + it)) tapMessage()
					}
					if (pressJob != null) {
						scope.launch {
							pressScaleAnimatable.animateTo(1f, spring(dampingRatio = 0.9f, stiffness = 600f))
						}
					}
				}
				if (longPress) {
					if (!startedInSelection) {
						if (!isImage && !isPostPreview) haptic.performHapticFeedback(HapticFeedbackType.LongPress)
						selectMessage()
					}
					while (true) {
						val event = awaitPointerEvent(PointerEventPass.Initial)
						event.changes.forEach { it.consume() }
						if (event.changes.none { it.pressed }) break
					}
				}
			}
		}

	val incomingBubbleModifier = if (showMessageAction) {
		Modifier
			.widthIn(max = 220.dp)
			.wrapContentWidth()
	} else {
		Modifier.wrapContentWidth()
	}

	Column(
		modifier = modifier
			.fillMaxWidth(),
	) {
		if (shouldShowUnreadBoundaryBeforeMessage) {
			UnreadBoundaryRow(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 6.dp)
			)
		}


        val mediaQuote = message.quote
        if ((isImage || isVoice || isVideoCircle || isPostPreview) && mediaQuote != null) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start,
            ) {
                ReplyPill(
                    replyText = mediaQuote.preview.ifBlank { "Сообщение недоступно" },
                    isDark = isDark,
                    isOut = isOut,
                    onClick = { onReplyClick(message) },
                    modifier = Modifier.widthIn(max = 263.dp),
                    enabled = mediaQuote.isAvailable && !isSelectionMode,
                )
            }
        }

        HighlightedMessageRow(
			highlighted = isRowHighlighted,
			selected = isSelected
		) {
			SelectableMessageRow(
				showSelector = isSelectionMode,
				selected = isSelected,
				indicatorAlignment = if (isOut) Alignment.CenterStart else Alignment.CenterEnd,
				interactionEnabled = false,
				modifier = Modifier
					.fillMaxWidth()
					.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
                    .then(if (entrance != null) Modifier.graphicsLayer {
                        val progress = entrance.value
                        val remaining = 1f - progress
                        translationY = if (entryOrigin == null) (if (isOut) 8.dp else 4.dp).toPx() * remaining else 0f
                        val scale = 1f - (if (isOut) 0.035f else 0.018f) * remaining
                        scaleX = scale
                        scaleY = scale
                        alpha = (0.78f + 0.22f * progress).coerceIn(0f, 1f)
                        transformOrigin = TransformOrigin(if (isOut) 1f else 0f, 1f)
                    } else Modifier)
                    .then(contextMenuAnchorModifier)
					.onSizeChanged { size ->
						rowBoundsRegistry.putHeight(
							messageId = message.id,
							rowHeightPx = size.height
						)
					}
					.padding(horizontal = 16.dp)
					.then(rowGestureModifier),
				onClick = {},
				onLongClick = {}
			) {
				if (isPostPreview) {
					val preview = message as PostPreviewMessage
					Row(
						verticalAlignment = Alignment.Bottom,
						horizontalArrangement = Arrangement.spacedBy(8.dp)
					) {
						ChatAvatar(
							placeholderText = preview.authorName?.takeIf { it.isNotBlank() },
							avatarUrl = preview.authorAvatarUrl,
							avatarThumb = preview.authorAvatarThumb,
							modifier = Modifier.size(36.dp),
							onClick = { onAvatarClick(preview) }
						)
						if (preview.imageVariants.isNotEmpty()) {
							val albumMessage = remember(preview) {
								ImageMessage(
									id = preview.id,
									uiKey = preview.uiKey,
									clientMessageId = preview.clientMessageId,
									messageText = preview.messageText,
									createdAtMillis = preview.createdAtMillis,
									isOutgoing = false,
									authorName = preview.authorName,
									authorUsername = preview.authorUsername,
									authorAvatarUrl = preview.authorAvatarUrl,
									deliveryStatus = preview.deliveryStatus,
									images = preview.imageVariants.mapIndexed { index, variant ->
										ChatImageItem(
											id = index.toLong(),
											remoteUrl = variant.fullUrl ?: variant.previewUrl,
											thumbnailUrl = variant.lqUrl ?: variant.previewUrl,
											width = variant.width ?: 0,
											height = variant.height ?: 0
										)
									}
								)
							}
							Column(
								horizontalAlignment = Alignment.Start
							) {
								Box(modifier = contextMenuHighlightModifier) {
									if (showTail) {
										val tailColor = if (isDark) Color(0xFF262626) else Color(0xFFE2E2E3)
										ChatTailVector(
											color = tailColor,
											isOutgoing = false,
											modifier = Modifier
												.align(Alignment.BottomStart)
												.offset(
													x = 7.0.dp,
													y = 4.5.dp
												)
										)
									}
									ChatAlbumBubble(
										message = albumMessage,
										isSelectionMode = isSelectionMode,
										selectedImageIds = selectedMessageIds,
										onToggleImageSelection = onToggleSelection,
										onClick = { onChatBubbleClick(preview) },
										onLongClick = { onChatBubbleLongClick(preview) },
										onImageClick = { imageIndex, bounds, painter ->
											if (!isSelectionMode) {
												onPostImageClick(preview, imageIndex, bounds, painter)
											}
										},
										onImageBoundsChanged = { imageIndex, bounds, painter ->
											onPostImageBoundsChanged?.invoke(preview, imageIndex, bounds, painter)
										},
										hiddenImageIndex = hiddenPostImageIndex,
										hiddenImageRevealProgress = hiddenPostImageRevealProgress,
										modifier = Modifier.widthIn(max = 280.dp)
									)
								}
								if (showTimestamp) {
									Spacer(Modifier.height(3.dp))
									ChatMessageTimestamp(
										message = preview,
										isOutgoing = false,
										showUnreadDot = false,
										modifier = Modifier.padding(start = 20.dp),
									)
								}
							}
						} else {
							MessageBubble(
								chatMessage = PrimaryInMessage(
									id = preview.id,
									uiKey = preview.uiKey,
									clientMessageId = preview.clientMessageId,
									messageText = preview.messageText,
									createdAtMillis = preview.createdAtMillis,
									authorName = preview.authorName,
									authorUsername = preview.authorUsername,
									authorAvatarUrl = preview.authorAvatarUrl,
									deliveryStatus = preview.deliveryStatus,
									isPinned = preview.isPinned
								),
								onReplyClick = { if (!isSelectionMode) onReplyClick(it) },
								onReply = { if (!isSelectionMode) onReply(it) },
								onRetrySendClick = null,
								isHighlighted = isRowHighlighted,
								showAuthorHeaderForInMessages = false,
								showUnreadDot = false,
								config = config,
								modifier = incomingBubbleModifier,
								bubbleBoundsModifier = contextMenuHighlightModifier,
								showReplyPreview = false,
								showTail = showTail,
								showTimestamp = showTimestamp
							)
						}
					}
				} else if (isVideoCircle) {
					ReplyableChatContent(
						chatMessage = message,
						onReply = {
							if (!isSelectionMode) onReply(it)
						},
						modifier = Modifier.fillMaxWidth(),
					) {
						BoxWithConstraints(
							modifier = Modifier.fillMaxWidth(),
							contentAlignment = if (isOut) Alignment.CenterEnd else Alignment.CenterStart,
						) {
							val availableWidth = maxWidth
							Box(modifier = contextMenuHighlightModifier) {
								(message as? VideoCircleOutMessage)?.let { circleMsg ->
									videoCircleContent(
										circleMsg,
										isSelectionMode,
										availableWidth,
										{ selectMessage() },
									)
								}
								if (showTimestamp) {
									ChatMessageTimestamp(
										message = message,
										isOutgoing = isOut,
										showUnreadDot = showUnreadDot,
										isOverlay = true,
										modifier = Modifier
											.align(Alignment.BottomEnd)
											.padding(end = 12.dp, bottom = 8.dp),
									)
								}
							}
						}
					}
				} else if (isVoice) {
					ReplyableChatContent(
						chatMessage = message,
						onReply = {
							if (!isSelectionMode) onReply(it)
						},
						modifier = Modifier.fillMaxWidth(),
					) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start
						) {
							Column(
								horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
							) {
								Box(modifier = contextMenuHighlightModifier) {
									voiceContent(
										message as VoiceMessage,
										isSelectionMode,
										{ selectMessage() }
									)
									if (showTail) {
										val tailColor = if (message.isOutgoing) Color(0xFF7A73F8) else if (isDark) Color(0xFF262626) else Color(0xFFE2E2E3)
										ChatTailVector(
											color = tailColor,
											isOutgoing = message.isOutgoing,
											modifier = Modifier
												.align(if (message.isOutgoing) Alignment.BottomEnd else Alignment.BottomStart)
												.offset(
													x = if (message.isOutgoing) (-7.6).dp else 7.0.dp,
													y = 4.5.dp
												)
										)
									}
								}
								if (showTimestamp) {
									Spacer(Modifier.height(3.dp))
									ChatMessageTimestamp(
										message = message,
										isOutgoing = message.isOutgoing,
										showUnreadDot = showUnreadDot,
										modifier = Modifier.chatMessageTimestampPadding(message.isOutgoing),
									)
								}
							}
						}
					}
				} else if (isImage) {
					ReplyableChatContent(
						chatMessage = message,
						onReply = {
							if (!isSelectionMode) onReply(it)
						},
						modifier = Modifier.fillMaxWidth(),
					) {
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = if (message.isOutgoing) Arrangement.End else Arrangement.Start
						) {
							Column(
								horizontalAlignment = if (message.isOutgoing) Alignment.End else Alignment.Start
							) {
								Box(modifier = contextMenuHighlightModifier) {
									if (showTail) {
										val tailColor = if (message.isOutgoing) Color(0xFF7A73F8) else if (isDark) Color(0xFF262626) else Color(0xFFE2E2E3)
										ChatTailVector(
											color = tailColor,
											isOutgoing = message.isOutgoing,
											modifier = Modifier
												.align(if (message.isOutgoing) Alignment.BottomEnd else Alignment.BottomStart)
												.offset(
													x = if (message.isOutgoing) (-7.6).dp else 7.0.dp,
													y = 4.5.dp
												)
										)
									}
									imageContent(
										message as ImageMessage,
										isSelectionMode,
										selectedMessageIds,
										onToggleSelection,
										{
											if (isSelectionMode) toggleSelection()
										else if (!openMessageMenu(null)) tapMessage()
									},
										{ selectMessage() },
										{ clientMessageId -> onImageRetry?.invoke(clientMessageId) },
										{ clientMessageId -> onImageCancel?.invoke(clientMessageId) }
									)
								}
								if (showTimestamp) {
									Spacer(Modifier.height(3.dp))
									ChatMessageTimestamp(
										message = message,
										isOutgoing = message.isOutgoing,
										showUnreadDot = showUnreadDot,
										modifier = Modifier.chatMessageTimestampPadding(message.isOutgoing),
									)
								}
							}
						}
					}
				} else if (isOut) {
					Row(
						modifier = Modifier.fillMaxWidth(),
						horizontalArrangement = Arrangement.End
					) {
						MessageBubble(
							chatMessage = message,
							onReplyClick = {
								if (!isSelectionMode) onReplyClick(it)
							},
							onReply = {
								if (!isSelectionMode) onReply(it)
							},
							onRetrySendClick = if (!isSelectionMode) onRetrySendClick else null,
							isHighlighted = isRowHighlighted,
							showAuthorHeaderForInMessages = false,
							showUnreadDot = showUnreadDot,
							showTail = showTail,
							showTimestamp = showTimestamp,
							modifier = Modifier,
							bubbleBoundsModifier = contextMenuHighlightModifier,
							config = config
						)
					}
				} else {
					Row(
						verticalAlignment = Alignment.Bottom,
						horizontalArrangement = Arrangement.spacedBy(6.dp)
					) {
						if (showIncomingAvatar) {
							if (showAvatar) {
								ChatAvatar(
									placeholderText = message.authorName?.takeIf { it.isNotBlank() },
									avatarUrl = message.authorAvatarUrl,
									avatarThumb = message.authorAvatarThumb,
									modifier = Modifier.size(36.dp),
									onClick = {
										if (!isSelectionMode) {
											onAvatarClick(message)
										}
									}
								)
							} else {
								Spacer(Modifier.width(36.dp))
							}
						}
						MessageBubble(
							chatMessage = message,
							onReplyClick = {
								if (!isSelectionMode) onReplyClick(it)
							},
							onReply = {
								if (!isSelectionMode) onReply(it)
							},
							onRetrySendClick = if (!isSelectionMode) onRetrySendClick else null,
							isHighlighted = isRowHighlighted,
							showAuthorHeaderForInMessages = showAuthorHeader,
							showUnreadDot = false,
							showReplyPreview = !isSelectionMode,
							showTail = showTail,
							showTimestamp = showTimestamp,
							modifier = incomingBubbleModifier,
							bubbleBoundsModifier = contextMenuHighlightModifier,
							config = config
						)
						if (showMessageAction) {
							MessageActionArrowButton(
								onClick = { onMessageActionClick(message) }
							)
						}
					}
				}
			}
		}

		if (isPostPreview) {
			Spacer(Modifier.height(12.dp))
			HorizontalDivider(
				color = config.dividerColor ?: MaterialTheme.colorScheme.outlineVariant,
				thickness = 1.dp,
				modifier = Modifier.fillMaxWidth()
			)
			Spacer(Modifier.height(8.dp))
		} else {
			val defaultSpacing = if (message is ImageMessage || message is PostPreviewMessage) 16.dp else 2.dp
			val spacing = layoutState?.spacingAfter ?: defaultSpacing
			if (spacing > 0.dp) {
				Spacer(Modifier.height(spacing))
			}
		}

		if (shouldShowUnreadBoundaryAfterMessage) {
			UnreadBoundaryRow(
				modifier = Modifier
					.fillMaxWidth()
					.padding(horizontal = 16.dp, vertical = 6.dp)
			)
		}
	}
}
