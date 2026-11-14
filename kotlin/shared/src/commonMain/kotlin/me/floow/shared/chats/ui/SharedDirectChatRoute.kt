package me.floow.shared.chats.ui

import androidx.compose.foundation.layout.Row
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import me.floow.shared.chats.model.ChatDeliveryState
import me.floow.shared.chats.model.ChatMessageContent
import me.floow.shared.chats.model.ChatMessageItemModel
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.shared.chats.model.withSavedMessagesSemantics
import me.floow.shared.chats.uilogic.direct.DeleteMessageResult
import me.floow.shared.chats.uilogic.direct.DirectChatInitialRequest
import me.floow.shared.chats.uilogic.direct.DirectChatScreenState
import me.floow.shared.chats.uilogic.direct.DirectChatStateHolder
import me.floow.domain.cache.DirectChatsLocalStore
import org.koin.compose.koinInject
import me.floow.uikit.chat.input.ChatInputController
import me.floow.uikit.chat.model.ChatContextMenuAction
import me.floow.uikit.chat.model.ChatSelectionState
import me.floow.uikit.chat.model.ChatViewportSnapshot
import me.floow.uikit.chat.model.VideoCircleOutMessage
import me.floow.uikit.chat.model.ChatDraftImage
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import me.floow.shared.chats.uilogic.direct.VideoRecordingStateHolder
import me.floow.shared.chats.media.ChatMediaPermissionType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.floow.shared.chats.media.PlatformCameraPreview
import me.floow.shared.chats.media.rememberChatMediaPermissionRequester
import me.floow.shared.chats.media.rememberPlatformVideoRecorder
import me.floow.shared.chats.media.rememberPlatformVoiceRecorder
import me.floow.shared.chats.media.attachment.ChatAttachmentPickerModal
import me.floow.shared.media.ui.NativeCameraRecordingPreview
import me.floow.shared.media.ui.NativeVideoCirclePlayer
import me.floow.shared.media.voice.createVoicePlayer
import me.floow.uikit.chat.components.ChatAlbumBubble
import me.floow.uikit.chat.components.resolveViewerImageSource
import me.floow.uikit.chat.components.VideoRecordingOverlay
import me.floow.uikit.chat.components.VoiceMessageBubble
import me.floow.uikit.chat.components.media.MediaTransferOverlay
import me.floow.uikit.chat.model.ChatRecordButtonMode
import me.floow.uikit.chat.model.ImageMessage
import me.floow.uikit.chat.model.MediaTransferActionIcon
import me.floow.uikit.chat.model.MediaTransferPhase
import me.floow.uikit.chat.model.MediaTransferUiState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import me.floow.uikit.chat.model.VideoCircleUploadStatus
import me.floow.uikit.chat.model.VideoRecordingMode
import me.floow.uikit.chat.model.VideoRecordingState
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerAction
import me.floow.uikit.components.media.viewer2.FullscreenImageViewerModel
import me.floow.uikit.components.media.viewer2.HostedFullscreenImageViewer
import me.floow.uikit.components.media.viewer2.FullscreenImageViewer
import me.floow.uikit.components.media.viewer2.SharedImageOrigin
import me.floow.uikit.components.media.viewer2.SourceImageScaleMode
import me.floow.uikit.components.media.viewer2.ViewerPhase
import me.floow.uikit.components.media.viewer2.reduce
import me.floow.uikit.components.media.viewer2.rememberFullscreenImageViewerState
import me.floow.uikit.components.media.viewer2.viewerSourceRevealProgress

