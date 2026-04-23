package me.floow.chats

import android.Manifest
import android.content.ClipData
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.runtime.mutableStateMapOf
import me.floow.uikit.chat.model.VideoRecordingMode
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import me.floow.chats.camera.AndroidVideoRecorderBridge
import me.floow.chats.camera.VideoRecorder
import me.floow.chats.uilogic.chat.DirectChatOpenMode
import me.floow.chats.uilogic.chat.AndroidVideoCircleMessageSender
import me.floow.chats.videocircle.MessageCirclePlayer
import me.floow.chats.videocircle.VideoCirclePlaybackStatus
import me.floow.chats.videocircle.rememberVideoCirclePlaybackCoordinator
import me.floow.shared.chats.model.ChatOpenMode
import me.floow.shared.chats.ui.SharedDirectChatRoute
import me.floow.shared.chats.uilogic.direct.DirectChatInitialRequest
import me.floow.shared.chats.uilogic.direct.ChatPresenceContract
import me.floow.shared.chats.uilogic.direct.ChatRealtimeContract
import me.floow.shared.chats.uilogic.direct.DirectChatStateHolder
import me.floow.shared.chats.uilogic.direct.StaticChatThreadRepository
import me.floow.shared.chats.uilogic.direct.VideoRecordingStateHolder
import me.floow.uikit.chat.components.VideoRecordingOverlay
import me.floow.uikit.components.pickers.FlowEmojiPanel
import me.floow.uikit.util.SetStatusBarStyle
import kotlinx.coroutines.launch
import org.koin.compose.koinInject
import org.koin.core.parameter.parametersOf
import java.io.File

private val REQUIRED_PERMISSIONS = arrayOf(
	Manifest.permission.CAMERA,
	Manifest.permission.RECORD_AUDIO,
)

data class ChatRouteInitialData(
	val chatInterlocutorId: String,
	val chatInterlocutorName: String,
	val chatInterlocutorAvatarUrl: Uri?,
	val conversationId: Long? = null,
	val messageAnchorId: Long? = null,
	val openMode: DirectChatOpenMode = DirectChatOpenMode.FROM_LAST_SEEN,
	val isSavedMessages: Boolean = false,
)

