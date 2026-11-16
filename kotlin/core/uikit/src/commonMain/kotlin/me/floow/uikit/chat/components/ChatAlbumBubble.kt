package me.floow.uikit.chat.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import me.floow.uikit.components.media.ProgressiveImage
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.floow.uikit.chat.components.media.MediaTransferOverlay
import me.floow.uikit.chat.model.ChatImageItem
import me.floow.uikit.chat.model.ImageMessage
import androidx.compose.ui.draw.blur
import me.floow.uikit.chat.model.MediaTransferActionIcon
import me.floow.uikit.chat.model.MediaTransferPhase
import me.floow.uikit.chat.model.MediaTransferUiState
import me.floow.uikit.chat.model.VideoCircleUploadStatus
import me.floow.uikit.components.misc.textwrap.TextWrap
import me.floow.uikit.components.misc.textwrap.buildFlowAnnotatedText
import me.floow.uikit.theme.LocalTypography

import me.floow.domain.media.MediaDownloadState

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ChatAlbumBubble(
	message: ImageMessage,
	isSelectionMode: Boolean,
	selectedImageIds: Set<Long> = emptySet(),
	onClick: () -> Unit = {},
	onLongClick: () -> Unit = {},
	onToggleImageSelection: ((Long) -> Unit)? = null,
	onImageClick: (index: Int, bounds: Rect?, painter: Painter?) -> Unit,
	onImageBoundsChanged: ((index: Int, bounds: Rect, painter: Painter?) -> Unit)? = null,
	onCancelUpload: ((clientMessageId: String) -> Unit)? = null,
	onRetryUpload: ((clientMessageId: String) -> Unit)? = null,
	hiddenImageIndex: Int? = null,
	hiddenImageRevealProgress: Float = 0f,
	uploadProgressByClientMessageId: Map<String, Float>? = null,
	downloadStatesByUrl: Map<String, MediaDownloadState> = emptyMap(),
	onDownloadImageClick: ((url: String) -> Unit)? = null,
	onCancelDownloadImage: ((url: String) -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	val items = message.images
	if (items.isEmpty()) return

	val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
	val isOutgoing = message.isOutgoing
	val bubbleShape = RoundedCornerShape(20.dp)

	val bubbleColor = if (isOutgoing) Color(0xFF7A73F8) else if (isDark) Color(0xFF262626) else Color(0xFFE2E2E3)
	val defaultTextColor = if (isOutgoing || isDark) Color.White else Color(0xFF000000)

	val hasCaption = message.messageText.isNotBlank()

	Column(
		modifier = modifier
			.widthIn(max = 280.dp)
			.clip(bubbleShape)
			.background(bubbleColor)
			.then(
				if (isOutgoing) {
					Modifier.border(
						width = 1.dp,
						brush = Brush.verticalGradient(
							listOf(
								Color.White.copy(alpha = 0.5f),
								Color.White.copy(alpha = 0.0f)
							)
						),
						shape = bubbleShape
					)
				} else Modifier
			)
			.combinedClickable(
				onClick = onClick,
				onLongClick = onLongClick,
			)
	) {
		Box(modifier = Modifier.fillMaxWidth().clip(bubbleShape)) {
			AlbumGrid(
				items = items,
				hiddenImageIndex = hiddenImageIndex,
				hiddenImageRevealProgress = hiddenImageRevealProgress,
				isSelectionMode = isSelectionMode,
				selectedImageIds = selectedImageIds,
				onToggleImageSelection = onToggleImageSelection,
				onClick = onClick,
				onLongClick = onLongClick,
				onImageClick = onImageClick,
				onImageBoundsChanged = onImageBoundsChanged,
				onCancelUpload = onCancelUpload,
				onRetryUpload = onRetryUpload,
				uploadProgressByClientMessageId = uploadProgressByClientMessageId,
				downloadStatesByUrl = downloadStatesByUrl,
				onDownloadImageClick = onDownloadImageClick,
				onCancelDownloadImage = onCancelDownloadImage,
				modifier = Modifier.fillMaxWidth(),
			)
		}

		if (hasCaption) {
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.padding(start = 10.dp, end = 10.dp, top = 6.dp, bottom = 8.dp)
			) {
				val hasSpoiler = remember(message.messageText) {
					message.messageText.contains("||")
				}
				var isSpoilerRevealed by remember(message.id) { mutableStateOf(false) }
				val textColor = defaultTextColor
				val linkColor = if (isOutgoing) Color.White else MaterialTheme.colorScheme.primary
				val annotatedMessageText = remember(message.messageText, isSpoilerRevealed, textColor, linkColor) {
					buildFlowAnnotatedText(
						text = message.messageText,
						linkColor = linkColor,
						isSpoilerRevealed = isSpoilerRevealed
					)
				}

				TextWrap(
					text = annotatedMessageText,
					color = textColor,
					style = LocalTypography.current.bodyMedium.copy(fontSize = 15.sp),
					isSpoilerRevealed = isSpoilerRevealed,
					onRevealSpoiler = if (hasSpoiler && !isSpoilerRevealed) {
						{ isSpoilerRevealed = true }
					} else null,
				)
			}
		}
	}
}