@Composable
fun SharedDirectChatRoute(
	stateHolder: DirectChatStateHolder,
	initialRequest: DirectChatInitialRequest,
	onBackClick: () -> Unit,
	onShowMessage: (String) -> Unit,
	onCopyText: (String) -> Unit = {},
	onReplyCreatedFeedback: () -> Unit = {},
	onHeaderClick: (() -> Unit)? = null,
	emojiPanel: @Composable (ChatInputController) -> Unit = { controller ->
		me.floow.shared.chats.emoji.FlowEmojiPanel(
			onEmojiPicked = { emoji ->
				val current = controller.textFieldValue
				val text = current.text
				val selection = current.selection
				val newText = text.replaceRange(selection.min, selection.max, emoji)
				val newCursor = selection.min + emoji.length
				controller.onTextFieldValueChange(
					current.copy(
						text = newText,
						selection = androidx.compose.ui.text.TextRange(newCursor),
						composition = null,
					)
				)
			}
		)
	},
	videoRecordingOverlay: (@Composable (Int, Rect?, Modifier) -> Unit)? = null,
	recordingState: me.floow.uikit.chat.model.VideoRecordingState? = null,
	onRecordButtonPress: (() -> Unit)? = null,
	onRecordButtonRelease: (() -> Unit)? = null,
	onRecordSwipeUp: (() -> Unit)? = null,
	onRecordSwipeLeft: (() -> Unit)? = null,
	onRecordDrag: ((Float, Float) -> Unit)? = null,
	onRecordStopClick: (() -> Unit)? = null,
	onRecordSendClick: (() -> Unit)? = null,
	onViewportSnapshotChanged: (ChatViewportSnapshot) -> Unit = {},
	videoCircleInteractionActive: Boolean = false,
	videoCircleContent: (@Composable (me.floow.uikit.chat.model.VideoCircleOutMessage, Boolean, () -> Unit) -> Unit)? = null,
	voiceContent: (@Composable (me.floow.uikit.chat.model.VoiceMessage, Boolean, () -> Unit) -> Unit)? = null,
	imageContent: (@Composable (
		message: me.floow.uikit.chat.model.ImageMessage,
		isSelectionMode: Boolean,
		selectedImageIds: Set<Long>,
		onToggleImageSelection: (Long) -> Unit,
		onClick: () -> Unit,
		onLongClick: () -> Unit,
		onRetry: (String) -> Unit,
		onCancel: (String) -> Unit,
	) -> Unit)? = null,
	onImageRetry: ((String) -> Unit)? = null,
	onImageCancel: ((String) -> Unit)? = null,
	onAttachClick: (() -> Unit)? = null,
	recordButtonMode: me.floow.uikit.chat.model.ChatRecordButtonMode? = null,
	onRecordButtonModeToggle: (() -> Unit)? = null,
	voiceDraft: me.floow.uikit.chat.model.VoiceDraftUiState? = null,
	onVoiceDraftPlayPause: () -> Unit = {},
	onVoiceDraftSeek: (Float) -> Unit = {},
	onVoiceDraftDelete: () -> Unit = {},
	onVoiceDraftSend: () -> Unit = {},
	voiceRecordingState: me.floow.uikit.chat.model.VoiceRecordingInputState = me.floow.uikit.chat.model.VoiceRecordingInputState(),
	imageDrafts: List<ChatDraftImage> = emptyList(),
	onImageDraftRemove: (String) -> Unit = {},
	onImageDraftReorder: (List<String>) -> Unit = {},
	onImageDraftSend: () -> Unit = {},
	bubbleBoundsByMessageKey: androidx.compose.runtime.snapshots.SnapshotStateMap<String, androidx.compose.ui.geometry.Rect> = androidx.compose.runtime.mutableStateMapOf(),
	modifier: Modifier = Modifier,
) {
	val state by stateHolder.state.collectAsState()
	val outboxTasks by stateHolder.mediaOutboxTasks.collectAsState()
	val mediaDownloadStates by stateHolder.mediaDownloadStates.collectAsState()
	val strings = rememberSharedChatStrings()
	val scope = rememberCoroutineScope()
	val snackbarHostState = remember { SnackbarHostState() }
	val voicePlayer = remember { createVoicePlayer() }
	val voicePlaybackState by voicePlayer.state.collectAsState()
	val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
	DisposableEffect(voicePlayer) {
		onDispose {
			voicePlayer.release()
		}
	}
	val rowBoundsByMessageKey = remember(stateHolder) { androidx.compose.runtime.mutableStateMapOf<String, Rect>() }
	var jumpRequest by remember(stateHolder) { mutableStateOf<SharedJumpRequest?>(null) }
	var activeContextMenuMessageId by remember(stateHolder) { mutableStateOf<Long?>(null) }
	var contextMenuFallbackAnchorRect by remember(stateHolder) { mutableStateOf<Rect?>(null) }
	var contextMenuFallbackHighlightRect by remember(stateHolder) { mutableStateOf<Rect?>(null) }
	var selectedMessageIds by remember(stateHolder) { mutableStateOf<Set<Long>>(emptySet()) }
	var pendingDeleteMessageId by remember(stateHolder) { mutableStateOf<Long?>(null) }
	var isDeleteSelectionConfirmationVisible by remember(stateHolder) { mutableStateOf(false) }

	val directChatsLocalStore: DirectChatsLocalStore = koinInject()
	val cachedConversation = remember(initialRequest.conversationId, initialRequest.peerUserId) {
		initialRequest.conversationId?.let { directChatsLocalStore.getCachedConversationSync(it) }
			?: directChatsLocalStore.getCachedConversationByPeerIdSync(initialRequest.peerUserId)
	}
	val initialAvatarThumb = cachedConversation?.peer?.avatarThumb

	val chatDraftKey = remember(initialRequest) {
		initialRequest.conversationId?.toString()?.takeIf { it != "0" }
			?: initialRequest.peerUserId.trim().takeIf(String::isNotEmpty)
			?: "draft_chat"
	}
	val initialMediaDraft = remember(chatDraftKey) {
		me.floow.shared.chats.media.ChatMediaDraftStore.getDraft(chatDraftKey)
	}

	var localImageDrafts by remember(chatDraftKey) {
		mutableStateOf<List<ChatDraftImage>>(initialMediaDraft.images)
	}
	val effectiveImageDrafts = if (imageDrafts.isNotEmpty()) imageDrafts else localImageDrafts

	var internalVoiceDraft by remember(chatDraftKey) {
		mutableStateOf<me.floow.uikit.chat.model.VoiceDraftUiState?>(initialMediaDraft.voiceDraft)
	}
	var currentDraftClip by remember(chatDraftKey) {
		mutableStateOf<me.floow.shared.chats.uilogic.direct.RecordedVoiceClip?>(initialMediaDraft.voiceClip)
	}
	val effectiveVoiceDraft = voiceDraft ?: internalVoiceDraft

	LaunchedEffect(chatDraftKey, effectiveImageDrafts) {
		me.floow.shared.chats.media.ChatMediaDraftStore.updateImages(chatDraftKey, effectiveImageDrafts)
	}
	LaunchedEffect(chatDraftKey, effectiveVoiceDraft, currentDraftClip) {
		me.floow.shared.chats.media.ChatMediaDraftStore.updateVoice(chatDraftKey, effectiveVoiceDraft, currentDraftClip)
	}

	val effectiveImageDraftRemove: (String) -> Unit = { id ->
		if (imageDrafts.isNotEmpty()) {
			onImageDraftRemove(id)
		} else {
			localImageDrafts = localImageDrafts.filterNot { it.id == id }
		}
	}

	val effectiveImageDraftReorder: (List<String>) -> Unit = { ids ->
		if (imageDrafts.isNotEmpty()) {
			onImageDraftReorder(ids)
		} else {
			val byId = localImageDrafts.associateBy { it.id }
			localImageDrafts = ids.mapNotNull { byId[it] }
		}
	}

	val effectiveImageDraftSend: () -> Unit = {
		if (imageDrafts.isNotEmpty()) {
			onImageDraftSend()
		} else {
			val currentDrafts = localImageDrafts
			if (currentDrafts.isNotEmpty()) {
				localImageDrafts = emptyList()
				me.floow.shared.chats.media.ChatMediaDraftStore.clear(chatDraftKey)
				val caption = stateHolder.state.value.inputValue().trim().takeIf(String::isNotEmpty)
				if (caption != null) {
					stateHolder.updateInput("")
				}

				// 1. Immediately create optimistic messages with local draft URIs -> 0ms delay!
				val initialDrafts = currentDrafts.mapIndexed { index, draft ->
					DirectChatStateHolder.LocalImageDraft(
						localPath = draft.uri,
						caption = if (index == 0) caption else null,
						width = 0,
						height = 0,
					)
				}
				val groupId = if (initialDrafts.size > 1) "album_${currentChatEpochMillis()}_${(1000..9999).random()}" else null
				val clientMessageIds = stateHolder.addLocalImages(items = initialDrafts, groupId = groupId, enqueueOutbox = false)

				// 2. Prepare media in parallel in background and enqueue to outbox as each finishes
				scope.launch(Dispatchers.Default) {
					val preparer = me.floow.shared.media.pipeline.createLocalMediaPreparer()
					currentDrafts.mapIndexed { index, draft ->
						val clientMessageId = clientMessageIds.getOrNull(index) ?: return@mapIndexed null
						async {
							val cleanDraftUri = draft.uri.removePrefix("file://")
							val prepared = runCatching {
								preparer.prepareMedia(draft.uri, me.floow.shared.media.pipeline.MediaUploadPolicy.CHAT_IMAGE)
							}.getOrNull()
							val preparedFile = (prepared as? me.floow.shared.media.pipeline.MediaPreparationResult.Success)?.media
							val finalPath = (preparedFile?.filePath ?: cleanDraftUri).removePrefix("file://")
							val finalWidth = preparedFile?.width ?: 0
							val finalHeight = preparedFile?.height ?: 0

							val microThumb = preparedFile?.microThumbnail
							withContext(Dispatchers.Main) {
								stateHolder.updateLocalImageSource(
									clientMessageId = clientMessageId,
									localPath = finalPath,
									width = finalWidth,
									height = finalHeight,
									thumbnailUrl = microThumb,
								)
							}

							stateHolder.enqueuePreparedImageOutbox(
								clientMessageId = clientMessageId,
								filePath = finalPath,
								width = finalWidth,
								height = finalHeight,
								caption = if (index == 0) caption else null,
								groupId = groupId,
								position = index,
								thumbnailUrl = microThumb,
							)
						}
					}.filterNotNull().awaitAll()
				}
			}
		}
	}

	val currentMessages = when (val currentState = state) {
		is DirectChatScreenState.HasData -> currentState.messages
		else -> emptyList()
	}
	val messagesById = remember(currentMessages) {
		currentMessages.associateBy(ChatMessageItemModel::id)
	}
	val contextMenuMessage = activeContextMenuMessageId?.let(messagesById::get)
	val contextMenuMessageKey = remember(contextMenuMessage) {
		contextMenuMessage?.uiMessageKey()
	}
	val liveContextMenuAnchorRect by remember(
		contextMenuMessage,
		contextMenuMessageKey,
		contextMenuFallbackAnchorRect,
		bubbleBoundsByMessageKey,
		rowBoundsByMessageKey,
	) {
		derivedStateOf {
			val imgGroupId = (contextMenuMessage?.content as? ChatMessageContent.Image)?.groupId?.takeIf(String::isNotBlank)
			val candidateKeys = buildList {
				contextMenuMessageKey?.let(::add)
				contextMenuMessage?.clientMessageId?.takeIf(String::isNotBlank)?.let { add("cmid_$it") }
				contextMenuMessage?.id?.let {
					add("msg_$it")
					add(it.toString())
				}
				if (imgGroupId != null) {
					bubbleBoundsByMessageKey.keys.firstOrNull { it.contains(imgGroupId) }?.let(::add)
					rowBoundsByMessageKey.keys.firstOrNull { it.contains(imgGroupId) }?.let(::add)
				}
			}
			candidateKeys.firstNotNullOfOrNull(bubbleBoundsByMessageKey::get)
				?: candidateKeys.firstNotNullOfOrNull(rowBoundsByMessageKey::get)
				?: contextMenuFallbackAnchorRect
		}
	}
	val liveContextMenuHighlightRect by remember(
		contextMenuMessage,
		contextMenuMessageKey,
		contextMenuFallbackHighlightRect,
		liveContextMenuAnchorRect,
		bubbleBoundsByMessageKey,
		rowBoundsByMessageKey,
	) {
		derivedStateOf {
			val imgGroupId = (contextMenuMessage?.content as? ChatMessageContent.Image)?.groupId?.takeIf(String::isNotBlank)
			val candidateKeys = buildList {
				contextMenuMessageKey?.let(::add)
				contextMenuMessage?.clientMessageId?.takeIf(String::isNotBlank)?.let { add("cmid_$it") }
				contextMenuMessage?.id?.let {
					add("msg_$it")
					add(it.toString())
				}
				if (imgGroupId != null) {
					bubbleBoundsByMessageKey.keys.firstOrNull { it.contains(imgGroupId) }?.let(::add)
					rowBoundsByMessageKey.keys.firstOrNull { it.contains(imgGroupId) }?.let(::add)
				}
			}
			candidateKeys.firstNotNullOfOrNull(bubbleBoundsByMessageKey::get)
				?: candidateKeys.firstNotNullOfOrNull(rowBoundsByMessageKey::get)
				?: contextMenuFallbackHighlightRect
				?: liveContextMenuAnchorRect
		}
	}
	val selectionMode = selectedMessageIds.isNotEmpty()
	val selectedMessages = remember(selectedMessageIds, messagesById) {
		selectedMessageIds.mapNotNull(messagesById::get)
	}
	val selectedCopyText = remember(selectedMessages) {
		selectedMessages
			.filterNot(ChatMessageItemModel::isDeleted)
			.map(ChatMessageItemModel::text)
			.filter(String::isNotBlank)
			.joinToString(separator = "\n")
	}
	val selectionState = remember(selectedMessageIds, selectedCopyText) {
		ChatSelectionState(
			selectedMessageIds = selectedMessageIds,
			selectedCount = selectedMessageIds.size,
			canCopy = selectedCopyText.isNotBlank(),
			canDelete = selectedMessageIds.isNotEmpty(),
			copyText = selectedCopyText,
		)
	}
	val contextMenuActions = remember(contextMenuMessage) {
		contextMenuMessage?.let(::resolveContextMenuActions).orEmpty()
	}
	fun clearContextMenuState() {
		activeContextMenuMessageId = null
		contextMenuFallbackAnchorRect = null
		contextMenuFallbackHighlightRect = null
	}
	fun clearSelectionUiState() {
		selectedMessageIds = emptySet()
		isDeleteSelectionConfirmationVisible = false
	}
	fun toggleMessageSelection(messageId: Long) {
		val clickedMsg = messagesById[messageId]
		val imgGroupId = (clickedMsg?.content as? ChatMessageContent.Image)?.groupId?.takeIf(String::isNotBlank)
		if (imgGroupId != null) {
			val albumIds = currentMessages.filter {
				(it.content as? ChatMessageContent.Image)?.groupId == imgGroupId
			}.map { it.id }.toSet()
			val allSelected = albumIds.isNotEmpty() && albumIds.all { it in selectedMessageIds }
			selectedMessageIds = if (allSelected) {
				selectedMessageIds - albumIds
			} else {
				selectedMessageIds + albumIds
			}
		} else {
			selectedMessageIds = selectedMessageIds.toggle(messageId)
		}
	}

	LaunchedEffect(stateHolder, initialRequest) {
		if (stateHolder.activeRequest != initialRequest) {
			stateHolder.load(initialRequest)
		}
	}

	DisposableEffect(stateHolder) {
		onDispose {
			stateHolder.dispose()
		}
	}

	LaunchedEffect(messagesById) {
		selectedMessageIds = selectedMessageIds.filter(messagesById::containsKey).toSet()
		if (activeContextMenuMessageId != null && activeContextMenuMessageId !in messagesById) {
			clearContextMenuState()
		}
		if (pendingDeleteMessageId != null && pendingDeleteMessageId !in messagesById) {
			pendingDeleteMessageId = null
		}
		if (selectedMessageIds.isEmpty()) {
			isDeleteSelectionConfirmationVisible = false
		}
	}

	LaunchedEffect(activeContextMenuMessageId, liveContextMenuAnchorRect, liveContextMenuHighlightRect) {
		if (activeContextMenuMessageId == null) return@LaunchedEffect
		if (liveContextMenuAnchorRect == null && liveContextMenuHighlightRect == null) {
			clearContextMenuState()
		}
	}

	LaunchedEffect(activeContextMenuMessageId) {
		if (activeContextMenuMessageId == null) return@LaunchedEffect
		withFrameNanos { }
		val hasLiveBounds = contextMenuMessageKey?.let { bubbleBoundsByMessageKey.containsKey(it) || rowBoundsByMessageKey.containsKey(it) } == true
		if (hasLiveBounds) {
			contextMenuFallbackAnchorRect = null
			contextMenuFallbackHighlightRect = null
		}
	}

	LaunchedEffect(stateHolder) {
		stateHolder.events.collectLatest { event ->
			when (event) {
				is DirectChatStateHolder.Event.ShowMessage -> {
					snackbarHostState.showSnackbar(event.message)
				}
				is DirectChatStateHolder.Event.JumpToMessage -> {
					val nextToken = (jumpRequest?.requestToken ?: 0L) + 1L
					jumpRequest = SharedJumpRequest(
						messageId = event.messageId,
						requestToken = nextToken,
					)
				}
			}
		}
	}

	val density = LocalDensity.current
	val bubbleCornerRadiusPx = with(density) { 16.dp.toPx() }

	val viewerState = rememberFullscreenImageViewerState()
	val viewerImagesState = remember { mutableStateOf<List<String>>(emptyList()) }
	var viewerImages by viewerImagesState
	val viewerOriginKeysState = remember { mutableStateOf<List<String>>(emptyList()) }
	var viewerOriginKeys by viewerOriginKeysState
	val liveOrigins = remember { androidx.compose.runtime.mutableStateMapOf<String, SharedImageOrigin>() }
	var activeViewerMessageKey by remember { mutableStateOf<String?>(null) }
	var hiddenImageIndex by remember { mutableStateOf<Int?>(null) }
	var activeOpeningPainter by remember { mutableStateOf<Painter?>(null) }

	val viewerModelProvider: () -> FullscreenImageViewerModel? = remember {
		{
			val images = viewerImagesState.value
			if (images.isEmpty()) null
			else FullscreenImageViewerModel(
				images = images,
				title = initialRequest.peerDisplayName,
				subtitleProvider = { page -> "${page + 1} из ${images.size}" },
				openingPainter = activeOpeningPainter,
				originForPage = { page -> viewerOriginKeysState.value.getOrNull(page)?.let(liveOrigins::get) },
				aspectRatioForPage = { page -> viewerOriginKeysState.value.getOrNull(page)?.let(liveOrigins::get)?.aspectRatio },
			)
		}
	}

	val hostedViewer = remember(viewerState, viewerModelProvider) {
		HostedFullscreenImageViewer(
			modelProvider = viewerModelProvider,
			state = viewerState,
			onAction = { action ->
				when (action) {
					is FullscreenImageViewerAction.RequestClose -> {
						val images = viewerImagesState.value
						val safePage = action.page?.coerceIn(0, (images.size - 1).coerceAtLeast(0))
						val origin = action.origin ?: safePage?.let { viewerOriginKeysState.value.getOrNull(it)?.let(liveOrigins::get) }
						hiddenImageIndex = safePage ?: hiddenImageIndex
						viewerState.reduce(
							if (safePage != null) action.copy(page = safePage, origin = origin) else action,
							images.size,
						)
					}
					FullscreenImageViewerAction.CloseAnimationFinished -> {
						viewerState.reduce(action, viewerImagesState.value.size)
						hiddenImageIndex = null
						viewerImages = emptyList()
						viewerOriginKeys = emptyList()
						activeViewerMessageKey = null
						activeOpeningPainter = null
						liveOrigins.clear()
					}
					is FullscreenImageViewerAction.PageChanged -> {
						val safePage = action.page.coerceIn(0, (viewerImagesState.value.size - 1).coerceAtLeast(0))
						hiddenImageIndex = safePage
						viewerState.reduce(action.copy(page = safePage), viewerImagesState.value.size)
					}
					else -> viewerState.reduce(action, viewerImagesState.value.size)
				}
			}
		)
	}

	val platformVideoRecorder = rememberPlatformVideoRecorder()
	val platformVoiceRecorder = rememberPlatformVoiceRecorder()

	val internalRecordingStateHolder = remember { VideoRecordingStateHolder() }
	val internalRecordingState by internalRecordingStateHolder.state.collectAsState()
	val effectiveRecordingState = recordingState ?: internalRecordingState

	DisposableEffect(internalRecordingStateHolder, platformVideoRecorder) {
		internalRecordingStateHolder.recorder = platformVideoRecorder
		onDispose {
			internalRecordingStateHolder.recorder = null
		}
	}

	var activeFlightState by remember { mutableStateOf<me.floow.uikit.chat.model.FlightState?>(null) }
	var isFlightLanded by remember { mutableStateOf(false) }

	DisposableEffect(internalRecordingStateHolder, stateHolder, platformVideoRecorder) {
		var pendingFlightClientMessageId: String? = null

		internalRecordingStateHolder.onInstantFlightTrigger = { placeholderClip, sourceBounds ->
			val previewBmp = platformVideoRecorder?.capturePreviewBitmap()
			if (previewBmp != null) {
				me.floow.uikit.chat.components.videocircle.storeCachedThumbnail(placeholderClip.path, previewBmp)
			}

			val uiKey = stateHolder.addLocalVideoCircle(placeholderClip, enqueueOutbox = false)
			val clientMessageId = uiKey.removePrefix("cmid_")
			pendingFlightClientMessageId = clientMessageId

			isFlightLanded = false
			activeFlightState = me.floow.uikit.chat.model.FlightState(
				messageUiKey = uiKey,
				sourceBounds = sourceBounds ?: effectiveRecordingState.flightSourceBounds,
				thumbnailPath = placeholderClip.thumbnailPath ?: placeholderClip.path,
				videoPath = placeholderClip.path,
				phase = me.floow.uikit.chat.model.FlightPhase.InFlight,
			)
		}

		internalRecordingStateHolder.onVideoFinalized = { finalClip ->
			val clientMessageId = pendingFlightClientMessageId
			if (clientMessageId != null) {
				stateHolder.updateLocalVideoCircleClip(clientMessageId, finalClip)
			}
			pendingFlightClientMessageId = null
		}

		internalRecordingStateHolder.onVideoFinalizeFailed = {
			val clientMessageId = pendingFlightClientMessageId
			if (clientMessageId != null) {
				stateHolder.markLocalVideoCircleFailed(clientMessageId)
			}
			pendingFlightClientMessageId = null
		}

		internalRecordingStateHolder.onVideoReadyForLocalInsertion = { clip, sourceBounds ->
			val uiKey = stateHolder.addLocalVideoCircle(clip)
			isFlightLanded = false
			activeFlightState = me.floow.uikit.chat.model.FlightState(
				messageUiKey = uiKey,
				sourceBounds = sourceBounds ?: effectiveRecordingState.flightSourceBounds,
				thumbnailPath = clip.thumbnailPath ?: clip.path,
				videoPath = clip.path,
				phase = me.floow.uikit.chat.model.FlightPhase.InFlight,
			)
		}

		onDispose {
			internalRecordingStateHolder.onInstantFlightTrigger = null
			internalRecordingStateHolder.onVideoFinalized = null
			internalRecordingStateHolder.onVideoFinalizeFailed = null
			internalRecordingStateHolder.onVideoReadyForLocalInsertion = null
		}
	}

	var internalRecordButtonMode by remember { mutableStateOf(ChatRecordButtonMode.Voice) }
	val effectiveRecordButtonMode = recordButtonMode ?: internalRecordButtonMode
	val effectiveRecordButtonModeToggle = onRecordButtonModeToggle ?: {
		val nextMode = if (internalRecordButtonMode == ChatRecordButtonMode.Video) {
			platformVideoRecorder?.stopCamera()
			ChatRecordButtonMode.Voice
		} else {
			ChatRecordButtonMode.Video
		}
		internalRecordButtonMode = nextMode
		if (nextMode == ChatRecordButtonMode.Video) {
			platformVideoRecorder?.prepareCamera()
		}
	}

	var internalVoiceRecordingState by remember { mutableStateOf(me.floow.uikit.chat.model.VoiceRecordingInputState()) }
	val effectiveVoiceRecordingState = if (voiceRecordingState.isRecording || voiceRecordingState.isLocked) {
		voiceRecordingState
	} else {
		internalVoiceRecordingState
	}

	LaunchedEffect(internalVoiceRecordingState.isRecording) {
		if (internalVoiceRecordingState.isRecording) {
			val startTime = currentChatEpochMillis()
			while (internalVoiceRecordingState.isRecording) {
				kotlinx.coroutines.delay(80)
				val elapsed = (currentChatEpochMillis() - startTime).coerceAtLeast(0L)
				internalVoiceRecordingState = internalVoiceRecordingState.copy(durationMs = elapsed)
			}
		}
	}

	val checkMediaPermission = rememberChatMediaPermissionRequester(
		onGranted = { type ->
			if (type == ChatMediaPermissionType.VIDEO) {
				platformVideoRecorder?.prepareCamera()
			}
		}
	)

	val effectiveRecordButtonPress = onRecordButtonPress ?: {
		if (effectiveRecordButtonMode == ChatRecordButtonMode.Voice) {
			if (checkMediaPermission(ChatMediaPermissionType.VOICE)) {
				voicePlayer.pause()
				val started = platformVoiceRecorder?.startRecording(
					onAudioLevel = { level ->
						internalVoiceRecordingState = internalVoiceRecordingState.copy(audioLevel = level)
					},
					onMaxDuration = {
						val wasLocked = internalVoiceRecordingState.isLocked
						val clip = platformVoiceRecorder?.stopRecording()
						internalVoiceRecordingState = me.floow.uikit.chat.model.VoiceRecordingInputState()
						if (clip != null) {
							if (wasLocked) {
								currentDraftClip = clip
								internalVoiceDraft = me.floow.uikit.chat.model.VoiceDraftUiState(
									localAudioPath = clip.filePath,
									durationMs = clip.durationMs,
									waveform = clip.waveform,
									isPlaying = false,
									currentPositionMs = 0L,
								)
							} else {
								stateHolder.addLocalVoice(clip)
							}
						}
					}
				) == true
				if (started) {
					haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
					internalVoiceRecordingState = me.floow.uikit.chat.model.VoiceRecordingInputState(isRecording = true)
				}
			}
		} else {
			if (checkMediaPermission(ChatMediaPermissionType.VIDEO)) {
				internalRecordingStateHolder.onRecordButtonPress()
			}
		}
	}

	val effectiveRecordButtonRelease = onRecordButtonRelease ?: {
		if (effectiveRecordButtonMode == ChatRecordButtonMode.Voice) {
			if (internalVoiceRecordingState.isRecording && !internalVoiceRecordingState.isLocked) {
				val clip = platformVoiceRecorder?.stopRecording()
				internalVoiceRecordingState = me.floow.uikit.chat.model.VoiceRecordingInputState()
				if (clip != null) {
					haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
					stateHolder.addLocalVoice(clip)
				}
			}
		} else {
			internalRecordingStateHolder.onRecordButtonRelease()
		}
	}

	val effectiveRecordSwipeUp = onRecordSwipeUp ?: {
		haptics.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
		if (effectiveRecordButtonMode == ChatRecordButtonMode.Voice) {
			internalVoiceRecordingState = internalVoiceRecordingState.copy(isLocked = true)
		} else {
			internalRecordingStateHolder.onSwipeUpLock()
		}
	}

	val effectiveRecordSwipeLeft = onRecordSwipeLeft ?: {
		if (effectiveRecordButtonMode == ChatRecordButtonMode.Voice) {
			platformVoiceRecorder?.cancelRecording()
			internalVoiceRecordingState = me.floow.uikit.chat.model.VoiceRecordingInputState()
		} else {
			internalRecordingStateHolder.onSwipeLeft()
		}
	}

	val effectiveRecordDrag = onRecordDrag ?: { x, y ->
		if (effectiveRecordButtonMode == ChatRecordButtonMode.Voice) {
			val clampedX = if (x > 0f) 0f else x
			val clampedY = if (y > 0f) 0f else y
			internalVoiceRecordingState = internalVoiceRecordingState.copy(
				dragOffsetX = clampedX,
				dragOffsetY = clampedY,
			)
			if (-clampedX >= 220f) {
				platformVoiceRecorder?.cancelRecording()
				internalVoiceRecordingState = me.floow.uikit.chat.model.VoiceRecordingInputState()
			} else if (-clampedY >= 180f && !internalVoiceRecordingState.isLocked) {
				internalVoiceRecordingState = internalVoiceRecordingState.copy(
					isLocked = true,
					dragOffsetX = 0f,
					dragOffsetY = -180f,
				)
			}
		} else {
			internalRecordingStateHolder.onRecordDrag(x, y)
		}
	}

	val effectiveRecordStopClick = onRecordStopClick ?: {
		if (effectiveRecordButtonMode == ChatRecordButtonMode.Voice) {
			val clip = platformVoiceRecorder?.stopRecording()
			internalVoiceRecordingState = me.floow.uikit.chat.model.VoiceRecordingInputState()
			if (clip != null) {
				currentDraftClip = clip
				internalVoiceDraft = me.floow.uikit.chat.model.VoiceDraftUiState(
					localAudioPath = clip.filePath,
					durationMs = clip.durationMs,
					waveform = clip.waveform,
					isPlaying = false,
					currentPositionMs = 0L,
				)
			}
		} else {
			internalRecordingStateHolder.pauseRecording()
		}
	}

	val effectiveRecordSendClick = onRecordSendClick ?: {
		if (effectiveRecordButtonMode == ChatRecordButtonMode.Voice) {
			val clip = platformVoiceRecorder?.stopRecording()
			internalVoiceRecordingState = me.floow.uikit.chat.model.VoiceRecordingInputState()
			if (clip != null) {
				stateHolder.addLocalVoice(clip)
			}
		} else {
			internalRecordingStateHolder.sendRecordedVideo()
		}
	}

	val effectiveVoiceDraftSend: () -> Unit = {
		if (internalVoiceRecordingState.isLocked) {
			effectiveRecordSendClick()
		} else {
			onVoiceDraftSend()
		}
	}

	val isBackHandlerEnabled = viewerState.phase != ViewerPhase.Closed ||
		isDeleteSelectionConfirmationVisible ||
		pendingDeleteMessageId != null ||
		activeContextMenuMessageId != null ||
		selectionMode

	PlatformBackHandler(enabled = isBackHandlerEnabled) {
		when {
			viewerState.phase != ViewerPhase.Closed -> hostedViewer.onAction(FullscreenImageViewerAction.RequestClose())
			activeContextMenuMessageId != null -> clearContextMenuState()
			selectionMode -> clearSelectionUiState()
			else -> onBackClick()
		}
	}

	val effectiveVoiceContent: @Composable (me.floow.uikit.chat.model.VoiceMessage, Boolean, () -> Unit) -> Unit = voiceContent ?: { message, isSelectionMode, onLongClick ->
		val isThisMessageActive = voicePlaybackState.activeMessageId == message.id || voicePlaybackState.activeUiKey == message.uiKey
		val clientMessageId = message.clientMessageId
		val voiceTask = clientMessageId?.let { outboxTasks[it] }
		VoiceMessageBubble(
			message = message,
			isPlaying = isThisMessageActive && voicePlaybackState.isPlaying,
			currentPositionMs = if (isThisMessageActive) voicePlaybackState.currentPositionMs else 0L,
			speed = voicePlaybackState.speed,
			onPlayPauseClick = {
				val src = message.playableSource.orEmpty()
				voicePlayer.play(
					messageId = message.id,
					urlOrPath = src,
					uiKey = message.uiKey,
					durationMs = message.durationMs,
				)
			},
			onSeek = { fraction ->
				val pos = (fraction * message.durationMs).toLong()
				voicePlayer.seekTo(pos)
			},
			onToggleSpeed = { voicePlayer.toggleSpeed() },
			isBuffering = isThisMessageActive && voicePlaybackState.isBuffering,
			uploadProgress = voiceTask?.progress,
			onCancelUpload = clientMessageId?.let { id -> { stateHolder.cancelLocalVoice(id) } },
			onRetryClick = { clientMessageId?.let { stateHolder.retryMediaMessage(it) } },
		)
	}

	val activeVideoCircle by stateHolder.mediaController.activeVideoCircle.collectAsState()

	val effectiveVideoCircleContent: @Composable (VideoCircleOutMessage, Boolean, () -> Unit) -> Unit = videoCircleContent ?: { message, isSelectionMode, onLongClick ->
		val clientMessageId = message.clientMessageId
		val circleTask = clientMessageId?.let { outboxTasks[it] }
		val isUploading = message.uploadStatus == VideoCircleUploadStatus.Uploading || message.uploadStatus == VideoCircleUploadStatus.Pending
		val isFailed = message.uploadStatus == VideoCircleUploadStatus.Failed
		val playbackUiState = stateHolder.mediaController.resolveVideoCircleUiState(
			uiKey = message.uiKey,
			isUploading = isUploading,
			isFailed = isFailed,
		)
		val flightKey = activeFlightState?.messageUiKey
		val isThisMessageInFlight = (flightKey == message.uiKey || (clientMessageId != null && flightKey == "cmid_$clientMessageId")) && !isFlightLanded
		val flightBubbleAlpha = if (isThisMessageInFlight) 0f else 1f
		val bubbleSize by animateDpAsState(
			targetValue = if (playbackUiState.isExpanded) 252.dp else 228.dp,
			animationSpec = tween(durationMillis = 180),
			label = "video_circle_bubble_size",
		)
		Box(
			modifier = Modifier
				.size(bubbleSize)
				.graphicsLayer { alpha = flightBubbleAlpha }
				.clip(CircleShape),
			contentAlignment = Alignment.Center,
		) {
			NativeVideoCirclePlayer(
				videoUrl = message.playableSource.orEmpty(),
				thumbnailUrl = message.thumbnailPath ?: message.remoteThumbnailUrl,
				playbackUiState = playbackUiState,
				onClick = {
					if (isSelectionMode) {
						onLongClick()
					} else {
						stateHolder.mediaController.toggleVideoCircle(message.uiKey, message.id)
					}
				},
				onSeek = { nextProg ->
					stateHolder.mediaController.updateVideoCircleProgress(nextProg)
				},
				onPlaybackEnded = {
					stateHolder.mediaController.clearActiveVideoCircle()
				},
				modifier = Modifier.fillMaxSize(),
			)
			if (isUploading) {
				MediaTransferOverlay(
					state = MediaTransferUiState(
						phase = MediaTransferPhase.Uploading,
						progress = circleTask?.progress,
						actionIcon = MediaTransferActionIcon.Cancel,
						canCancel = true,
						showDim = true,
					),
					shape = CircleShape,
					controlSize = 64.dp,
					onCancel = { clientMessageId?.let { stateHolder.cancelLocalVideoCircle(it) } },
				)
			} else if (isFailed) {
				MediaTransferOverlay(
					state = MediaTransferUiState(
						phase = MediaTransferPhase.Failed,
						actionIcon = MediaTransferActionIcon.Retry,
						canRetry = true,
						showDim = true,
					),
					shape = CircleShape,
					controlSize = 64.dp,
					onRetry = { clientMessageId?.let { stateHolder.retryMediaMessage(it) } },
				)
			}
		}
	}

	val effectiveVideoRecordingOverlay: @Composable (Int, Rect?, Modifier) -> Unit = videoRecordingOverlay ?: { bottomInset, controlBounds, overlayModifier ->
		if (effectiveRecordingState.mode != VideoRecordingMode.Idle) {
			VideoRecordingOverlay(
				state = effectiveRecordingState,
				bottomInsetPx = bottomInset,
				recordingControlBounds = controlBounds,
				modifier = overlayModifier,
				onStopClick = effectiveRecordStopClick,
				onSourceMeasured = { bounds -> internalRecordingStateHolder.updateFlightSource(bounds) },
				onSwitchCameraClick = { platformVideoRecorder?.switchCamera() },
				cameraPreview = {
					PlatformCameraPreview(
						recorder = platformVideoRecorder,
						modifier = Modifier.fillMaxSize()
					)
				},
			)
		}
	}

	val effectiveImageContent: @Composable (
		ImageMessage,
		Boolean,
		Set<Long>,
		(Long) -> Unit,
		() -> Unit,
		() -> Unit,
		(String) -> Unit,
		(String) -> Unit,
	) -> Unit = imageContent ?: { albumMessage, isSelectionMode, selectedImageIds, onToggle, onClick, onLongClick, onRetry, onCancel ->
		val allUrls = remember(albumMessage.images, mediaDownloadStates) {
			albumMessage.images.map { img ->
				val downloadedPath = (img.remoteUrl?.let { mediaDownloadStates[it] } as? me.floow.domain.media.MediaDownloadState.Completed)?.localPath
				resolveViewerImageSource(
					localPath = img.localPath,
					downloadedPath = downloadedPath,
					remoteUrl = img.remoteUrl
				)
			}
		}
		val originKeys = remember(albumMessage.uiKey, albumMessage.images.size) {
			albumMessage.images.indices.map { index ->
				"${albumMessage.uiKey}_img_$index"
			}
		}
		val isCurrentMessageActive = activeViewerMessageKey == albumMessage.uiKey
		val activeHiddenIndex = if (isCurrentMessageActive) hiddenImageIndex else null
		val revealProgress = if (isCurrentMessageActive && activeHiddenIndex != null) {
			viewerSourceRevealProgress(viewerState.phase, viewerState.transitionProgress)
		} else {
			1f
		}

		ChatAlbumBubble(
			message = albumMessage,
			isSelectionMode = isSelectionMode,
			selectedImageIds = selectedImageIds,
			onClick = onClick,
			onLongClick = onLongClick,
			onToggleImageSelection = onToggle,
			onCancelUpload = onCancel,
			onRetryUpload = onRetry,
			hiddenImageIndex = activeHiddenIndex,
			hiddenImageRevealProgress = revealProgress,
			uploadProgressByClientMessageId = remember(outboxTasks) {
				outboxTasks.mapValues { it.value.progress }
			},
			downloadStatesByUrl = mediaDownloadStates,
			onDownloadImageClick = { url -> stateHolder.downloadImage(url) },
			onCancelDownloadImage = { url -> stateHolder.cancelImageDownload(url) },
			onImageClick = { index, bounds, painter ->
				val item = albumMessage.images.getOrNull(index)
				when (item?.uploadStatus) {
					VideoCircleUploadStatus.Failed -> item.clientMessageId?.let(onRetry)
					VideoCircleUploadStatus.Uploading -> item.clientMessageId?.let(onCancel)
					else -> Unit
				}
				val isUploadedOrLocal = item?.uploadStatus == VideoCircleUploadStatus.Uploaded ||
					!item?.localPath.isNullOrBlank() ||
					!item?.remoteUrl.isNullOrBlank()
				if (isUploadedOrLocal && allUrls.isNotEmpty() && index in allUrls.indices) {
					activeViewerMessageKey = albumMessage.uiKey
					viewerImages = allUrls
					viewerOriginKeys = originKeys
					activeOpeningPainter = painter
					val imageAspect = resolveChatImageAspect(item, painter, bounds)
					bounds?.let { b ->
						liveOrigins[originKeys[index]] = SharedImageOrigin(
							sourceKey = originKeys[index],
							rectInWindow = b,
							aspectRatio = imageAspect,
							cornerRadiusPx = bubbleCornerRadiusPx,
							sourceScaleMode = SourceImageScaleMode.Crop,
						)
					}
					hiddenImageIndex = index
					viewerState.reduce(
						FullscreenImageViewerAction.Open(
							page = index,
							origin = liveOrigins[originKeys[index]],
						),
						allUrls.size,
					)
				}
			},
			onImageBoundsChanged = { index, bounds, painter ->
				if (index in originKeys.indices) {
					val item = albumMessage.images.getOrNull(index)
					val imageAspect = resolveChatImageAspect(item, painter, bounds)
					liveOrigins[originKeys[index]] = SharedImageOrigin(
						sourceKey = originKeys[index],
						rectInWindow = bounds,
						aspectRatio = imageAspect,
						cornerRadiusPx = bubbleCornerRadiusPx,
						sourceScaleMode = SourceImageScaleMode.Crop,
					)
				}
			},
		)
	}

	var isAttachmentPickerVisible by remember { mutableStateOf(false) }
	val effectiveOnAttachClick = onAttachClick ?: { isAttachmentPickerVisible = true }
	val effectiveOnImageRetry: (String) -> Unit = onImageRetry ?: { id: String -> stateHolder.retryMediaMessage(id); Unit }
	val effectiveOnImageCancel: (String) -> Unit = onImageCancel ?: { id: String -> stateHolder.cancelLocalImage(id); Unit }

	SharedDirectChatScreen(
		state = state,
		initialHeader = initialRequest.toInitialHeader(initialAvatarThumb),
		selectionState = selectionState,
		contextMenuActions = contextMenuActions,
		resolveContextMenuActions = { messageId ->
			messagesById[messageId]?.let(::resolveContextMenuActions).orEmpty()
		},
		jumpRequest = jumpRequest,
		onBackClick = onBackClick,
		onHeaderClick = onHeaderClick,
		onInputChange = stateHolder::updateInput,
		onSendClick = if (effectiveImageDrafts.isNotEmpty()) effectiveImageDraftSend else stateHolder::sendOrEdit,
		onReplyClick = stateHolder::addReply,
		onClearReplyClick = stateHolder::clearReply,
		onEditClick = stateHolder::startEditing,
		onCancelEditClick = stateHolder::cancelEditing,
		onDeleteClick = { stateHolder.deleteMessage(it) },
		onTogglePinClick = stateHolder::togglePin,
		onLoadMore = stateHolder::loadMore,
		onRequestScrollToBottom = stateHolder::requestScrollToBottom,
		onUserStartedScroll = stateHolder::onUserStartedScroll,
		onViewportSnapshotChanged = { snapshot ->
			stateHolder.onViewportSnapshotChanged(snapshot)
			onViewportSnapshotChanged(snapshot)
		},
		onScrollSettled = stateHolder::onScrollSettled,
		onAnchorRestoreSettled = stateHolder::onAnchorRestoreSettled,
		onAnchorRestoreTimedOut = stateHolder::onAnchorRestoreTimedOut,
		onMessageClick = { messageId ->
			if (selectionMode) {
				toggleMessageSelection(messageId)
			}
		},
		onMessageLongClick = { messageId ->
			clearContextMenuState()
			toggleMessageSelection(messageId)
		},
		onReplyPreviewClick = stateHolder::jumpToMessage,
		onContextMenuOpen = { messageId, anchorRect, highlightRect ->
			activeContextMenuMessageId = messageId
			contextMenuFallbackAnchorRect = anchorRect
			contextMenuFallbackHighlightRect = highlightRect ?: anchorRect
		},
		contextMenuAnchorRect = liveContextMenuAnchorRect,
		contextMenuHighlightRect = liveContextMenuHighlightRect,
		onDismissContextMenu = ::clearContextMenuState,
		onContextMenuActionClick = { action ->
			val message = contextMenuMessage ?: return@SharedDirectChatScreen
			clearContextMenuState()
			when (action) {
				ChatContextMenuAction.Reply -> {
					onReplyCreatedFeedback()
					stateHolder.addReply(message.id)
				}
				ChatContextMenuAction.Pin -> stateHolder.togglePin(message.id, true)
				ChatContextMenuAction.Unpin -> stateHolder.togglePin(message.id, false)
				ChatContextMenuAction.CopyText -> {
					onCopyText(message.text)
					scope.launch {
						snackbarHostState.showSnackbar(strings.copied)
					}
				}
				ChatContextMenuAction.Edit -> stateHolder.startEditing(message.id)
				ChatContextMenuAction.Delete -> pendingDeleteMessageId = message.id
				ChatContextMenuAction.Retry -> {
					if (message.deliveryState == ChatDeliveryState.FAILED) {
						val clientMessageId = message.clientMessageId
						if (message.content is ChatMessageContent.Image && clientMessageId != null && onImageRetry != null) {
							onImageRetry(clientMessageId)
						} else {
							stateHolder.retryMessage(message.id)
						}
					}
				}
			}
		},
		pendingDeleteMessageId = pendingDeleteMessageId,
		isDeleteSelectionConfirmationVisible = isDeleteSelectionConfirmationVisible,
		onDismissDeleteMessageConfirmation = {
			pendingDeleteMessageId = null
		},
		onDismissDeleteSelectionConfirmation = {
			isDeleteSelectionConfirmationVisible = false
		},
		onConfirmDeleteMessage = { messageId ->
			pendingDeleteMessageId = null
			stateHolder.deleteMessages(listOf(messageId))
			true
		},
		onUndoDeleteClick = {
			stateHolder.undoDeleteMessage()
		},
		onConfirmDeleteSelection = {
			stateHolder.deleteMessages(selectedMessageIds)
			clearSelectionUiState()
		},
		onSelectionCloseClick = ::clearSelectionUiState,
		onCopySelectionClick = {
			if (selectedCopyText.isNotBlank()) {
				onCopyText(selectedCopyText)
				scope.launch {
					snackbarHostState.showSnackbar(strings.copied)
				}
			}
		},
		onDeleteSelectionClick = {
			isDeleteSelectionConfirmationVisible = true
		},
		onPinnedMessageClick = stateHolder::jumpToMessage,
		snackbarHostState = snackbarHostState,
		onReplyCreatedFeedback = onReplyCreatedFeedback,
		emojiPanel = emojiPanel,
		videoRecordingOverlay = effectiveVideoRecordingOverlay,
		recordingState = effectiveRecordingState,
		onRecordButtonPress = effectiveRecordButtonPress,
		onRecordButtonRelease = effectiveRecordButtonRelease,
		onRecordSwipeUp = effectiveRecordSwipeUp,
		onRecordSwipeLeft = effectiveRecordSwipeLeft,
		onRecordDrag = effectiveRecordDrag,
		onRecordStopClick = effectiveRecordStopClick,
		onRecordSendClick = effectiveRecordSendClick,
		videoCircleInteractionActive = videoCircleInteractionActive,
		videoCircleContent = effectiveVideoCircleContent,
		voiceContent = effectiveVoiceContent,
		imageContent = { albumMessage, isSelectionMode, selectedImageIds, _, onClick, onLongClick, onRetry, onCancel ->
			effectiveImageContent(
				albumMessage,
				isSelectionMode,
				selectedImageIds,
				{ singleId -> selectedMessageIds = selectedMessageIds.toggle(singleId) },
				onClick,
				onLongClick,
				effectiveOnImageRetry,
				effectiveOnImageCancel,
			)
		},
		onImageRetry = effectiveOnImageRetry,
		onImageCancel = effectiveOnImageCancel,
		onAttachClick = effectiveOnAttachClick,
		recordButtonMode = effectiveRecordButtonMode,
		onRecordButtonModeToggle = effectiveRecordButtonModeToggle,
		voiceDraft = effectiveVoiceDraft,
		onVoiceDraftPlayPause = {
			val draft = effectiveVoiceDraft ?: return@SharedDirectChatScreen
			if (voicePlaybackState.isPlaying && voicePlaybackState.activeUiKey == "draft") {
				voicePlayer.pause()
			} else {
				voicePlayer.play(
					messageId = -1L,
					urlOrPath = draft.localAudioPath,
					uiKey = "draft",
					durationMs = draft.durationMs,
				)
			}
		},
		onVoiceDraftSeek = { fraction ->
			val draft = effectiveVoiceDraft ?: return@SharedDirectChatScreen
			val pos = (fraction * draft.durationMs).toLong()
			voicePlayer.seekTo(pos)
		},
		onVoiceDraftDelete = {
			currentDraftClip?.let { runCatching { platformVoiceRecorder?.cancelRecording() } }
			currentDraftClip = null
			internalVoiceDraft = null
			me.floow.shared.chats.media.ChatMediaDraftStore.clear(chatDraftKey)
		},
		onVoiceDraftSend = {
			val clip = currentDraftClip
			if (clip != null) {
				currentDraftClip = null
				internalVoiceDraft = null
				me.floow.shared.chats.media.ChatMediaDraftStore.clear(chatDraftKey)
				stateHolder.addLocalVoice(clip)
			} else {
				effectiveVoiceDraftSend()
			}
		},
		voiceRecordingState = effectiveVoiceRecordingState,
		flightState = activeFlightState,
		videoCircleFlightOverlay = { overlayModifier ->
			me.floow.uikit.chat.components.videocircle.VideoCircleFlightOverlay(
				flightState = activeFlightState,
				targetBoundsProvider = { bubbleBoundsByMessageKey[activeFlightState?.messageUiKey] },
				onLanded = {
					isFlightLanded = true
				},
				onFlightFinished = {
					activeFlightState = null
					isFlightLanded = false
				},
				modifier = overlayModifier,
			)
		},
		imageDrafts = effectiveImageDrafts,
		onImageDraftRemove = effectiveImageDraftRemove,
		onImageDraftReorder = effectiveImageDraftReorder,
		rowBoundsByMessageKey = rowBoundsByMessageKey,
		bubbleBoundsByMessageKey = bubbleBoundsByMessageKey,
		modifier = modifier,
	)

	if (isAttachmentPickerVisible) {
		ChatAttachmentPickerModal(
			onDismissRequest = { isAttachmentPickerVisible = false },
			initialSelectedUris = effectiveImageDrafts.map { it.uri },
			onImagesSelected = { uris, caption ->
				isAttachmentPickerVisible = false
				if (uris.isNotEmpty()) {
					val newDrafts = uris.mapIndexed { index, uri ->
						ChatDraftImage(
							id = "draft_${currentChatEpochMillis()}_${index}_${(100..999).random()}",
							uri = uri,
						)
					}
					localImageDrafts = newDrafts
					if (!caption.isNullOrBlank()) {
						stateHolder.updateInput(caption)
					}
				}
			},
		)
	}

	val pendingMsg = pendingDeleteMessageId?.let(messagesById::get)
	val pendingGroupId = (pendingMsg?.content as? ChatMessageContent.Image)?.groupId?.takeIf(String::isNotBlank)

	if (pendingDeleteMessageId != null) {
		if (pendingGroupId != null) {
			AlertDialog(
				onDismissRequest = { pendingDeleteMessageId = null },
				title = { Text("Удалить медиа") },
				text = { Text("Удалить только эту фотографию или весь альбом?") },
				confirmButton = {
					Button(
						onClick = {
							val groupId = pendingGroupId
							pendingDeleteMessageId = null
							stateHolder.deleteAlbum(groupId)
						}
					) {
						Text("Весь альбом")
					}
				},
				dismissButton = {
					Row {
						TextButton(
							onClick = {
								val id = pendingDeleteMessageId
								pendingDeleteMessageId = null
								if (id != null) {
									stateHolder.deleteMessages(listOf(id))
								}
							}
						) {
							Text("Только эту")
						}
						TextButton(
							onClick = { pendingDeleteMessageId = null }
						) {
							Text(strings.deleteMessageCancel)
						}
					}
				}
			)
		} else {
			AlertDialog(
				onDismissRequest = { pendingDeleteMessageId = null },
				title = { Text(strings.deleteMessageTitle) },
				text = { Text(strings.deleteMessageText) },
				confirmButton = {
					Button(
						onClick = {
							val id = pendingDeleteMessageId
							pendingDeleteMessageId = null
							if (id != null) {
								stateHolder.deleteMessages(listOf(id))
							}
						}
					) {
						Text(strings.deleteMessageConfirm)
					}
				},
				dismissButton = {
					TextButton(onClick = { pendingDeleteMessageId = null }) {
						Text(strings.deleteMessageCancel)
					}
				}
			)
		}
	}

	if (isDeleteSelectionConfirmationVisible) {
		AlertDialog(
			onDismissRequest = { isDeleteSelectionConfirmationVisible = false },
			title = { Text(strings.deleteSelectedMessagesTitle) },
			text = { Text(strings.deleteSelectedMessagesText) },
			confirmButton = {
				Button(
					onClick = {
						val idsToDelete = selectedMessageIds
						clearSelectionUiState()
						stateHolder.deleteMessages(idsToDelete)
					}
				) {
					Text(strings.deleteMessageConfirm)
				}
			},
			dismissButton = {
				TextButton(onClick = { isDeleteSelectionConfirmationVisible = false }) {
					Text(strings.deleteMessageCancel)
				}
			}
		)
	}

	val currentViewerModel = viewerModelProvider()
	if (currentViewerModel != null && viewerState.phase != ViewerPhase.Closed) {
		FullscreenImageViewer(
			model = currentViewerModel,
			state = viewerState,
			onAction = hostedViewer.onAction,
			modifier = Modifier.fillMaxSize(),
		)
	}
}

