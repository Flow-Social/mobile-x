package me.floow.uikit.chat.states

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.unit.dp
import me.floow.uikit.chat.components.ChatAlbumBubble
import me.floow.uikit.chat.components.ChatTailVector
import me.floow.uikit.chat.components.MessageTimestampRow
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
	onContextMenuOpenRequest: (ChatMessage, Rect?, Rect?) -> Unit,
	onContextMenuDismissRequest: () -> Unit,
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
	val isVideoCircle = message is VideoCircleOutMessage
	val isVoice = message is VoiceMessage
	val isImage = message is ImageMessage
	val isOut = message is PrimaryOutMessage || message is ReplyOutMessage ||
		(message is ImageMessage && message.isOutgoing) ||
		(message is VideoCircleOutMessage && message.isOutgoing) ||
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

	val contextMenuHighlightModifier = if (shouldMeasureBounds) {
		Modifier.onGloballyPositioned { coordinates ->
			if (!isScrollBusy || isVideoCircle) {
				val bounds = coordinates.boundsInRoot()
				bubbleBoundsByMessageKey[message.uiKey] = bounds
				message.clientMessageId?.takeIf(String::isNotBlank)?.let { cmid ->
					bubbleBoundsByMessageKey["cmid_$cmid"] = bounds
				}
			}
		}
	} else {
		Modifier
	}

	val incomingBubbleModifier = if (showMessageAction) {
		Modifier
			.widthIn(max = 220.dp)
			.wrapContentWidth()
	} else {
		Modifier.wrapContentWidth()
	}

	Column(modifier = modifier.fillMaxWidth()) {
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
				interactionEnabled = true,
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
					.padding(horizontal = 16.dp),
				onClick = {
					when (resolveMessageTapOutcome(isSelectionMode)) {
						ChatMessageTapOutcome.ToggleSelection -> onToggleSelection(message.id)
						ChatMessageTapOutcome.OpenContextMenu -> {
							if (resolveContextMenuActions(message).isNotEmpty()) {
								onContextMenuOpenRequest(
									message,
									rowBoundsByMessageKey[message.uiKey],
									bubbleBoundsByMessageKey[message.uiKey]
								)
							}
							onChatBubbleClick(message)
						}
					}
				},
				onLongClick = {
					if (isSelectionMode) {
						onToggleSelection(message.id)
					} else {
						onContextMenuDismissRequest()
						onChatBubbleLongClick(message)
					}
				}
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
									MessageTimestampRow(
										formattedTime = formatChatClockTime(preview.createdAtMillis),
										isOutgoing = false,
										deliveryStatus = preview.deliveryStatus,
										isPinned = preview.isPinned,
										showMessageStatus = true,
										showUnreadDot = false,
										modifier = Modifier.padding(start = 20.dp)
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
							modifier = Modifier
								.fillMaxWidth()
								.animateContentSize(animationSpec = tween(180)),
							horizontalArrangement = if (message.isOutgoing) {
								Arrangement.End
							} else {
								Arrangement.Start
							},
						) {
							Box(modifier = contextMenuHighlightModifier) {
								videoCircleContent(
									message as VideoCircleOutMessage,
									isSelectionMode,
									{
										if (isSelectionMode) {
											onToggleSelection(message.id)
										} else {
											onContextMenuDismissRequest()
											onChatBubbleLongClick(message)
										}
									},
								)
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
											if (isSelectionMode) {
												onToggleSelection(message.id)
											} else {
												onContextMenuDismissRequest()
												onChatBubbleLongClick(message)
											}
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
									MessageTimestampRow(
										formattedTime = formatChatClockTime(message.createdAtMillis),
										isOutgoing = message.isOutgoing,
										deliveryStatus = message.deliveryStatus,
										isPinned = message.isPinned,
										showMessageStatus = true,
										showUnreadDot = false,
										modifier = Modifier.padding(
											start = if (message.isOutgoing) 0.dp else 20.dp,
											end = if (message.isOutgoing) 20.dp else 0.dp
										)
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
											when (resolveMessageTapOutcome(isSelectionMode)) {
												ChatMessageTapOutcome.ToggleSelection -> onToggleSelection(message.id)
												ChatMessageTapOutcome.OpenContextMenu -> {
													if (resolveContextMenuActions(message).isNotEmpty()) {
														onContextMenuOpenRequest(
															message,
															rowBoundsByMessageKey[message.uiKey],
															bubbleBoundsByMessageKey[message.uiKey]
														)
													}
													onChatBubbleClick(message)
												}
											}
										},
										{
											if (isSelectionMode) {
												onToggleSelection(message.id)
											} else {
												onContextMenuDismissRequest()
												onChatBubbleLongClick(message)
											}
										},
										{ clientMessageId -> onImageRetry?.invoke(clientMessageId) },
										{ clientMessageId -> onImageCancel?.invoke(clientMessageId) }
									)
								}
								if (showTimestamp) {
									Spacer(Modifier.height(3.dp))
									MessageTimestampRow(
										formattedTime = formatChatClockTime(message.createdAtMillis),
										isOutgoing = message.isOutgoing,
										deliveryStatus = message.deliveryStatus,
										isPinned = message.isPinned,
										showMessageStatus = true,
										showUnreadDot = false,
										modifier = Modifier.padding(
											start = if (message.isOutgoing) 0.dp else 20.dp,
											end = if (message.isOutgoing) 20.dp else 0.dp
										)
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
						verticalAlignment = Alignment.Top,
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
