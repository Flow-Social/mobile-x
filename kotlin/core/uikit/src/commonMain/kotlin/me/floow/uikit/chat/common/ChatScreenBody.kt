package me.floow.uikit.chat.common

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import me.floow.uikit.chat.components.ChatBottomHost
import me.floow.uikit.chat.components.ChatDraftImageStrip
import me.floow.uikit.chat.components.MessageInputField
import me.floow.uikit.chat.input.ChatInputController
import me.floow.uikit.chat.input.ChatInputMode
import me.floow.uikit.chat.model.ChatDraftImage
import me.floow.uikit.chat.model.VideoRecordingState
import me.floow.uikit.components.media.FlowImageSource

@Composable
fun ChatScreenBody(
	title: String?,
	subtitle: String?,
	avatarSource: FlowImageSource = FlowImageSource.Empty,
	avatarUrl: String? = null,
	avatarThumb: String? = null,
	isSavedMessages: Boolean,
	onBackClick: () -> Unit,
	isSelectionMode: Boolean,
	selectedCount: Int,
	canCopySelection: Boolean,
	canDeleteSelection: Boolean,
	onSelectionCloseClick: () -> Unit,
	onCopySelectionClick: () -> Unit,
	onDeleteSelectionClick: () -> Unit,
	selectedCountLabel: String,
	closeContentDescription: String,
	copyContentDescription: String,
	deleteContentDescription: String,
	showInputBar: Boolean,
	inputController: ChatInputController,
	actualImeHeightPx: Int,
	onSendClick: () -> Unit,
	sendButtonActive: Boolean,
	isEditMode: Boolean,
	showEmojiButton: Boolean,
	showRecordButton: Boolean = false,
	onRecordButtonPress: () -> Unit = {},
	onRecordButtonRelease: () -> Unit = {},
	onRecordSwipeUp: () -> Unit = {},
	onRecordSwipeLeft: () -> Unit = {},
	onRecordDrag: (Float, Float) -> Unit = { _, _ -> },
	onRecordStopClick: () -> Unit = {},
	onRecordSendClick: () -> Unit = onRecordStopClick,
	onRecordButtonBoundsChanged: (Rect?) -> Unit = {},
	recordingState: VideoRecordingState = VideoRecordingState(),
	recordCancelThresholdPx: Float = 220f,
	recordLockThresholdPx: Float = 180f,
	recordButtonMode: me.floow.uikit.chat.model.ChatRecordButtonMode = me.floow.uikit.chat.model.ChatRecordButtonMode.Voice,
	onRecordButtonModeToggle: () -> Unit = {},
	voiceDraft: me.floow.uikit.chat.model.VoiceDraftUiState? = null,
	onVoiceDraftPlayPause: () -> Unit = {},
	onVoiceDraftSeek: (Float) -> Unit = {},
	onVoiceDraftDelete: () -> Unit = {},
	onVoiceDraftSend: () -> Unit = {},
	voiceRecordingState: () -> me.floow.uikit.chat.model.VoiceRecordingInputState = { me.floow.uikit.chat.model.VoiceRecordingInputState() },
	imageDrafts: List<ChatDraftImage> = emptyList(),
	onImageDraftRemove: (String) -> Unit = {},
	onImageDraftReorder: (List<String>) -> Unit = {},
	onAttachClick: (() -> Unit)? = null,
	composerReplyTitle: String? = null,
	composerReplySubtitle: String? = null,
	onComposerReplyClose: (() -> Unit)? = null,
	onComposerReplyClick: (() -> Unit)? = null,
	onHeaderClick: (() -> Unit)? = null,
	onTrailingClick: (() -> Unit)? = null,
	headerAvatarPainter: Painter? = null,
	dividerColor: Color? = null,
	modifier: Modifier = Modifier,
	bodyContentModifier: Modifier = Modifier,
	snackbarHost: @Composable (() -> Unit)? = null,
	playbackBar: @Composable () -> Unit = {},
	pinnedMessages: @Composable (() -> Unit)? = null,
	content: @Composable (Modifier, Dp) -> Unit,
	screenOverlay: @Composable ((Modifier) -> Unit)? = null,
	emojiPanel: @Composable BoxScope.() -> Unit = {},
	contextMenuOverlay: @Composable (Rect?) -> Unit = {},
) {
	val density = LocalDensity.current
	val isVoiceRecording by remember(voiceRecordingState) {
		derivedStateOf(structuralEqualityPolicy()) { voiceRecordingState().isRecording }
	}
	val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
	val chatBg = if (isDark) Color(0xFF121217) else Color(0xFFF6F6F6)

	val bottomSpacerHeightPx = when (inputController.inputMode) {
		ChatInputMode.Keyboard -> if (inputController.isSwitchingEmojiToKeyboard) {
			maxOf(actualImeHeightPx, inputController.keyboardLayoutHeightPx)
		} else {
			actualImeHeightPx
		}
		ChatInputMode.Emoji -> inputController.emojiPanelHeightPx
		ChatInputMode.None -> 0
		ChatInputMode.Recording -> if (inputController.wasKeyboardOpenBeforeRecording) {
			actualImeHeightPx
		} else {
			0
		}
	}
	val bottomSpacerHeightDp = with(density) { bottomSpacerHeightPx.toDp() }
	val navBarsBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
	val imeBottomDp = with(density) { WindowInsets.ime.getBottom(density).toDp() }
	val contentBottomLiftDp = if (bottomSpacerHeightPx > 0) {
		maxOf(bottomSpacerHeightDp + navBarsBottomDp, imeBottomDp)
	} else {
		0.dp
	}
	val showComposer = showInputBar && !isSelectionMode
	var composerHeightPx by remember(showInputBar) { mutableIntStateOf(0) }
	val composerHeight = if (showInputBar) {
		with(density) { composerHeightPx.toDp() }
	} else {
		0.dp
	}
	val timelineBottomInset = (navBarsBottomDp + bottomSpacerHeightDp + composerHeight - contentBottomLiftDp)
		.coerceAtLeast(0.dp)
	val replyHeader = composerReplyTitle?.takeUnless(String::isBlank)?.let { it to composerReplySubtitle.orEmpty() }
	var timelineBounds by remember { mutableStateOf<Rect?>(null) }

	Box(
		modifier = modifier
			.fillMaxSize()
			.background(chatBg),
	) {
		Column(
			modifier = Modifier
				.fillMaxSize()
				.padding(bottom = contentBottomLiftDp)
				.then(bodyContentModifier),
		) {
			val topBarBackground = MaterialTheme.colorScheme.surface
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.background(topBarBackground)
					.statusBarsPadding()
			) {
				if (isSelectionMode) {
					ChatSelectionTopBar(
						selectedCount = selectedCount,
						canCopy = canCopySelection,
						canDelete = canDeleteSelection,
						onCloseClick = onSelectionCloseClick,
						onCopyClick = onCopySelectionClick,
						onDeleteClick = onDeleteSelectionClick,
						selectedCountLabel = selectedCountLabel,
						closeContentDescription = closeContentDescription,
						copyContentDescription = copyContentDescription,
						deleteContentDescription = deleteContentDescription,
						dividerColor = dividerColor,
						modifier = Modifier.fillMaxWidth(),
					)
				} else if (title != null) {
					val effectiveAvatarSource = if (!avatarSource.isEmpty) avatarSource else FlowImageSource.of(avatarUrl, avatarThumb)
					ChatHeaderBar(
						title = title,
						subtitle = subtitle,
						avatarSource = effectiveAvatarSource,
						isSavedMessages = isSavedMessages,
						onBackClick = onBackClick,
						onHeaderClick = onHeaderClick,
						onTrailingClick = onTrailingClick,
						avatarPainter = headerAvatarPainter,
						dividerColor = dividerColor,
						modifier = Modifier.fillMaxWidth(),
					)
				}
			}
			pinnedMessages?.invoke()
			Box(
				modifier = Modifier
					.fillMaxWidth()
					.weight(1f)
					.background(chatBg)
					.onGloballyPositioned { timelineBounds = it.boundsInRoot() },
			) {
				content(Modifier.fillMaxSize(), timelineBottomInset)
				if (!isSelectionMode) playbackBar()
			}
		}

		screenOverlay?.invoke(
			Modifier.fillMaxSize(),
		)

		AnimatedVisibility(
			visible = showComposer,
			modifier = Modifier.align(Alignment.BottomCenter),
			enter = fadeIn(tween(150)),
			exit = fadeOut(tween(120)),
		) {
			ChatBottomHost(
				controller = inputController,
				actualImeHeightPx = actualImeHeightPx,
				isRecording = recordingState.isActive || isVoiceRecording,
				onComposerHeightChanged = { if (showComposer) composerHeightPx = it },
				modifier = Modifier.fillMaxWidth(),
				composer = {
					if (imageDrafts.isNotEmpty()) {
						ChatDraftImageStrip(
							images = imageDrafts,
							onAddClick = onAttachClick ?: {},
							onRemoveClick = onImageDraftRemove,
							onOrderCommit = onImageDraftReorder,
							modifier = Modifier
								.fillMaxWidth()
								.padding(vertical = 4.dp),
						)
					}
					if (dividerColor != null) {
						HorizontalDivider(color = dividerColor)
					}
					MessageInputField(
						controller = inputController,
						onSendClick = onSendClick,
						sendButtonActive = sendButtonActive,
						isEditMode = isEditMode,
						showEmojiButton = showEmojiButton,
						showRecordButton = showRecordButton,
						onRecordButtonPress = onRecordButtonPress,
						onRecordButtonRelease = onRecordButtonRelease,
						onRecordSwipeUp = onRecordSwipeUp,
						onRecordSwipeLeft = onRecordSwipeLeft,
						onRecordDrag = onRecordDrag,
						onRecordStopClick = onRecordStopClick,
						onRecordSendClick = onRecordSendClick,
						onRecordButtonBoundsChanged = onRecordButtonBoundsChanged,
						recordingState = recordingState,
						recordCancelThresholdPx = recordCancelThresholdPx,
						recordLockThresholdPx = recordLockThresholdPx,
						recordButtonMode = recordButtonMode,
						onRecordButtonModeToggle = onRecordButtonModeToggle,
						voiceDraft = voiceDraft,
						onVoiceDraftPlayPause = onVoiceDraftPlayPause,
						onVoiceDraftSeek = onVoiceDraftSeek,
						onVoiceDraftDelete = onVoiceDraftDelete,
						onVoiceDraftSend = onVoiceDraftSend,
						voiceRecordingState = voiceRecordingState(),
						imageDrafts = imageDrafts,
						onImageDraftRemove = onImageDraftRemove,
						onImageDraftReorder = onImageDraftReorder,
						onAttachClick = onAttachClick,
						modifier = Modifier.fillMaxWidth(),
						expanded = replyHeader != null,
						header = {
							AnimatedContent(
								targetState = replyHeader,
								contentKey = { it != null },
								transitionSpec = {
									(fadeIn(tween(160)) togetherWith fadeOut(tween(110)))
										.using(SizeTransform { _, _ -> tween(190) })
								},
								label = "chat_composer_reply",
							) { reply ->
								if (reply != null) {
									ChatCurrentReply(
										title = reply.first,
										subtitle = reply.second,
										onClose = onComposerReplyClose ?: {},
										onClick = onComposerReplyClick,
										isEditMode = isEditMode,
										modifier = Modifier.fillMaxWidth(),
									)
								}
							}
						},
					)
				},
				emojiPanel = emojiPanel,
			)
		}

		snackbarHost?.invoke()
		contextMenuOverlay(timelineBounds?.let { bounds ->
			bounds.copy(bottom = (bounds.bottom - with(density) { timelineBottomInset.toPx() }).coerceAtLeast(bounds.top))
		})
	}
}
