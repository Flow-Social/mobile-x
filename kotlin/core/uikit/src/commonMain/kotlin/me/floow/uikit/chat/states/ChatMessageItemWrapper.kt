package me.floow.uikit.chat.states

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import kotlin.math.roundToInt
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.chat.common.ElevatedMessageData
import me.floow.uikit.chat.components.ChatAlbumBubble
import me.floow.uikit.chat.components.ChatMessageTimestamp
import me.floow.uikit.chat.components.ChatTailVector
import me.floow.uikit.chat.components.chatMessageTimestampPadding
import me.floow.uikit.chat.components.replyable.ReplyableChatContent
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatImageItem
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatMessageTapOutcome
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
import me.floow.uikit.chat.model.resolveMessageTapOutcome

val LocalActiveContextMenuMessageId = compositionLocalOf<Long?> { null }

/**
 * Message item row wrapper handling swipe-to-reply, multi-selection mode (checkboxes / borders),
 * context menu coordinate tracking, bubble / media content dispatch, and spacing / dividers.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LazyItemScope.ChatMessageItemWrapper(
	message: ChatMessage,
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
	elevatedDataByMessageKey: MutableMap<String, ElevatedMessageData>? = null,
	videoCircleContent: @Composable (VideoCircleOutMessage, Boolean, () -> Unit) -> Unit,
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

	val isContextMenuActive = LocalActiveContextMenuMessageId.current == message.id
	val isElevated = isContextMenuActive
	val itemLayer = rememberGraphicsLayer()
	var rootPos by remember { mutableStateOf(Offset.Zero) }
	var itemSize by remember { mutableStateOf(IntSize.Zero) }

	val isFirstInGroup = layoutState?.isFirstInCluster ?: true
	val isLastInGroup = layoutState?.isLastInCluster ?: true
	val showTail = layoutState?.showTail ?: isLastInGroup
	val showTimestamp = layoutState?.showTimestamp ?: isLastInGroup
	val showAuthorHeader = layoutState?.showAuthorHeader ?: false
	val showAvatar = layoutState?.showAvatar ?: false

	val shouldShowUnreadBoundaryBeforeMessage = !isReverseLayout &&
		unreadBoundaryMessageId == message.id
	val shouldShowUnreadBoundaryAfterMessage = isReverseLayout &&
		unreadBoundaryMessageId == message.id

	val isPostPreview = message is PostPreviewMessage
	val isVideoCircle = message is VideoCircleOutMessage ||
		(message as? VideoCircleOutMessage) != null ||
		message::class.simpleName?.contains("VideoCircle", ignoreCase = true) == true
	val isVoice = message is VoiceMessage
	val isImage = message is ImageMessage
	val isMediaMessage = isImage || isVideoCircle || isVoice || (message is PostPreviewMessage && message.imageVariants.isNotEmpty())
	val isOut = message is PrimaryOutMessage || message is ReplyOutMessage ||
		(message is ImageMessage && message.isOutgoing) ||
		((message as? VideoCircleOutMessage)?.isOutgoing == true) ||
		(message is VoiceMessage && message.isOutgoing)

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
	val pressScale = pressScaleAnimatable.value
	var currentTapRootOffset by remember { mutableStateOf<Offset?>(null) }

	val elevatedData = remember(message.id, message.uiKey, isOut, isVideoCircle, itemLayer, rootPos, itemSize) {
		ElevatedMessageData(
			messageId = message.id,
			uiKey = message.uiKey,
			isMine = isOut,
			isVideoNote = isVideoCircle,
			layer = itemLayer,
			rootPosition = rootPos,
			size = itemSize,
			videoCircleBoundsInRoot = if (isVideoCircle) Rect(rootPos, androidx.compose.ui.geometry.Size(itemSize.width.toFloat(), itemSize.height.toFloat())) else null,
			startScale = 1f,
			tapOffset = null,
		)
	}

	elevatedDataByMessageKey?.let { registry ->
		registry[message.uiKey] = elevatedData
		registry["msg_${message.id}"] = elevatedData
		registry[message.id.toString()] = elevatedData
		message.clientMessageId?.takeIf(String::isNotBlank)?.let { cmid ->
			registry["cmid_$cmid"] = elevatedData
		}
	}

	val contextMenuHighlightModifier = Modifier
		.onGloballyPositioned { coordinates ->
			rootPos = coordinates.positionInRoot()
			itemSize = coordinates.size
			if (!isScrollBusy || isVideoCircle) {
				val bounds = coordinates.boundsInRoot()
				bubbleBoundsByMessageKey[message.uiKey] = bounds
				bubbleBoundsByMessageKey["msg_${message.id}"] = bounds
				bubbleBoundsByMessageKey[message.id.toString()] = bounds
				message.clientMessageId?.takeIf(String::isNotBlank)?.let { cmid ->
					bubbleBoundsByMessageKey["cmid_$cmid"] = bounds
				}
			}
		}
		.drawWithContent {
			itemLayer.record {
				this@drawWithContent.drawContent()
			}
			if (!isElevated || isVideoCircle) {
				withTransform({
					scale(
						pressScaleAnimatable.value,
						pressScaleAnimatable.value,
						pivot = Offset(size.width / 2f, size.height / 2f),
					)
				}) {
					drawLayer(itemLayer)
				}
			}
		}

	var rowRootPosition by remember { mutableStateOf(Offset.Zero) }

	val rowGestureModifier = Modifier
		.onGloballyPositioned { coords ->
			rowRootPosition = coords.positionInRoot()
		}
		.pointerInput(message.id, isSelectionMode) {
			if (isSelectionMode) {
				detectTapGestures(
					onTap = { onToggleSelection(message.id) }
				)
				return@pointerInput
			}

			awaitEachGesture {
				val down = awaitFirstDown(requireUnconsumed = false)
				val timeMark = kotlin.time.TimeSource.Monotonic.markNow()
				var currentLocalPos = down.position
				val touchSlop = viewConfiguration.touchSlop
				var longPressTriggered = false

				// Запускаем корутину 4-фазного жеста
				val phaseJob = scope.launch {
					// Фаза 1 (0..120 мс): нулевая реакция, бабл неподвижен
					delay(120L)

					// Фаза 2 (120..320 мс): плавное сжатие бабла до 0.95f
					pressScaleAnimatable.animateTo(
						targetValue = 0.95f,
						animationSpec = tween(durationMillis = 200, easing = LinearEasing),
					)

					// Фаза 3 (320 мс): тактильный отклик, пружинный отскок 1.02f -> 1.0f
					longPressTriggered = true
					haptic.performHapticFeedback(HapticFeedbackType.LongPress)

					launch {
						pressScaleAnimatable.animateTo(
							targetValue = 1.02f,
							animationSpec = spring(
								dampingRatio = 0.72f,
								stiffness = 450f,
							),
						)
						pressScaleAnimatable.animateTo(
							targetValue = 1.0f,
							animationSpec = spring(
								dampingRatio = 0.72f,
								stiffness = 450f,
							),
						)
					}

					// Лонгтап в любом месте строки переводит чат в режим множественного выделения с чекбоксом!
					onToggleSelection(message.id)
				}

				while (true) {
					val event = awaitPointerEvent()
					val change = event.changes.firstOrNull { it.id == down.id } ?: break
					currentLocalPos = change.position

					val distance = (change.position - down.position).getDistance()
					if (distance > touchSlop) {
						// Превышение touchSlop -> отмена жеста, даем скроллить
						phaseJob.cancel()
						if (!longPressTriggered) {
							scope.launch {
								pressScaleAnimatable.animateTo(
									targetValue = 1f,
									animationSpec = spring(
										dampingRatio = 0.8f,
										stiffness = 600f,
									),
								)
							}
						}
						break
					}

					if (!change.pressed) {
						// Палец отпущен
						phaseJob.cancel()
						val elapsed = timeMark.elapsedNow().inWholeMilliseconds
						if (!longPressTriggered) {
							scope.launch {
								pressScaleAnimatable.animateTo(
									targetValue = 1f,
									animationSpec = spring(
										dampingRatio = 0.8f,
										stiffness = 600f,
									),
								)
							}
							// Короткий тап: если событие не было поглощено дочерним кликаблом (фоткой, кнопкой плей, ссылкой)
							if (!change.isConsumed && elapsed < 320L && distance <= touchSlop) {
								change.consume()
								val tapRoot = rowRootPosition + currentLocalPos
								currentTapRootOffset = tapRoot
								val highlight = bubbleBoundsByMessageKey[message.uiKey]
									?: bubbleBoundsByMessageKey["msg_${message.id}"]
									?: bubbleBoundsByMessageKey[message.id.toString()]
									?: message.clientMessageId?.let { bubbleBoundsByMessageKey["cmid_$it"] }

								if (resolveContextMenuActions(message).isNotEmpty()) {
									val startScaleSnapshot = pressScaleAnimatable.value.coerceIn(0.9f, 1.0f)
									val effectiveRootPos = highlight?.topLeft ?: if (rootPos != Offset.Zero) rootPos else rowRootPosition
									val effectiveSize = highlight?.let { IntSize(it.width.roundToInt(), it.height.roundToInt()) } ?: if (itemSize != IntSize.Zero) itemSize else IntSize(rowBoundsByMessageKey[message.uiKey]?.width?.roundToInt() ?: 0, rowBoundsByMessageKey[message.uiKey]?.height?.roundToInt() ?: 0)
									onContextMenuOpenRequest(
										message,
										highlight ?: rowBoundsByMessageKey[message.uiKey],
										highlight,
										elevatedData.copy(
											rootPosition = effectiveRootPos,
											size = effectiveSize,
											isVideoNote = isVideoCircle,
											startScale = startScaleSnapshot,
											tapOffset = tapRoot,
										),
									)
								}
								onChatBubbleClick(message)
							}
						}
						break
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

		HighlightedMessageRow(
			highlighted = isRowHighlighted,
			selected = isSelected
		) {
			SelectableMessageRow(
				showSelector = isSelectionMode,
				selected = isSelected,
				interactionEnabled = false,
				modifier = Modifier
					.fillMaxWidth()
					.animateItem(fadeInSpec = null, placementSpec = null, fadeOutSpec = null)
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
						Row(
							modifier = Modifier.fillMaxWidth(),
							horizontalArrangement = if (isOut) {
								Arrangement.End
							} else {
								Arrangement.Start
							},
						) {
							Box(modifier = contextMenuHighlightModifier) {
								(message as? VideoCircleOutMessage)?.let { circleMsg ->
									videoCircleContent(
										circleMsg,
										isSelectionMode,
										{
											haptic.performHapticFeedback(HapticFeedbackType.LongPress)
											onToggleSelection(message.id)
										},
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
										{
											haptic.performHapticFeedback(HapticFeedbackType.LongPress)
											onToggleSelection(message.id)
										}
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
											when (resolveMessageTapOutcome(isSelectionMode, isMediaMessage)) {
												ChatMessageTapOutcome.ToggleSelection -> onToggleSelection(message.id)
												ChatMessageTapOutcome.HandleMedia -> onChatBubbleClick(message)
												ChatMessageTapOutcome.OpenContextMenu -> {
													if (resolveContextMenuActions(message).isNotEmpty()) {
														val highlight = bubbleBoundsByMessageKey[message.uiKey]
															?: bubbleBoundsByMessageKey["msg_${message.id}"]
															?: bubbleBoundsByMessageKey[message.id.toString()]
															?: message.clientMessageId?.let { bubbleBoundsByMessageKey["cmid_$it"] }
														onContextMenuOpenRequest(
															message,
															highlight ?: rowBoundsByMessageKey[message.uiKey],
															highlight,
															elevatedData.copy(startScale = pressScaleAnimatable.value.coerceIn(0.9f, 1.0f)),
														)
													}
													onChatBubbleClick(message)
												}
											}
										},
										{
											haptic.performHapticFeedback(HapticFeedbackType.LongPress)
											onToggleSelection(message.id)
										},
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
