package me.floow.uikit.chat.common

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import me.floow.uikit.chat.components.ChatBottomHost
import me.floow.uikit.chat.components.ChatDraftImageStrip
import me.floow.uikit.chat.components.MessageInputField
import me.floow.uikit.chat.input.ChatInputController
import me.floow.uikit.chat.input.ChatInputMode
import me.floow.uikit.chat.model.ChatDraftImage
import me.floow.uikit.chat.model.VideoRecordingState

@Composable
fun ChatScreenBody(
	title: String?,
	subtitle: String?,
	avatarUrl: String?,
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
	voiceRecordingState: me.floow.uikit.chat.model.VoiceRecordingInputState = me.floow.uikit.chat.model.VoiceRecordingInputState(),
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
	pinnedMessages: @Composable (() -> Unit)? = null,
	content: @Composable (Modifier) -> Unit,
	screenOverlay: @Composable ((Modifier) -> Unit)? = null,
	emojiPanel: @Composable BoxScope.() -> Unit = {},
	contextMenuOverlay: @Composable () -> Unit = {},
) {
	val density = LocalDensity.current
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
					ChatHeaderBar(
						title = title,
						subtitle = subtitle,
						avatarUrl = avatarUrl,
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
			content(
				Modifier
					.fillMaxWidth()
					.weight(1f)
					.background(chatBg),
			)
		}

		screenOverlay?.invoke(
			Modifier.fillMaxSize(),
		)

		if (showInputBar && !isSelectionMode) {
			ChatBottomHost(
				controller = inputController,
				actualImeHeightPx = actualImeHeightPx,
				isRecording = recordingState.isActive || voiceRecordingState.isRecording,
				modifier = Modifier
					.align(Alignment.BottomCenter)
					.fillMaxWidth(),
				composer = {
					if (!composerReplyTitle.isNullOrBlank() || !composerReplySubtitle.isNullOrBlank()) {
						ChatCurrentReply(
							title = composerReplyTitle.orEmpty(),
							subtitle = composerReplySubtitle.orEmpty(),
							onClose = onComposerReplyClose ?: {},
							onClick = onComposerReplyClick,
							modifier = Modifier.fillMaxWidth(),
						)
					}
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
						voiceRecordingState = voiceRecordingState,
						imageDrafts = imageDrafts,
						onImageDraftRemove = onImageDraftRemove,
						onImageDraftReorder = onImageDraftReorder,
						onAttachClick = onAttachClick,
						modifier = Modifier.fillMaxWidth(),
					)
				},
				emojiPanel = emojiPanel,
			)
		}

		snackbarHost?.invoke()
	}

	contextMenuOverlay()
}