@Composable
fun ChatRoute(
	initialData: ChatRouteInitialData,
	onBackClick: () -> Unit,
	onProfileClick: (String) -> Unit = {},
	isMockBuild: Boolean = false,
	modifier: Modifier = Modifier
) {
	val context = LocalContext.current
	val lifecycleOwner = LocalLifecycleOwner.current
	val scope = rememberCoroutineScope()
	val clipboard = LocalClipboard.current
	val haptics = LocalHapticFeedback.current
	val snackbarHostState = remember { SnackbarHostState() }
	val initialRequest = remember(initialData) { initialData.toSharedInitialRequest() }
	val chatRealtimeContract: ChatRealtimeContract? = if (isMockBuild) null else koinInject()
	val chatPresenceContract: ChatPresenceContract? = if (isMockBuild) null else koinInject()
	val videoCircleMessageSender: AndroidVideoCircleMessageSender? = if (isMockBuild) null else koinInject()

	val videoRecorder = remember {
		VideoRecorder(context, lifecycleOwner).also {
			it.init(File(context.cacheDir, "video_circles"))
		}
	}

	var permissionsGranted by remember { mutableStateOf(hasPermissions(context)) }

	val permissionLauncher = rememberLauncherForActivityResult(
		ActivityResultContracts.RequestMultiplePermissions()
	) { result ->
		permissionsGranted = result.all { it.value }
	}

	LaunchedEffect(Unit) {
		if (hasPermissions(context)) {
			permissionsGranted = true
		}
	}

	DisposableEffect(Unit) {
		onDispose { videoRecorder.release() }
	}

	val recordingStateHolder = remember { VideoRecordingStateHolder() }
	val recordingState by recordingStateHolder.state.collectAsState()
	val recorderBridge = remember { AndroidVideoRecorderBridge(videoRecorder) }
	val bubbleBoundsByMessageKey = remember { mutableStateMapOf<String, androidx.compose.ui.geometry.Rect>() }
	val playbackCoordinator = rememberVideoCirclePlaybackCoordinator()
	val videoCirclePlaybackState by playbackCoordinator.state.collectAsState()
	var videoCircleInteractionActive by remember { mutableStateOf(false) }

	DisposableEffect(recorderBridge) {
		recordingStateHolder.recorder = recorderBridge
		onDispose { recordingStateHolder.recorder = null }
	}

	LaunchedEffect(recordingState.isActive) {
		if (!recordingState.isActive) {
			videoRecorder.stopCamera()
		}
	}

	DisposableEffect(lifecycleOwner, recordingState.mode) {
		val observer = LifecycleEventObserver { _, event ->
			if (event == Lifecycle.Event.ON_STOP) {
				playbackCoordinator.onStop()
				when (recordingState.mode) {
					VideoRecordingMode.Recording -> recordingStateHolder.onRecordButtonRelease()
					else -> Unit
				}
			}
		}
		lifecycleOwner.lifecycle.addObserver(observer)
		onDispose {
			lifecycleOwner.lifecycle.removeObserver(observer)
		}
	}

	val stateHolder = if (isMockBuild) {
		remember(initialRequest) { DirectChatStateHolder(repository = StaticChatThreadRepository()) }
	} else {
		koinInject<DirectChatStateHolder>(
			parameters = { parametersOf(chatRealtimeContract, chatPresenceContract) }
		)
	}

	// Keep business insertion separate from playback. The bubble registers itself as
	// visible when it enters composition; only then can the player pool attach preview.
	androidx.compose.runtime.SideEffect {
		recordingStateHolder.onVideoReadyForLocalInsertion = { clip, _ ->
			val uiKey = stateHolder.addLocalVideoCircle(clip)
			val clientMessageId = uiKey.removePrefix("cmid_")
			val conversationId = stateHolder.activeConversationIdOrNull()
				if (conversationId == null || videoCircleMessageSender == null) {
					stateHolder.markLocalVideoCircleFailed(clientMessageId)
					scope.launch {
						snackbarHostState.showSnackbar("Не удалось подготовить видеосообщение")
					}
				} else {
					scope.launch {
						stateHolder.markLocalVideoCircleUploading(clientMessageId)
						videoCircleMessageSender.send(
							conversationId = conversationId,
							clientMessageId = clientMessageId,
							clip = clip,
						).onSuccess { sent ->
							stateHolder.resolveLocalVideoCircleSent(
								clientMessageId = sent.clientMessageId,
								serverMessage = sent.message,
								remoteUrl = sent.remoteUrl,
								durationMs = sent.durationMs,
								width = sent.width,
								height = sent.height,
							)
						}.onFailure {
							stateHolder.markLocalVideoCircleFailed(clientMessageId)
							snackbarHostState.showSnackbar("Не удалось отправить видеосообщение")
						}
					}
				}
			}
		}

	SetStatusBarStyle(
		color = MaterialTheme.colorScheme.background,
		darkIcons = MaterialTheme.colorScheme.background.luminance() > 0.5f
	)

	Box {
	SharedDirectChatRoute(
		stateHolder = stateHolder,
		initialRequest = initialRequest,
		onBackClick = onBackClick,
		onShowMessage = { message ->
			scope.launch { snackbarHostState.showSnackbar(message) }
		},
			onCopyText = { text ->
				scope.launch {
					clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("chat-message", text)))
				}
			},
		onHeaderClick = if (initialRequest.isSavedMessages || initialRequest.peerUserId.isBlank()) {
			null
		} else {
			{ onProfileClick(initialRequest.peerUserId) }
		},
		emojiPanel = { inputController ->
			FlowEmojiPanel(
				onEmojiPicked = inputController::insertEmoji,
				modifier = Modifier,
			)
		},
		videoRecordingOverlay = { bottomInsetPx, recordingControlBounds, overlayModifier ->
			VideoRecordingOverlay(
				state = recordingState,
				bottomInsetPx = bottomInsetPx,
				recordingControlBounds = recordingControlBounds,
				modifier = overlayModifier,
				onStopClick = recordingStateHolder::stopRecording,
				onSourceMeasured = recordingStateHolder::updateFlightSource,
				onDismissFailed = recordingStateHolder::onDismissFailed,
				onSwitchCameraClick = { videoRecorder.switchCamera() },
				cameraPreview = {
					androidx.compose.ui.viewinterop.AndroidView(
						factory = { videoRecorder.previewViewChild },
						modifier = Modifier.matchParentSize(),
					)
				},
			)
		},
		recordingState = recordingState,
		onRecordButtonPress = {
			if (!permissionsGranted) {
				permissionLauncher.launch(REQUIRED_PERMISSIONS)
				return@SharedDirectChatRoute
			}
			videoRecorder.startCamera { ready ->
				if (!ready) {
					scope.launch { snackbarHostState.showSnackbar("Камера не готова") }
					return@startCamera
				}
				haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
				playbackCoordinator.onRecordingStarted()
				recordingStateHolder.onRecordButtonPress()
			}
		},
		onRecordButtonRelease = {
			haptics.performHapticFeedback(HapticFeedbackType.LongPress)
			recordingStateHolder.onRecordButtonRelease()
		},
		onRecordSwipeUp = {
			haptics.performHapticFeedback(HapticFeedbackType.LongPress)
			recordingStateHolder.onSwipeUpLock()
		},
		onRecordSwipeLeft = recordingStateHolder::onSwipeLeft,
		onRecordDrag = recordingStateHolder::onRecordDrag,
		onRecordStopClick = recordingStateHolder::stopRecording,
		onViewportSnapshotChanged = playbackCoordinator::onViewportSnapshotChanged,
		videoCircleInteractionActive = videoCircleInteractionActive,
		bubbleBoundsByMessageKey = bubbleBoundsByMessageKey,
		videoCircleContent = { videoCircleMessage ->
				val messageUiKey = videoCircleMessage.uiKey
				DisposableEffect(messageUiKey) {
					playbackCoordinator.register(videoCircleMessage)
					onDispose {
						playbackCoordinator.unregister(messageUiKey)
					}
				}
				val playablePath = videoCircleMessage.playableSource
				val isActiveMessage = videoCirclePlaybackState.activeUiKey == messageUiKey &&
					videoCirclePlaybackState.status != VideoCirclePlaybackStatus.Idle
				val isPaused = isActiveMessage && videoCirclePlaybackState.status == VideoCirclePlaybackStatus.Paused
				val isPlaying = isActiveMessage && when (videoCirclePlaybackState.status) {
					VideoCirclePlaybackStatus.Buffering,
					VideoCirclePlaybackStatus.Playing -> true
					else -> false
				}
				val player = if (playablePath != null && isActiveMessage) playbackCoordinator.player else null

				if (playablePath != null) {
					MessageCirclePlayer(
						videoPath = playablePath,
						player = player,
						isPlaying = isPlaying && !isPaused,
						isPaused = isPaused,
						autoplayMutedLoop = isActiveMessage && videoCirclePlaybackState.isMuted,
						growFromEnd = true,
						onSeekProgress = playbackCoordinator::seekToProgress,
						onSeekInteractionChange = { active ->
							videoCircleInteractionActive = active
						},
						onTogglePlayback = {
							playbackCoordinator.onVideoCircleClick(videoCircleMessage)
						},
					)
				}
			},
		modifier = modifier,
	)
	}
}

private fun hasPermissions(context: android.content.Context): Boolean {
	return REQUIRED_PERMISSIONS.all {
		ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
	}
}

private fun ChatRouteInitialData.toSharedInitialRequest(): DirectChatInitialRequest {
	return DirectChatInitialRequest(
		peerUserId = chatInterlocutorId,
		peerDisplayName = chatInterlocutorName,
		peerAvatarUrl = chatInterlocutorAvatarUrl?.toString(),
		conversationId = conversationId,
		anchorMessageId = messageAnchorId,
		openMode = when (openMode) {
			DirectChatOpenMode.FROM_UNREAD -> ChatOpenMode.FROM_UNREAD
			DirectChatOpenMode.FROM_LAST_SEEN -> ChatOpenMode.FROM_LAST_SEEN
			DirectChatOpenMode.FROM_MESSAGE_LINK -> ChatOpenMode.FROM_MESSAGE_LINK
		},
		isSavedMessages = isSavedMessages,
		allowCreateFromPeerUserId = conversationId == null && chatInterlocutorId.isNotBlank(),
	)
}