private fun DirectChatInitialRequest.toInitialHeader(avatarThumb: String? = null): ChatThreadHeaderModel {
	return ChatThreadHeaderModel(
		peerUserId = if (isSavedMessages) "" else peerUserId,
		title = if (isSavedMessages) "" else peerDisplayName,
		avatarUrl = peerAvatarUrl,
		avatarThumb = avatarThumb,
		subtitle = null,
		isSavedMessages = isSavedMessages,
	).withSavedMessagesSemantics()
}

private fun Set<Long>.toggle(messageId: Long): Set<Long> {
	return if (contains(messageId)) this - messageId else this + messageId
}

private fun ChatMessageItemModel.uiMessageKey(): String {
	val normalizedClientMessageId = clientMessageId?.trim().orEmpty()
	return if (normalizedClientMessageId.isNotEmpty()) {
		"cmid_$normalizedClientMessageId"
	} else {
		"msg_$id"
	}
}

private fun resolveContextMenuActions(message: ChatMessageItemModel): List<ChatContextMenuAction> {
	val hasServerId = message.id > 0L
	val isPendingOutgoing = message.isOutgoing && !hasServerId && !message.clientMessageId.isNullOrBlank()
	return buildList {
		if (!message.isDeleted && !isPendingOutgoing) {
			add(ChatContextMenuAction.Reply)
			if (message.isPinned) {
				add(ChatContextMenuAction.Unpin)
			} else {
				add(ChatContextMenuAction.Pin)
			}
		}
		if (message.text.isNotBlank()) {
			add(ChatContextMenuAction.CopyText)
		}
		if (message.isOutgoing && !message.isDeleted && hasServerId) {
			if (message.content is ChatMessageContent.Text) {
				add(ChatContextMenuAction.Edit)
			}
			add(ChatContextMenuAction.Delete)
		} else if (isPendingOutgoing && !message.isDeleted) {
			add(ChatContextMenuAction.Delete)
		}
		if (isPendingOutgoing && message.deliveryState == ChatDeliveryState.FAILED) {
			add(ChatContextMenuAction.Retry)
		}
	}
}

private fun resolveChatImageAspect(
	item: me.floow.uikit.chat.model.ChatImageItem?,
	painter: Painter?,
	bounds: Rect?,
): Float {
	if (item != null && item.width > 0 && item.height > 0) {
		return (item.width.toFloat() / item.height.toFloat()).coerceIn(0.1f, 10f)
	}
	val intrinsic = painter?.intrinsicSize
	if (intrinsic != null && intrinsic.width.isFinite() && intrinsic.height.isFinite() && intrinsic.width > 0f && intrinsic.height > 0f) {
		return (intrinsic.width / intrinsic.height).coerceIn(0.1f, 10f)
	}
	if (bounds != null && bounds.height > 0f && bounds.width > 0f) {
		return (bounds.width / bounds.height).coerceIn(0.1f, 10f)
	}
	return 1.0f
}
