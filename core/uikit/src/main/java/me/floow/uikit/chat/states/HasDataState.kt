package me.floow.uikit.chat.states

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import me.floow.uikit.R
import me.floow.uikit.chat.components.ChatBubbleOption
import me.floow.uikit.chat.components.ChatBubble
import me.floow.uikit.chat.components.DateSeparator
import androidx.compose.material3.HorizontalDivider
import me.floow.uikit.chat.components.ReplyContent
import me.floow.uikit.chat.components.ScrollToBottomButton
import me.floow.uikit.chat.components.ChatPostBubble
import me.floow.uikit.chat.components.replyable.ReplyableChatBubble
import me.floow.uikit.chat.model.ChatLayoutMode
import me.floow.uikit.chat.model.ChatMessage
import me.floow.uikit.chat.model.ChatReplyMessage
import me.floow.uikit.chat.model.ChatScreenConfig
import me.floow.uikit.chat.model.ChatScreenUiState
import me.floow.uikit.chat.model.DatedChatMessages
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.theme.LocalTypography
import java.time.LocalDate
import java.time.format.DateTimeFormatter

private enum class ScrollOrchestratorMode {
	AwaitingInitialAnchor,
	FollowingUser
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HasDataState(
	state: ChatScreenUiState.HasData,
	onChatBubbleClick: (ChatMessage) -> Unit,
	onMessageActionClick: ((ChatMessage) -> Unit)? = null,
	onAvatarClick: (ChatMessage) -> Unit = {},
	onReply: (ChatMessage) -> Unit,
	onReplyClick: (ChatMessage) -> Unit,
	onPostImageClick: (PostPreviewMessage, Int) -> Unit,
	onRequestScrollToBottom: () -> Unit = {},
	onUserStartedScroll: () -> Unit = {},
	onVisibleMessageIdsChanged: (Set<Long>) -> Unit = {},
	onFirstVisibleMessageIdChanged: (Long?) -> Unit = {},
	suspendInitialPlacement: Boolean = false,
	onOptionClick: ((ChatBubbleOption, ChatMessage) -> Unit)?,
	onLoadMore: () -> Unit,
	config: ChatScreenConfig,
	modifier: Modifier = Modifier
) {
	val interactionPolicy = config.interactionPolicy
	val scrollPolicy = config.scrollPolicy
	val lazyListState = rememberLazyListState()
	var dateSeparatorVisible by remember { mutableStateOf(false) }
	val coroutineScope = rememberCoroutineScope()
	val density = LocalDensity.current
	val isReverseLayout = config.layoutMode == ChatLayoutMode.NewestAtBottom
	val highlightedItemIndex = remember(state.highlightedMessageId, state.messages, isReverseLayout) {
		findFlattenedMessageIndex(
			datedMessages = state.messages,
			targetMessageId = state.highlightedMessageId,
			isReverseLayout = isReverseLayout
		)
	}
	var lastHandledHighlightId by remember { mutableStateOf<Long?>(null) }
	var lastHandledHighlightToken by remember { mutableStateOf(0L) }
	var lastHandledHighlightIndex by remember { mutableStateOf<Int?>(null) }
	var lastHandledScrollRequestToken by remember { mutableStateOf(0L) }
	var hasInitializedMessageCounter by remember { mutableStateOf(false) }
	var lastTotalMessagesCount by remember { mutableStateOf(0) }
	var isProgrammaticScrollInProgress by remember { mutableStateOf(false) }

	suspend fun runProgrammaticScroll(block: suspend () -> Unit) {
		isProgrammaticScrollInProgress = true
		try {
			block()
		} finally {
			isProgrammaticScrollInProgress = false
		}
	}

	suspend fun scrollToBottomInternal(animate: Boolean) {
		runProgrammaticScroll {
			lazyListState.scrollToBottom(
				isReverseLayout = isReverseLayout,
				animate = animate
			)
		}
	}

	suspend fun jumpToIndexInternal(targetIndex: Int, animate: Boolean): Boolean {
		var attempts = 0
		while (lazyListState.layoutInfo.totalItemsCount <= targetIndex && attempts < 24) {
			withFrameNanos { }
			attempts += 1
		}
		if (lazyListState.layoutInfo.totalItemsCount <= targetIndex) return false

		runProgrammaticScroll {
			val currentIndex = lazyListState.firstVisibleItemIndex
			val distance = kotlin.math.abs(currentIndex - targetIndex)
			if (animate && distance > 20) {
				val totalItems = lazyListState.layoutInfo.totalItemsCount
				val snapIndex = if (targetIndex > currentIndex) {
					(targetIndex - 10).coerceAtLeast(0)
				} else {
					(targetIndex + 10).coerceAtMost((totalItems - 1).coerceAtLeast(0))
				}
				lazyListState.scrollToItem(snapIndex)
			}
			if (animate) {
				lazyListState.animateScrollToItem(targetIndex)
			} else {
				lazyListState.scrollToItem(targetIndex)
			}
		}
		return true
	}

	// Floating date separator logic
	LaunchedEffect(lazyListState.isScrollInProgress) {
		if (lazyListState.isScrollInProgress) {
			dateSeparatorVisible = true
		} else {
			delay(300L)
			dateSeparatorVisible = false
		}
	}

	var newMessagesCount by remember { mutableStateOf(0) }
	var previousImeBottomPadding by remember { mutableStateOf(0.dp) }
	val imeBottomPadding = if (scrollPolicy.liftMessageListWithIme) {
		WindowInsets.ime.asPaddingValues().calculateBottomPadding()
	} else {
		0.dp
	}
	val listBottomPadding = 8.dp

	val isAtBottom by remember {
		derivedStateOf {
			if (isReverseLayout) {
				lazyListState.firstVisibleItemIndex == 0
			} else {
				val layoutInfo = lazyListState.layoutInfo
				val total = layoutInfo.totalItemsCount
				if (total == 0) return@derivedStateOf false
				val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
				lastVisible >= total - 1
			}
		}
	}
	val hasListLayout by remember {
		derivedStateOf {
			lazyListState.layoutInfo.visibleItemsInfo.isNotEmpty()
		}
	}

	var scrollButtonVisible by remember { mutableStateOf(false) }
	var scrollButtonJob by remember { mutableStateOf<Job?>(null) }
	var hasUserStartedScroll by remember { mutableStateOf(false) }
	var scrollOrchestratorMode by remember { mutableStateOf(ScrollOrchestratorMode.FollowingUser) }

	LaunchedEffect(lazyListState) {
		snapshotFlow { lazyListState.isScrollInProgress }
			.distinctUntilChanged()
			.collectLatest { isInProgress ->
				if (isInProgress && !hasUserStartedScroll && !isProgrammaticScrollInProgress) {
					hasUserStartedScroll = true
					scrollOrchestratorMode = ScrollOrchestratorMode.FollowingUser
					onUserStartedScroll()
				}
			}
	}

	fun isNearBottom(): Boolean {
		return lazyListState.isNearBottom(isReverseLayout = isReverseLayout)
	}

	fun scrollToBottom(animate: Boolean = true) {
		coroutineScope.launch {
			scrollToBottomInternal(animate)
		}
	}

	LaunchedEffect(imeBottomPadding, isReverseLayout, scrollPolicy.liftMessageListWithIme) {
		if (!scrollPolicy.liftMessageListWithIme) return@LaunchedEffect
		if (isReverseLayout) return@LaunchedEffect

		val imeChanged = imeBottomPadding != previousImeBottomPadding
		if (imeChanged) {
			val deltaPx = with(density) {
				(imeBottomPadding - previousImeBottomPadding).toPx()
			}
			if (isNearBottom()) {
				scrollToBottomInternal(animate = false)
			} else if (deltaPx != 0f) {
				runProgrammaticScroll {
					lazyListState.scrollBy(deltaPx)
				}
			}
		}
		previousImeBottomPadding = imeBottomPadding
	}

	LaunchedEffect(
		state.messages,
		state.highlightedMessageId,
		state.highlightedMessageRequestToken,
		state.keepHighlightedMessageAnchored,
		highlightedItemIndex,
		state.scrollToBottomRequestToken,
		isReverseLayout,
		scrollPolicy.animateJumpToHighlightedMessage,
		suspendInitialPlacement
	) {
		val totalMessages = state.messages.sumOf { it.messages.size }
		if (!hasInitializedMessageCounter) {
			hasInitializedMessageCounter = true
			lastTotalMessagesCount = totalMessages
			if (isReverseLayout && totalMessages > 0) {
				scrollToBottomInternal(animate = false)
			}
		}
		if (suspendInitialPlacement) return@LaunchedEffect

		val highlightId = state.highlightedMessageId
		val highlightToken = state.highlightedMessageRequestToken
		val shouldLockInitialAnchor = state.keepHighlightedMessageAnchored &&
			highlightId != null &&
			!hasUserStartedScroll
		scrollOrchestratorMode = if (shouldLockInitialAnchor) {
			ScrollOrchestratorMode.AwaitingInitialAnchor
		} else {
			ScrollOrchestratorMode.FollowingUser
		}
		val shouldForceReanchor = state.keepHighlightedMessageAnchored &&
			!hasUserStartedScroll &&
			highlightedItemIndex != null &&
			highlightedItemIndex != lastHandledHighlightIndex
		val shouldHandleByToken = highlightToken != 0L && highlightToken != lastHandledHighlightToken
		val shouldHandleByNewHighlightId = highlightId != null && highlightId != lastHandledHighlightId
		if (
			highlightId != null &&
			highlightedItemIndex != null &&
			(shouldHandleByNewHighlightId || shouldHandleByToken || shouldForceReanchor)
			) {
				val didJump = jumpToIndexInternal(
					targetIndex = highlightedItemIndex,
					animate = scrollPolicy.animateJumpToHighlightedMessage &&
						scrollOrchestratorMode == ScrollOrchestratorMode.FollowingUser
				)
				if (didJump) {
					lastHandledHighlightId = highlightId
					lastHandledHighlightToken = highlightToken
				lastHandledHighlightIndex = highlightedItemIndex
			}
			return@LaunchedEffect
		} else if (highlightId == null) {
			lastHandledHighlightId = null
			lastHandledHighlightToken = 0L
			lastHandledHighlightIndex = null
		}

		val scrollRequestToken = state.scrollToBottomRequestToken
		if (scrollRequestToken != 0L && scrollRequestToken != lastHandledScrollRequestToken) {
			lastHandledScrollRequestToken = scrollRequestToken
			scrollOrchestratorMode = ScrollOrchestratorMode.FollowingUser
			scrollToBottomInternal(animate = true)
			newMessagesCount = 0
			lastTotalMessagesCount = totalMessages
			return@LaunchedEffect
		}

		val addedCount = (totalMessages - lastTotalMessagesCount).coerceAtLeast(0)
		lastTotalMessagesCount = totalMessages
		if (addedCount <= 0) return@LaunchedEffect

		val isAnchorLocked = scrollOrchestratorMode == ScrollOrchestratorMode.AwaitingInitialAnchor
		val allowIncomingAutoScroll = if (isAnchorLocked) {
			false
		} else if (isReverseLayout) {
			true
		} else {
			hasUserStartedScroll
		}
		if (allowIncomingAutoScroll && lazyListState.isNearBottom(isReverseLayout = isReverseLayout)) {
			scrollToBottomInternal(animate = true)
			newMessagesCount = 0
		} else {
			newMessagesCount += addedCount
		}
	}

	LaunchedEffect(isAtBottom) {
		if (isAtBottom) {
			newMessagesCount = 0
			scrollButtonVisible = false
			scrollButtonJob?.cancel()
			scrollButtonJob = null
		}
	}

	LaunchedEffect(lazyListState, isReverseLayout, isAtBottom) {
		if (scrollPolicy.alwaysShowScrollToBottomWhenNotAtBottom) return@LaunchedEffect
		var lastIndex = 0
		var lastOffset = 0
		snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
			.distinctUntilChanged()
			.collectLatest { (index, offset) ->
				if (isProgrammaticScrollInProgress) {
					lastIndex = index
					lastOffset = offset
					return@collectLatest
				}

				val scrollingAwayFromBottom = if (isReverseLayout) {
					index > lastIndex || (index == lastIndex && offset > lastOffset)
				} else {
					index < lastIndex || (index == lastIndex && offset < lastOffset)
				}

				val scrollingTowardBottom = if (isReverseLayout) {
					index < lastIndex || (index == lastIndex && offset < lastOffset)
				} else {
					index > lastIndex || (index == lastIndex && offset > lastOffset)
				}

				lastIndex = index
				lastOffset = offset

				if (isAtBottom) return@collectLatest

				if (scrollingAwayFromBottom) {
					scrollButtonJob?.cancel()
					scrollButtonJob = null
					scrollButtonVisible = true
				} else if (scrollingTowardBottom) {
					scrollButtonVisible = false
					scrollButtonJob?.cancel()
					scrollButtonJob = coroutineScope.launch {
						delay(3000)
						if (!isAtBottom) {
							scrollButtonVisible = true
						}
					}
				}
			}
	}

	LaunchedEffect(state.canLoadMore, state.isLoadingMore, isReverseLayout) {
		if (!state.canLoadMore || state.isLoadingMore) return@LaunchedEffect
		snapshotFlow { lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
			.distinctUntilChanged()
			.collectLatest { lastVisible ->
				val totalItems = lazyListState.layoutInfo.totalItemsCount
				if (totalItems == 0) return@collectLatest
				val shouldLoadMore = lastVisible >= totalItems - 3
				if (shouldLoadMore) {
					onLoadMore()
				}
			}
	}

	LaunchedEffect(lazyListState) {
		snapshotFlow {
			val visibleMessageIds = lazyListState.layoutInfo.visibleItemsInfo
				.mapNotNull { item -> item.key as? Long }
				.toSet()
			val firstVisibleMessageId = lazyListState.layoutInfo.visibleItemsInfo
				.asSequence()
				.mapNotNull { item -> item.key as? Long }
				.firstOrNull()
			visibleMessageIds to firstVisibleMessageId
		}
			.distinctUntilChanged()
			.collectLatest { (visibleMessageIds, firstVisibleMessageId) ->
				onVisibleMessageIdsChanged(visibleMessageIds)
				onFirstVisibleMessageIdChanged(firstVisibleMessageId)
			}
	}

	val dateSeparatorModifier = Modifier
		.fillMaxWidth()
		.height(32.dp)

	Column(
		modifier = modifier,
		horizontalAlignment = Alignment.CenterHorizontally
	) {
		Box(
			Modifier
				.fillMaxWidth()
				.weight(1f)
		) {
			LazyColumn(
				state = lazyListState,
				reverseLayout = isReverseLayout,
				modifier = Modifier
					.fillMaxWidth()
					.fillMaxHeight(),
				contentPadding = PaddingValues(bottom = listBottomPadding)
			) {
				val groups = if (isReverseLayout) state.messages.asReversed() else state.messages
				groups.forEach { (date, messageList) ->
					val messages = if (isReverseLayout) messageList.asReversed() else messageList

					if (!isReverseLayout) {
						item(key = "header_$date") {
							Spacer(Modifier.height(8.dp))
							Row(
								horizontalArrangement = Arrangement.Center,
								modifier = dateSeparatorModifier,
							) {
								DateSeparator(text = getDateSeparatorText(date))
							}
							Spacer(Modifier.height(8.dp))
						}
					}

						items(
							items = messages,
							key = { it.id }
						) { message ->
							val shouldShowUnreadBoundaryBeforeMessage = !isReverseLayout &&
								state.unreadBoundaryMessageId == message.id
							val shouldShowUnreadBoundaryAfterMessage = isReverseLayout &&
								state.unreadBoundaryMessageId == message.id
							if (shouldShowUnreadBoundaryBeforeMessage) {
								UnreadBoundaryRow(
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 14.dp, vertical = 6.dp)
								)
							}

							val isPostPreview = message is PostPreviewMessage
							val isOut: Boolean = message is PrimaryOutMessage || message is ReplyOutMessage
							val showIncomingAvatar = !isOut && interactionPolicy.showAuthorHeaderForInMessages
							val showMessageAction = !isOut && !isPostPreview && onMessageActionClick != null
						val incomingBubbleModifier = if (showMessageAction) {
							Modifier
								.widthIn(max = 220.dp)
								.wrapContentWidth()
						} else {
							Modifier.wrapContentWidth()
						}

						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(horizontal = 14.dp),
							horizontalArrangement = if (isOut) Arrangement.End else Arrangement.Start
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
									onClick = { onAvatarClick(preview) }
								)
								ChatPostBubble(
									authorName = preview.authorName.orEmpty(),
										imageVariants = preview.imageVariants,
										likesCount = preview.likesCount,
										description = preview.messageText,
										dateTime = preview.dateTime,
										onImageClick = { index -> onPostImageClick(preview, index) },
										modifier = Modifier.widthIn(max = 280.dp)
									)
								}
							} else {
								if (isOut) {
									MessageBubble(
										chatMessage = message,
										onClick = onChatBubbleClick,
										onReplyClick = onReplyClick,
										onReply = onReply,
										isHighlighted = config.showHighlightedMessageBackground && state.highlightedMessageId == message.id,
										onOptionClick = onOptionClick,
										showAuthorHeaderForInMessages = interactionPolicy.showAuthorHeaderForInMessages,
											showPinAction = interactionPolicy.showPinActions,
											modifier = Modifier.fillMaxWidth(),
											config = config
										)
								} else {
									if (message is ChatReplyMessage) {
										Column {
											Row(
												verticalAlignment = Alignment.Bottom,
												horizontalArrangement = Arrangement.spacedBy(8.dp)
											) {
												if (showIncomingAvatar) {
													ChatAvatar(
														placeholderText = message.authorName?.takeIf { it.isNotBlank() },
														avatarUrl = message.authorAvatarUrl,
														onClick = { onAvatarClick(message) }
													)
												}
												MessageBubble(
													chatMessage = message,
													onClick = onChatBubbleClick,
													onReplyClick = onReplyClick,
													onReply = onReply,
													isHighlighted = config.showHighlightedMessageBackground && state.highlightedMessageId == message.id,
													onOptionClick = onOptionClick,
													showAuthorHeaderForInMessages = interactionPolicy.showAuthorHeaderForInMessages,
													showPinAction = interactionPolicy.showPinActions,
													showReplyPreview = false,
													modifier = incomingBubbleModifier,
													config = config
												)
												if (showMessageAction) {
													MessageActionArrowButton(
														onClick = { onMessageActionClick(message) }
													)
												}
											}
											Row(
												modifier = Modifier.padding(start = if (showIncomingAvatar) 44.dp else 0.dp, top = 3.dp)
											) {
												ReplyContent(
													replyMessageText = message.replyMessageText,
													color = Color(0xFFBEBEBE),
													modifier = Modifier
														.height(25.dp)
														.then(
															if (interactionPolicy.showReplyInteractions) {
																Modifier.clickable { onReplyClick(message) }
															} else {
																Modifier
															}
														)
												)
											}
										}
									} else {
										Row(
											verticalAlignment = Alignment.Bottom,
											horizontalArrangement = Arrangement.spacedBy(8.dp)
										) {
											if (showIncomingAvatar) {
													ChatAvatar(
														placeholderText = message.authorName?.takeIf { it.isNotBlank() },
														avatarUrl = message.authorAvatarUrl,
														onClick = { onAvatarClick(message) }
													)
											}
											MessageBubble(
												chatMessage = message,
												onClick = onChatBubbleClick,
												onReplyClick = onReplyClick,
												onReply = onReply,
												isHighlighted = config.showHighlightedMessageBackground && state.highlightedMessageId == message.id,
												onOptionClick = onOptionClick,
												showAuthorHeaderForInMessages = interactionPolicy.showAuthorHeaderForInMessages,
												showPinAction = interactionPolicy.showPinActions,
												modifier = incomingBubbleModifier,
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
						}

							if (isPostPreview) {
								Spacer(Modifier.height(12.dp))
								HorizontalDivider(
									color = config.dividerColor ?: MaterialTheme.colorScheme.outlineVariant,
									thickness = 1.dp,
								modifier = Modifier
									.fillMaxWidth()
							)
							Spacer(Modifier.height(8.dp))
							} else {
								Spacer(Modifier.height(8.dp))
							}

							if (shouldShowUnreadBoundaryAfterMessage) {
								UnreadBoundaryRow(
									modifier = Modifier
										.fillMaxWidth()
										.padding(horizontal = 14.dp, vertical = 6.dp)
								)
							}
						}

					if (isReverseLayout) {
						item(key = "header_$date") {
							Spacer(Modifier.height(8.dp))
							Row(
								horizontalArrangement = Arrangement.Center,
								modifier = dateSeparatorModifier,
							) {
								DateSeparator(text = getDateSeparatorText(date))
							}
							Spacer(Modifier.height(8.dp))
						}
					}
				}

				if (state.isLoadingMore) {
					item(key = "loading_more") {
						Row(
							horizontalArrangement = Arrangement.Center,
							modifier = Modifier
								.fillMaxWidth()
								.padding(vertical = 12.dp)
						) {
							FlowLoadingIndicator()
						}
					}
				}
			}

			Column {
				Spacer(Modifier.height(8.dp))

				Row(
					horizontalArrangement = Arrangement.Center,
					modifier = dateSeparatorModifier,
				) {
					AnimatedVisibility(
						visible = dateSeparatorVisible,
						enter = fadeIn(),
						exit = fadeOut()
					) {
						DateSeparator(
							text = stringResource(R.string.today)
						)
					}
				}

				Spacer(Modifier.height(8.dp))
			}

				val scrollBadgeCount = state.scrollToBottomBadgeCount
					.takeIf { it > 0 }
					?: newMessagesCount
				val shouldForceShowScrollToBottom = scrollPolicy.alwaysShowScrollToBottomWhenNotAtBottom &&
					hasListLayout &&
					!isAtBottom

				ScrollToBottomButton(
				visible = shouldForceShowScrollToBottom || (hasListLayout && !isAtBottom && (scrollButtonVisible || scrollBadgeCount > 0)),
				badgeCount = scrollBadgeCount,
				onClick = {
					scrollToBottom()
					newMessagesCount = 0
					onRequestScrollToBottom()
				},
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(16.dp)
			)
		}
	}
}

@Composable
private fun MessageActionArrowButton(
	onClick: () -> Unit,
	modifier: Modifier = Modifier
) {
	IconButton(
		onClick = onClick,
		modifier = modifier.size(32.dp)
	) {
		Icon(
			imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
			contentDescription = stringResource(R.string.chat_open_thread),
			tint = MaterialTheme.colorScheme.onSurfaceVariant
		)
	}
}

@Composable
private fun UnreadBoundaryRow(
	modifier: Modifier = Modifier
) {
	Row(
		verticalAlignment = Alignment.CenterVertically,
		horizontalArrangement = Arrangement.spacedBy(8.dp),
		modifier = modifier
	) {
		HorizontalDivider(
			color = MaterialTheme.colorScheme.outlineVariant,
			thickness = 1.dp,
			modifier = Modifier.weight(1f)
		)
		Text(
			text = stringResource(R.string.chat_unread_boundary),
			style = LocalTypography.current.labelMedium,
			color = MaterialTheme.colorScheme.primary
		)
		HorizontalDivider(
			color = MaterialTheme.colorScheme.outlineVariant,
			thickness = 1.dp,
			modifier = Modifier.weight(1f)
		)
	}
}

@Composable
private fun MessageBubble(
	chatMessage: ChatMessage,
	onClick: (ChatMessage) -> Unit,
	onReplyClick: (ChatMessage) -> Unit,
	onReply: (ChatMessage) -> Unit,
	isHighlighted: Boolean,
	onOptionClick: ((ChatBubbleOption, ChatMessage) -> Unit)?,
	showAuthorHeaderForInMessages: Boolean,
	showPinAction: Boolean,
	config: ChatScreenConfig,
	modifier: Modifier = Modifier,
	showReplyPreview: Boolean = true
	) {
		if (config.showReplyInteractions) {
		ReplyableChatBubble(
			chatMessage = chatMessage,
			onClick = onClick,
			onReplyClick = onReplyClick,
			onReply = onReply,
			isHighlighted = isHighlighted,
			onOptionClick = onOptionClick,
			showAuthorHeaderForInMessages = showAuthorHeaderForInMessages,
			showPinAction = showPinAction,
			showReplyPreview = showReplyPreview,
			modifier = modifier
		)
	} else {
		ChatBubble(
			chatMessage = chatMessage,
			onClick = onClick,
			onReplyClick = onReplyClick,
			isHighlighted = isHighlighted,
			onOptionClick = onOptionClick,
			showAuthorHeaderForInMessages = showAuthorHeaderForInMessages,
			showPinAction = showPinAction,
			showReplyPreview = showReplyPreview,
			modifier = modifier
		)
	}
}

private fun findFlattenedMessageIndex(
	datedMessages: List<DatedChatMessages>,
	targetMessageId: Long?,
	isReverseLayout: Boolean
): Int? {
	val highlightId = targetMessageId ?: return null
	var flatIndex = 0
	val groups = if (isReverseLayout) datedMessages.asReversed() else datedMessages

	for (group in groups) {
		val messages = if (isReverseLayout) group.messages.asReversed() else group.messages
		if (!isReverseLayout) {
			flatIndex += 1 // Header goes before messages.
		}
		for (message in messages) {
			if (message.id == highlightId) {
				return flatIndex
			}
			flatIndex += 1
		}
		if (isReverseLayout) {
			flatIndex += 1 // Header goes after messages.
		}
	}
	return null
}

private fun androidx.compose.foundation.lazy.LazyListState.isNearBottom(
	isReverseLayout: Boolean
): Boolean {
	return if (isReverseLayout) {
		firstVisibleItemIndex < 3
	} else {
		val layoutInfo = layoutInfo
		val totalItems = layoutInfo.totalItemsCount
		if (totalItems == 0) return false
		val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
		lastVisible >= totalItems - 3
	}
}

private suspend fun androidx.compose.foundation.lazy.LazyListState.scrollToBottom(
	isReverseLayout: Boolean,
	animate: Boolean
) {
	val totalItems = layoutInfo.totalItemsCount
	val targetIndex = if (isReverseLayout) 0 else (totalItems - 1).coerceAtLeast(0)
	if (animate) {
		animateScrollToItem(targetIndex)
	} else {
		scrollToItem(targetIndex)
	}
}

@Composable
fun getDateSeparatorText(date: LocalDate): String {
	return if (date == LocalDate.now()) {
		stringResource(R.string.today)
	} else {
		date.format(DateTimeFormatter.ofPattern("dd.MM.yy"))
	}
}

@Composable
private fun ChatAvatar(
	placeholderText: String?,
	avatarUrl: String?,
	onClick: (() -> Unit)? = null,
	modifier: Modifier = Modifier
) {
	NetworkAvatar(
		name = placeholderText.orEmpty(),
		avatarModel = avatarUrl,
		modifier = if (onClick != null) {
			modifier.clickable { onClick() }
		} else {
			modifier
		}
	)
}
