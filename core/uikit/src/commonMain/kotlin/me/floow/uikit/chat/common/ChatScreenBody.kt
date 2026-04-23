package me.floow.uikit.chat.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.unit.dp
import me.floow.uikit.chat.components.ChatBottomHost
import me.floow.uikit.chat.components.MessageInputField
import me.floow.uikit.chat.input.ChatInputController
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
	onRecordButtonBoundsChanged: (Rect?) -> Unit = {},
	recordingState: VideoRecordingState = VideoRecordingState(),
	recordCancelThresholdPx: Float = 220f,
	recordLockThresholdPx: Float = 180f,
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
	Scaffold(
		bottomBar = {
			if (showInputBar && !isSelectionMode) {
				ChatBottomHost(
					controller = inputController,
					actualImeHeightPx = actualImeHeightPx,
					modifier = Modifier.fillMaxWidth(),
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
						HorizontalDivider(color = dividerColor ?: MaterialTheme.colorScheme.outlineVariant)
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
							onRecordButtonBoundsChanged = onRecordButtonBoundsChanged,
							recordingState = recordingState,
							recordCancelThresholdPx = recordCancelThresholdPx,
							recordLockThresholdPx = recordLockThresholdPx,
							modifier = Modifier.fillMaxWidth(),
						)
					},
					emojiPanel = emojiPanel,
				)
			}
		},
		snackbarHost = { snackbarHost?.invoke() },
		contentWindowInsets = WindowInsets(0.dp),
		modifier = modifier
			.fillMaxSize()
			.background(MaterialTheme.colorScheme.background),
	) { innerPadding ->
		Box(modifier = Modifier.fillMaxSize()) {
			Column(
				modifier = Modifier
					.fillMaxSize()
					.padding(innerPadding)
					.then(bodyContentModifier),
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
						modifier = Modifier.statusBarsPadding(),
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
						modifier = Modifier.statusBarsPadding(),
					)
				}
				pinnedMessages?.invoke()
				content(
					Modifier
						.fillMaxWidth()
						.weight(1f)
						.background(MaterialTheme.colorScheme.surfaceContainer),
				)
			}

			screenOverlay?.invoke(
				Modifier
					.fillMaxSize()
					.padding(bottom = innerPadding.calculateBottomPadding()),
			)
		}
	}

	contextMenuOverlay()
}