private data class MessageGroupedLayoutAttempt(
	val lineCounts: List<Int>,
	val heights: List<Dp>,
)

private fun generateLayoutAttempts(total: Int): List<List<Int>> {
	if (total <= 0) return emptyList()
	return when (total) {
		1 -> listOf(listOf(1))
		2 -> listOf(listOf(2), listOf(1, 1))
		3 -> listOf(listOf(1, 2), listOf(2, 1), listOf(3))
		4 -> listOf(listOf(2, 2), listOf(1, 3), listOf(3, 1))
		5 -> listOf(listOf(2, 3), listOf(3, 2), listOf(1, 2, 2), listOf(2, 2, 1))
		6 -> listOf(listOf(3, 3), listOf(2, 2, 2), listOf(2, 4))
		7 -> listOf(listOf(1, 3, 3), listOf(2, 3, 2), listOf(3, 2, 2))
		8 -> listOf(listOf(2, 3, 3), listOf(3, 2, 3), listOf(3, 3, 2))
		9 -> listOf(listOf(3, 3, 3), listOf(2, 2, 2, 3))
		10 -> listOf(listOf(1, 3, 3, 3), listOf(2, 3, 3, 2), listOf(3, 2, 2, 3))
		else -> {
			val rows = mutableListOf<Int>()
			var rem = total
			while (rem > 0) {
				val take = if (rem >= 3) 3 else rem
				rows.add(take)
				rem -= take
			}
			listOf(rows)
		}
	}
}

private fun computeOptimalLayout(
	items: List<ChatImageItem>,
	availableWidth: Dp,
): MessageGroupedLayoutAttempt {
	val total = items.size
	if (total == 0) return MessageGroupedLayoutAttempt(emptyList(), emptyList())

	val ratios = items.map { item ->
		val r = if (item.width > 0 && item.height > 0) {
			item.width.toFloat() / item.height.toFloat()
		} else {
			1f
		}
		if (r.isNaN() || r <= 0f) 1f else r.coerceIn(0.4f, 2.5f)
	}

	if (total == 1) {
		val singleRatio = ratios[0]
		val singleHeight = (availableWidth / singleRatio).coerceIn(110.dp, 260.dp)
		return MessageGroupedLayoutAttempt(listOf(1), listOf(singleHeight))
	}

	val attemptsPartitions = generateLayoutAttempts(total)
	val maxHeightValue = (availableWidth * (4f / 3f)).value

	var optimal: MessageGroupedLayoutAttempt? = null
	var optimalDiff = Float.MAX_VALUE

	for (partition in attemptsPartitions) {
		var itemCursor = 0
		val heights = mutableListOf<Dp>()
		var valid = true

		for (count in partition) {
			var sumAspectRatios = 0f
			for (i in 0 until count) {
				sumAspectRatios += ratios[itemCursor + i]
			}
			itemCursor += count
			if (sumAspectRatios <= 0f) {
				valid = false
				break
			}
			val rowHeight = (availableWidth / sumAspectRatios).coerceIn(110.dp, 260.dp)
			heights.add(rowHeight)
		}

		if (!valid) continue

		val totalHeightValue = heights.sumOf { it.value.toDouble() }.toFloat()
		var diff = kotlin.math.abs(totalHeightValue - maxHeightValue)

		if (partition.size > 1) {
			if (partition[0] > partition[1]) diff *= 1.2f
			if (partition.size > 2 && partition[1] > partition[2]) diff *= 1.2f
			if (partition.size > 3 && partition[2] > partition[3]) diff *= 1.2f
		}

		val minHeight = heights.minOfOrNull { it.value } ?: Float.MAX_VALUE
		if (minHeight < 110f) {
			diff *= 1.5f
		}

		if (optimal == null || diff < optimalDiff) {
			optimal = MessageGroupedLayoutAttempt(partition, heights)
			optimalDiff = diff
		}
	}

	return optimal ?: run {
		val fallbackRows = calculateAlbumRows(total)
		var cursor = 0
		val fallbackHeights = fallbackRows.map { count ->
			var sum = 0f
			for (i in 0 until count) {
				sum += ratios[cursor + i]
			}
			cursor += count
			(availableWidth / (if (sum > 0f) sum else 1f)).coerceIn(110.dp, 260.dp)
		}
		MessageGroupedLayoutAttempt(fallbackRows, fallbackHeights)
	}
}

