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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import me.floow.uikit.chat.model.PrimaryOutMessage
import me.floow.uikit.chat.model.ReplyOutMessage
import me.floow.uikit.chat.model.PostPreviewMessage
import me.floow.uikit.components.avatar.NetworkAvatar
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.theme.LocalTypography
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HasDataState(
	state: ChatScreenUiState.HasData,
	onChatBubbleClick: (ChatMessage) -> Unit,
	onAvatarClick: (ChatMessage) -> Unit = {},
	onReply: (ChatMessage) -> Unit,
	onReplyClick: (ChatMessage) -> Unit,
	onPostImageClick: (PostPreviewMessage, Int) -> Unit,
	onOptionClick: (ChatBubbleOption, ChatMessage) -> Unit,
	onLoadMore: () -> Unit,
	config: ChatScreenConfig,
	modifier: Modifier = Modifier
) {
	val lazyListState = rememberLazyListState()
	var dateSeparatorVisible by remember { mutableStateOf(false) }
	val coroutineScope = rememberCoroutineScope()
	val density = LocalDensity.current
	val isReverseLayout = config.layoutMode == ChatLayoutMode.NewestAtBottom

	// Floating date separator logic
	LaunchedEffect(lazyListState.isScrollInProgress) {
		if (lazyListState.isScrollInProgress) {
			dateSeparatorVisible = true
		} else {
			delay(300L)
			dateSeparatorVisible = false
		}
	}

	// Jump to message logic
	LaunchedEffect(state.highlightedMessageId, state.messages, isReverseLayout) {
		val highlightId = state.highlightedMessageId ?: return@LaunchedEffect
		var index = 0
		var found = false

		val groups = if (isReverseLayout) state.messages.asReversed() else state.messages
		for (group in groups) {
			val messages = if (isReverseLayout) group.messages.asReversed() else group.messages
			if (!isReverseLayout) {
				// header before messages
				index++
			}
			for (msg in messages) {
				if (msg.id == highlightId) {
					found = true
					break
				}
				index++
			}
			if (found) break
			if (isReverseLayout) {
				// header after messages
				index++
			}
		}

		if (found) {
			val current = lazyListState.firstVisibleItemIndex
			val distance = kotlin.math.abs(current - index)
			if (distance > 20) {
				val snapIndex = if (index > current) index - 10 else index + 10
				lazyListState.scrollToItem(snapIndex)
			}
			lazyListState.animateScrollToItem(index)
		}
	}

	var isFirstLoad by remember { mutableStateOf(true) }
	var lastTotalMessagesCount by remember { mutableStateOf(0) }
	var newMessagesCount by remember { mutableStateOf(0) }
	var initialScrollButtonSet by remember { mutableStateOf(false) }
	var previousImeBottomPadding by remember { mutableStateOf(0.dp) }
	val imeBottomPadding = if (config.liftMessageListWithIme) {
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
				if (total == 0) return@derivedStateOf true
				val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
				lastVisible >= total - 1
			}
		}
	}

	var scrollButtonVisible by remember { mutableStateOf(false) }
	var scrollButtonJob by remember { mutableStateOf<Job?>(null) }

	fun isNearBottom(): Boolean {
		return if (isReverseLayout) {
			lazyListState.firstVisibleItemIndex < 3
		} else {
			val layoutInfo = lazyListState.layoutInfo
			val totalItems = layoutInfo.totalItemsCount
			if (totalItems == 0) return true
			val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
			lastVisible >= totalItems - 3
		}
	}

	fun scrollToBottom(animate: Boolean = true) {
		val totalItems = lazyListState.layoutInfo.totalItemsCount
		val targetIndex = if (isReverseLayout) 0 else (totalItems - 1).coerceAtLeast(0)
		coroutineScope.launch {
			if (animate) {
				lazyListState.animateScrollToItem(targetIndex)
			} else {
				lazyListState.scrollToItem(targetIndex)
			}
		}
	}

	LaunchedEffect(imeBottomPadding, isReverseLayout, config.liftMessageListWithIme) {
		if (!config.liftMessageListWithIme) return@LaunchedEffect
		if (isReverseLayout) return@LaunchedEffect

		val imeChanged = imeBottomPadding != previousImeBottomPadding
		if (imeChanged) {
			val deltaPx = with(density) {
				(imeBottomPadding - previousImeBottomPadding).toPx()
			}
			if (isNearBottom()) {
				scrollToBottom(animate = false)
			} else if (deltaPx != 0f) {
				lazyListState.scrollBy(deltaPx)
			}
		}
		previousImeBottomPadding = imeBottomPadding
	}

	// Handle explicit scroll to bottom request
	LaunchedEffect(state.scrollToBottomRequestToken, isReverseLayout) {
		if (state.scrollToBottomRequestToken == 0L) return@LaunchedEffect
		scrollToBottom()
		newMessagesCount = 0
	}

	// Auto-scroll logic for new messages
	LaunchedEffect(state.messages, isReverseLayout) {
		val totalMessages = state.messages.sumOf { it.messages.size }
		if (totalMessages == 0) return@LaunchedEffect

		if (isFirstLoad) {
			if (isReverseLayout) {
				lazyListState.scrollToItem(0)
			}
			isFirstLoad = false
			lastTotalMessagesCount = totalMessages
			return@LaunchedEffect
		}

		val addedCount = (totalMessages - lastTotalMessagesCount).coerceAtLeast(0)
		lastTotalMessagesCount = totalMessages

		val nearBottom = isNearBottom()

		if (nearBottom) {
			scrollToBottom()
			newMessagesCount = 0
		} else if (addedCount > 0) {
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

	LaunchedEffect(state.messages, isReverseLayout, isAtBottom) {
		if (isReverseLayout || initialScrollButtonSet) return@LaunchedEffect
		if (state.messages.isEmpty()) return@LaunchedEffect
		if (!isAtBottom) {
			scrollButtonVisible = true
		}
		initialScrollButtonSet = true
	}

	LaunchedEffect(lazyListState, isReverseLayout, isAtBottom) {
		var lastIndex = 0
		var lastOffset = 0
		snapshotFlow { lazyListState.firstVisibleItemIndex to lazyListState.firstVisibleItemScrollOffset }
			.distinctUntilChanged()
			.collectLatest { (index, offset) ->
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
						val isPostPreview = message is PostPreviewMessage
						val isOut: Boolean = message is PrimaryOutMessage || message is ReplyOutMessage
						val showIncomingAvatar = !isOut && config.showAuthorHeaderForInMessages

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
									ReplyableChatBubble(
										chatMessage = message,
										onClick = onChatBubbleClick,
										onReplyClick = onReplyClick,
										onReply = onReply,
										isHighlighted = state.highlightedMessageId == message.id,
										onOptionClick = onOptionClick,
										showAuthorHeaderForInMessages = config.showAuthorHeaderForInMessages,
										showPinAction = config.showPinActions,
										modifier = Modifier.fillMaxWidth()
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
												ReplyableChatBubble(
													chatMessage = message,
													onClick = onChatBubbleClick,
													onReplyClick = onReplyClick,
													onReply = onReply,
													isHighlighted = state.highlightedMessageId == message.id,
													onOptionClick = onOptionClick,
													showAuthorHeaderForInMessages = config.showAuthorHeaderForInMessages,
													showPinAction = config.showPinActions,
													showReplyPreview = false,
													modifier = Modifier.wrapContentWidth()
												)
											}
											Row(
												modifier = Modifier.padding(start = if (showIncomingAvatar) 44.dp else 0.dp, top = 3.dp)
											) {
												ReplyContent(
													replyMessageText = message.replyMessageText,
													color = Color(0xFFBEBEBE),
													modifier = Modifier
														.height(25.dp)
														.clickable { onReplyClick(message) }
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
											ReplyableChatBubble(
												chatMessage = message,
												onClick = onChatBubbleClick,
												onReplyClick = onReplyClick,
												onReply = onReply,
												isHighlighted = state.highlightedMessageId == message.id,
												onOptionClick = onOptionClick,
												showAuthorHeaderForInMessages = config.showAuthorHeaderForInMessages,
												showPinAction = config.showPinActions,
												modifier = Modifier.wrapContentWidth()
											)
										}
									}
								}
							}
						}

						if (isPostPreview) {
							Spacer(Modifier.height(12.dp))
							HorizontalDivider(
								color = config.dividerColor ?: androidx.compose.material3.MaterialTheme.colorScheme.outlineVariant,
								thickness = 1.dp,
								modifier = Modifier
									.fillMaxWidth()
							)
							Spacer(Modifier.height(8.dp))
						} else {
							Spacer(Modifier.height(8.dp))
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

			ScrollToBottomButton(
				visible = !isAtBottom && (scrollButtonVisible || newMessagesCount > 0),
				badgeCount = newMessagesCount,
				onClick = {
					scrollToBottom()
					newMessagesCount = 0
				},
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(16.dp)
			)
		}
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