private fun calculateAlbumRows(total: Int): List<Int> = when (total) {
	1 -> listOf(1)
	2 -> listOf(2)
	3 -> listOf(1, 2)
	4 -> listOf(2, 2)
	5 -> listOf(2, 3)
	6 -> listOf(3, 3)
	7 -> listOf(1, 3, 3)
	8 -> listOf(2, 3, 3)
	9 -> listOf(3, 3, 3)
	10 -> listOf(1, 3, 3, 3)
	else -> {
		val rows = mutableListOf<Int>()
		var rem = total
		while (rem > 0) {
			val take = if (rem >= 3) 3 else rem
			rows.add(take)
			rem -= take
		}
		rows
	}
}

@Composable
private fun AlbumGrid(
	items: List<ChatImageItem>,
	hiddenImageIndex: Int?,
	hiddenImageRevealProgress: Float,
	isSelectionMode: Boolean,
	selectedImageIds: Set<Long>,
	onToggleImageSelection: ((Long) -> Unit)?,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	onImageClick: (Int, Rect?, Painter?) -> Unit,
	onImageBoundsChanged: ((Int, Rect, Painter?) -> Unit)?,
	onCancelUpload: ((String) -> Unit)?,
	onRetryUpload: ((String) -> Unit)?,
	uploadProgressByClientMessageId: Map<String, Float>? = null,
	downloadStatesByUrl: Map<String, MediaDownloadState> = emptyMap(),
	onDownloadImageClick: ((String) -> Unit)? = null,
	onCancelDownloadImage: ((String) -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	BoxWithConstraints(modifier = modifier) {
		val availableWidth = if (maxWidth > 0.dp && maxWidth < Dp.Infinity) maxWidth else 280.dp
		val layoutAttempt = remember(items, availableWidth) {
			computeOptimalLayout(items, availableWidth)
		}

		val rowCounts = layoutAttempt.lineCounts
		val rowHeights = layoutAttempt.heights
		val numRows = rowCounts.size

		var currentIndex = 0
		Column(modifier = Modifier.fillMaxWidth()) {
			rowCounts.forEachIndexed { rowIndex, count ->
				if (rowIndex > 0) {
					Spacer(Modifier.height(2.dp))
				}
				val rowHeight = rowHeights.getOrElse(rowIndex) { 130.dp }
				val isTopRow = (rowIndex == 0)
				val isBottomRow = (rowIndex == numRows - 1)

				Row(modifier = Modifier.fillMaxWidth()) {
					for (i in 0 until count) {
						if (i > 0) {
							Spacer(Modifier.width(2.dp))
						}
						val itemIndex = currentIndex++
						val item = items[itemIndex]

						val isFirstCol = (i == 0)
						val isLastCol = (i == count - 1)

						// Outer borders keep 16dp radius, inner image dividers 0dp with 2dp spacing
						val cellShape = RoundedCornerShape(
							topStart = if (isTopRow && isFirstCol) 16.dp else 0.dp,
							topEnd = if (isTopRow && isLastCol) 16.dp else 0.dp,
							bottomStart = if (isBottomRow && isFirstCol) 16.dp else 0.dp,
							bottomEnd = if (isBottomRow && isLastCol) 16.dp else 0.dp,
						)

						val itemRatio = run {
							val r = if (item.width > 0 && item.height > 0) item.width.toFloat() / item.height.toFloat() else 1f
							if (r.isNaN() || r <= 0f) 1f else r.coerceIn(0.4f, 2.5f)
						}
						val cellKey = item.clientMessageId?.takeIf { it.isNotBlank() }
							?: item.remoteUrl?.takeIf { it.isNotBlank() }
							?: item.localPath?.takeIf { it.isNotBlank() }
							?: item.id.takeIf { it > 0L }?.toString()
							?: itemIndex.toString()

						key(cellKey) {
							SingleImageCell(
								item = item,
								index = itemIndex,
								height = rowHeight,
								hidden = hiddenImageIndex == itemIndex,
								revealProgress = hiddenImageRevealProgress,
								isSelectionMode = isSelectionMode,
								isSelected = item.id in selectedImageIds,
								onToggleSelection = onToggleImageSelection,
								onClick = onClick,
								onLongClick = onLongClick,
								onImageClick = onImageClick,
								onImageBoundsChanged = onImageBoundsChanged,
								onCancelUpload = onCancelUpload,
								onRetryUpload = onRetryUpload,
								uploadProgress = item.clientMessageId?.let { uploadProgressByClientMessageId?.get(it) },
								downloadState = item.remoteUrl?.let { downloadStatesByUrl[it] },
								onDownloadClick = onDownloadImageClick,
								onCancelDownload = onCancelDownloadImage,
								shape = cellShape,
								modifier = Modifier
									.weight(itemRatio)
									.clip(cellShape),
							)
						}
					}
				}
			}
		}
	}
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SingleImageCell(
	item: ChatImageItem,
	index: Int,
	height: Dp,
	hidden: Boolean,
	revealProgress: Float,
	isSelectionMode: Boolean,
	isSelected: Boolean,
	onToggleSelection: ((Long) -> Unit)?,
	onClick: () -> Unit,
	onLongClick: () -> Unit,
	onImageClick: (Int, Rect?, Painter?) -> Unit,
	onImageBoundsChanged: ((Int, Rect, Painter?) -> Unit)?,
	onCancelUpload: ((String) -> Unit)?,
	onRetryUpload: ((String) -> Unit)?,
	uploadProgress: Float? = null,
	downloadState: MediaDownloadState? = null,
	onDownloadClick: ((String) -> Unit)? = null,
	onCancelDownload: ((String) -> Unit)? = null,
	shape: Shape = RectangleShape,
	modifier: Modifier = Modifier,
) {
	val cellKey = item.clientMessageId?.takeIf { it.isNotBlank() }
		?: item.remoteUrl?.takeIf { it.isNotBlank() }
		?: item.localPath?.takeIf { it.isNotBlank() }
		?: item.id.takeIf { it > 0L }?.toString()
		?: index.toString()
	var painter by remember(cellKey) { mutableStateOf<Painter?>(null) }
	var bounds by remember { mutableStateOf<Rect?>(null) }

	val currentOnClick by rememberUpdatedState(onClick)
	val currentOnLongClick by rememberUpdatedState(onLongClick)
	val currentOnImageClick by rememberUpdatedState(onImageClick)
	val currentOnImageBoundsChanged by rememberUpdatedState(onImageBoundsChanged)

	Box(
		modifier = modifier
			.height(height)
			.graphicsLayer { alpha = if (hidden) revealProgress else 1f }
			.combinedClickable(
				onClick = {
					if (isSelectionMode) {
						if (onToggleSelection != null && item.id > 0L) {
							onToggleSelection(item.id)
						} else {
							currentOnClick()
						}
					} else {
						val remoteUrl = item.remoteUrl
						val downloadedPath = (downloadState as? MediaDownloadState.Completed)?.localPath?.takeIf { it.isNotBlank() }
						val initialPath = item.localPath?.takeIf { it.isNotBlank() }
						val effectiveLocal = downloadedPath ?: initialPath
						val isFileReady = effectiveLocal != null && platformChatFileExists(effectiveLocal)
						val isImageReady = isFileReady || painter != null
						if (!isImageReady && !remoteUrl.isNullOrBlank() && downloadState !is MediaDownloadState.Downloading) {
							onDownloadClick?.invoke(remoteUrl)
						} else {
							currentOnImageClick(index, bounds, painter)
						}
					}
				},
				onLongClick = {
					currentOnLongClick()
				},
			)
			.onGloballyPositioned { coordinates ->
				val b = coordinates.boundsInWindow()
				bounds = b
				currentOnImageBoundsChanged?.invoke(index, b, painter)
			}
	) {
		val remoteUrl = item.remoteUrl
		val downloadedLocalPath = (downloadState as? MediaDownloadState.Completed)?.localPath?.takeIf { it.isNotBlank() }
		val initialLocalPath = item.localPath?.takeIf { it.isNotBlank() }
		val fullSource = resolveChatImageSource(
			localPath = initialLocalPath,
			downloadedPath = downloadedLocalPath,
			remoteUrl = remoteUrl,
			thumbnailUrl = item.thumbnailUrl,
		)
		val hasLocalFile = remember(downloadedLocalPath, initialLocalPath) {
			(downloadedLocalPath ?: initialLocalPath)?.let(::platformChatFileExists) == true
		}
		val isFullReady = hasLocalFile || downloadState is MediaDownloadState.Completed

		var activeModel by remember(fullSource) { mutableStateOf(fullSource) }
		val displayTarget = activeModel ?: remoteUrl

		if (displayTarget != null || !item.thumbnailUrl.isNullOrBlank()) {
			ProgressiveImage(
				thumbUrl = item.thumbnailUrl,
				targetUrl = displayTarget,
				contentDescription = null,
				contentScale = ContentScale.Crop,
				modifier = Modifier.fillMaxSize(),
				onPainterChanged = { p ->
					if (p != null) {
						painter = p
						bounds?.let { b -> currentOnImageBoundsChanged?.invoke(index, b, p) }
					}
				},
				onError = {
					if (activeModel != remoteUrl && !remoteUrl.isNullOrBlank()) {
						activeModel = remoteUrl
					} else if (painter == null) {
						painter = null
					}
				}
			)
		}

		// 3. Индикатор загрузки / скачивания с отменой поверх thumb (Telegram Parity)
		if (item.uploadStatus == VideoCircleUploadStatus.Uploading) {
			MediaTransferOverlay(
				state = MediaTransferUiState(
					phase = MediaTransferPhase.Uploading,
					progress = uploadProgress,
					actionIcon = MediaTransferActionIcon.Cancel,
					canCancel = true,
					showDim = false,
					showBlur = false,
				),
				shape = shape,
				controlSize = 40.dp,
				onCancel = {
					item.clientMessageId?.let { onCancelUpload?.invoke(it) }
				},
			)
		} else if (item.uploadStatus == VideoCircleUploadStatus.Failed) {
			MediaTransferOverlay(
				state = MediaTransferUiState(
					phase = MediaTransferPhase.Failed,
					actionIcon = MediaTransferActionIcon.Retry,
					canRetry = true,
					showDim = false,
					showBlur = false,
				),
				shape = shape,
				controlSize = 40.dp,
				onRetry = {
					item.clientMessageId?.let { onRetryUpload?.invoke(it) }
				},
			)
		} else if (downloadState is MediaDownloadState.Downloading) {
			MediaTransferOverlay(
				state = MediaTransferUiState(
					phase = MediaTransferPhase.Downloading,
					progress = downloadState.progress,
					actionIcon = MediaTransferActionIcon.Cancel,
					canCancel = true,
					showDim = false,
					showBlur = false,
				),
				shape = shape,
				controlSize = 48.dp,
				onCancel = {
					remoteUrl?.let { onCancelDownload?.invoke(it) }
				},
			)
		} else if (downloadState is MediaDownloadState.Failed) {
			MediaTransferOverlay(
				state = MediaTransferUiState(
					phase = MediaTransferPhase.Failed,
					actionIcon = MediaTransferActionIcon.Retry,
					canRetry = true,
					showDim = false,
					showBlur = false,
				),
				shape = shape,
				controlSize = 48.dp,
				onRetry = {
					remoteUrl?.let { onDownloadClick?.invoke(it) }
				},
			)
		} else if (!isFullReady && !remoteUrl.isNullOrBlank()) {
			val sizeStr = formatMediaFileSize(item.sizeBytes)
			MediaTransferOverlay(
				state = MediaTransferUiState(
					phase = MediaTransferPhase.Idle,
					actionIcon = MediaTransferActionIcon.Download,
					showDim = false,
					showBlur = false,
					sizeText = sizeStr.takeIf { it.isNotBlank() },
				),
				shape = shape,
				controlSize = 48.dp,
				onClick = {
					remoteUrl.let { onDownloadClick?.invoke(it) }
				},
			)
		}

		if (isSelectionMode) {
			val checkBgColor = if (isSelected) Color(0xFF7A73F8) else Color.Black.copy(alpha = 0.35f)
			val checkBorderColor = if (isSelected) Color.Transparent else Color.White.copy(alpha = 0.85f)

			Box(
				modifier = Modifier
					.align(Alignment.TopEnd)
					.padding(4.dp)
					.size(28.dp)
					.background(checkBgColor, CircleShape)
					.border(1.5.dp, checkBorderColor, CircleShape)
					.clickable {
						if (item.id > 0L && onToggleSelection != null) {
							onToggleSelection(item.id)
						}
					},
				contentAlignment = Alignment.Center
			) {
				if (isSelected) {
					Icon(
						imageVector = Icons.Default.Check,
						contentDescription = null,
						tint = Color.White,
						modifier = Modifier.size(16.dp)
					)
				}
			}
		}
	}
}

private fun formatMediaFileSize(bytes: Long): String {
	if (bytes <= 0L) return ""
	return if (bytes < 1024L * 1024L) {
		val kb = (bytes / 1024L).coerceAtLeast(1L)
		"$kb КБ"
	} else {
		val mb10 = (bytes * 10L / (1024L * 1024L))
		val intPart = mb10 / 10L
		val fracPart = mb10 % 10L
		"$intPart.$fracPart МБ"
	}
}

