package me.floow.uikit.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Dp
import me.floow.uikit.chat.model.ChatRecordButtonMode
import me.floow.uikit.chat.model.ChatDraftImage
import me.floow.uikit.chat.model.VoiceDraftUiState
import me.floow.uikit.chat.model.VoiceRecordingInputState
import me.floow.uikit.chat.model.toChatMediaRecordingState
import me.floow.uikit.chat.components.UnifiedRecordingCapsule
import me.floow.uikit.chat.components.RecordingLockPill
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import flow.core.uikit.generated.resources.Res
import flow.core.uikit.generated.resources.chat_recording_cancel
import flow.core.uikit.generated.resources.done_icon
import flow.core.uikit.generated.resources.chat_clip_icon
import flow.core.uikit.generated.resources.chat_emoji_icon
import flow.core.uikit.generated.resources.chat_keyboard_icon
import flow.core.uikit.generated.resources.chat_mic_icon
import flow.core.uikit.generated.resources.chat_video_round_icon
import flow.core.uikit.generated.resources.chat_send_arrow
import flow.core.uikit.generated.resources.gallery_icon
import flow.core.uikit.generated.resources.send_icon
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import me.floow.uikit.chat.components.ChatTailVector
import me.floow.uikit.chat.input.ChatInputController
import me.floow.uikit.chat.input.ChatInputMode
import me.floow.uikit.chat.model.VideoRecordingMode
import me.floow.uikit.chat.model.VideoRecordingState
import me.floow.uikit.theme.LocalTypography
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource
import coil3.compose.AsyncImage
import androidx.compose.ui.layout.ContentScale

private val ChatInputIconSize = 28.dp
private val ChatInputKeyboardGlyphSize = 24.dp
private val ChatInputGlyphSize = 28.dp
private val ChatInputSidePadding = 14.dp
private val ChatInputMinHeight = 54.dp
private val ChatInputIconBottomInset = 12.dp
private val ChatInputTextBottomInset = 15.dp
private val ChatInputTextStartInset = 38.dp
private val ChatInputTextEndInset = 38.dp
private val ChatInputTextSize = 16.sp
private val RecordingInputButtonSize = 72.dp
private val RecordingInputHaloSize = 94.dp
private val RecordingInputRed = Color(0xFFFF5959)
private const val RecordCancelThresholdPx = 220f

@Composable
fun MessageInputField(
	controller: ChatInputController,
	onSendClick: () -> Unit,
	sendButtonActive: Boolean,
	isEditMode: Boolean = false,
	showEmojiButton: Boolean = true,
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
	recordCancelThresholdPx: Float = RecordCancelThresholdPx,
	recordLockThresholdPx: Float = 180f,
	recordButtonMode: ChatRecordButtonMode = ChatRecordButtonMode.Voice,
	onRecordButtonModeToggle: () -> Unit = {},
	voiceDraft: VoiceDraftUiState? = null,
	onVoiceDraftPlayPause: () -> Unit = {},
	onVoiceDraftSeek: (Float) -> Unit = {},
	onVoiceDraftDelete: () -> Unit = {},
	onVoiceDraftSend: () -> Unit = {},
	voiceRecordingState: VoiceRecordingInputState = VoiceRecordingInputState(),
	imageDrafts: List<ChatDraftImage> = emptyList(),
	onImageDraftRemove: (String) -> Unit = {},
	onImageDraftReorder: (List<String>) -> Unit = {},
	onAttachClick: (() -> Unit)? = null,
	modifier: Modifier = Modifier,
) {
	val focusRequester = remember { FocusRequester() }
	val focusManager = LocalFocusManager.current
	val keyboardController = LocalSoftwareKeyboardController.current
	val viewConfiguration = LocalViewConfiguration.current
	val haptic = LocalHapticFeedback.current
	val inputInteractionSource = remember { MutableInteractionSource() }
	var isFocused by remember { mutableStateOf(false) }
	var textLayoutResult by remember { mutableStateOf<TextLayoutResult?>(null) }
	val isVideoRecording = recordingState.mode == VideoRecordingMode.Recording
	val isVideoPreview = recordingState.mode == VideoRecordingMode.Preview
	val isRecordingActive = isVideoRecording
	val isVoiceRecording = voiceRecordingState.isRecording
	val hasVoiceDraft = voiceDraft != null
	val hasAnyDraft = hasVoiceDraft || isVideoPreview
	val isAnyRecording = isVideoRecording || isVoiceRecording || isRecordingActive
	var lastHandledKeyboardToken by remember { mutableStateOf(controller.keyboardRequestToken) }

	LaunchedEffect(controller.keyboardRequestToken) {
		if (controller.keyboardRequestToken == 0 || controller.keyboardRequestToken == lastHandledKeyboardToken) return@LaunchedEffect
		lastHandledKeyboardToken = controller.keyboardRequestToken
		focusRequester.requestFocus()
		keyboardController?.show()
	}

	LaunchedEffect(controller.hideImeToken) {
		if (controller.hideImeToken == 0) return@LaunchedEffect
		keyboardController?.hide()
		focusManager.clearFocus(force = true)
	}

	LaunchedEffect(controller.inputMode) {
		if (controller.inputMode == ChatInputMode.Emoji) {
			keyboardController?.hide()
		}
	}

	val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
	val capsuleBg = if (isDark) Color(0xFF2E2E2E) else Color(0xFFDFDFDF)
	val inputTextColor = if (isDark) Color.White else Color(0xFF121213)
	val placeholderColor = Color(0xFFAFAFAF)
	val iconTint = Color(0xFF83858F)
	val sendButtonBg = Color(0xFF7A73F8)

	var textFieldViewportHeightPx by remember { mutableStateOf(0) }
	var scrollOffsetPx by remember { mutableStateOf(0f) }

	val totalTextHeightPx = textLayoutResult?.size?.height ?: 0
	val lineCount = textLayoutResult?.lineCount ?: 0
	val hasOverflow = (textFieldViewportHeightPx > 0 && totalTextHeightPx > textFieldViewportHeightPx + 4) ||
		(textLayoutResult?.didOverflowHeight == true) ||
		(textLayoutResult?.hasVisualOverflow == true) ||
		lineCount > 6

	val maxScrollPx = if (textFieldViewportHeightPx > 0 && totalTextHeightPx > textFieldViewportHeightPx + 2) {
		(totalTextHeightPx - textFieldViewportHeightPx).coerceAtLeast(0)
	} else {
		0
	}

	val cursorOffset = controller.textFieldValue.selection.start.coerceIn(0, controller.textFieldValue.text.length)
	val textLength = controller.textFieldValue.text.length

	LaunchedEffect(cursorOffset, textLength, maxScrollPx) {
		if (maxScrollPx <= 0) {
			scrollOffsetPx = 0f
			return@LaunchedEffect
		}
		if (cursorOffset >= textLength) {
			scrollOffsetPx = maxScrollPx.toFloat()
			return@LaunchedEffect
		}
		if (cursorOffset == 0) {
			scrollOffsetPx = 0f
			return@LaunchedEffect
		}
		val layout = textLayoutResult ?: return@LaunchedEffect
		val cursorRect = try { layout.getCursorRect(cursorOffset) } catch (_: Exception) { null }
		if (cursorRect != null && textFieldViewportHeightPx > 0) {
			if (cursorRect.bottom > scrollOffsetPx + textFieldViewportHeightPx) {
				scrollOffsetPx = (cursorRect.bottom - textFieldViewportHeightPx).coerceIn(0f, maxScrollPx.toFloat())
			} else if (cursorRect.top < scrollOffsetPx) {
				scrollOffsetPx = cursorRect.top.coerceIn(0f, maxScrollPx.toFloat())
			}
		}
	}

	val inputNestedScrollConnection = remember(maxScrollPx) {
		object : NestedScrollConnection {
			override fun onPostScroll(
				consumed: Offset,
				available: Offset,
				source: NestedScrollSource
			): Offset {
				if (maxScrollPx > 0 && consumed.y != 0f) {
					scrollOffsetPx = (scrollOffsetPx - consumed.y).coerceIn(0f, maxScrollPx.toFloat())
				}
				return Offset.Zero
			}
		}
	}

	val canScrollUp = hasOverflow && (if (maxScrollPx > 0) scrollOffsetPx > 2f else false)
	val canScrollDown = hasOverflow && (if (maxScrollPx > 0) scrollOffsetPx < (maxScrollPx - 2f) else false)

	val topFadeAlpha by animateFloatAsState(
		targetValue = if (canScrollUp) 1f else 0f,
		animationSpec = tween(
			durationMillis = 280,
			easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1.0f),
		),
		label = "chat_input_top_fade",
	)
	val bottomFadeAlpha by animateFloatAsState(
		targetValue = if (canScrollDown) 1f else 0f,
		animationSpec = tween(
			durationMillis = 280,
			easing = CubicBezierEasing(0.1f, 0.9f, 0.2f, 1.0f),
		),
		label = "chat_input_bottom_fade",
	)

	val isAnyLocked = (isRecordingActive && recordingState.isLocked) || (isVoiceRecording && voiceRecordingState.isLocked)
	val showRecord = showRecordButton && !isEditMode && controller.textFieldValue.text.isEmpty() && !sendButtonActive && imageDrafts.isEmpty() && !hasAnyDraft
	val showRecordingButton = showRecord || isAnyRecording

	var isRecordPressed by remember { mutableStateOf(false) }
	val recordButtonScale by animateFloatAsState(
		targetValue = if (isRecordPressed && !isAnyRecording) 1.18f else 1f,
		animationSpec = spring(
			dampingRatio = 0.55f,
			stiffness = 420f,
		),
		label = "record_button_scale",
	)
	val recordingButtonColor by animateColorAsState(
		targetValue = if (isAnyLocked) MaterialTheme.colorScheme.primary else RecordingInputRed,
		animationSpec = tween(durationMillis = 160),
		label = "recording_input_button_color",
	)
	val currentCancelThreshold = if (isRecordingActive) recordingState.cancelThresholdPx else recordCancelThresholdPx
	val currentLockThreshold = if (isRecordingActive) recordingState.lockThresholdPx else recordLockThresholdPx
	val currentDragOffsetX = if (isRecordingActive) recordingState.dragOffsetX else if (isVoiceRecording) voiceRecordingState.dragOffsetX else 0f
	val currentDragOffsetY = if (isRecordingActive) recordingState.dragOffsetY else if (isVoiceRecording) voiceRecordingState.dragOffsetY else 0f

	val rawRecordingButtonOffsetX = if (isAnyLocked) {
		0f
	} else {
		currentDragOffsetX.coerceIn(-currentCancelThreshold, 0f)
	}
	val rawRecordingButtonOffsetY = if (isAnyLocked) {
		0f
	} else {
		currentDragOffsetY.coerceIn(-currentLockThreshold, 0f)
	}
	val snapRecordingButtonOffsetX by animateFloatAsState(
		targetValue = if (isAnyLocked) 0f else currentDragOffsetX.coerceIn(-currentCancelThreshold, 0f),
		animationSpec = tween(durationMillis = 160),
		label = "recording_input_button_offset_x",
	)
	val snapRecordingButtonOffsetY by animateFloatAsState(
		targetValue = if (isAnyLocked) 0f else currentDragOffsetY.coerceIn(-currentLockThreshold, 0f),
		animationSpec = tween(durationMillis = 180),
		label = "recording_input_button_offset_y",
	)
	val lockedButtonScale by animateFloatAsState(
		targetValue = if (isAnyLocked) 1.07f else 1f,
		animationSpec = spring(
			dampingRatio = 0.48f,
			stiffness = 520f,
		),
		label = "recording_locked_button_scale",
	)
	val currentAudioLevel = if (isRecordingActive) recordingState.audioLevel else voiceRecordingState.audioLevel
	val micPulseScale by animateFloatAsState(
		targetValue = if (isRecordingActive || isVoiceRecording) {
			1f + currentAudioLevel.coerceIn(0f, 1f) * 0.14f
		} else {
			1f
		},
		animationSpec = tween(durationMillis = 70),
		label = "recording_mic_pulse_scale",
	)
	val micCorePulseScale by animateFloatAsState(
		targetValue = if ((isRecordingActive && !recordingState.isLocked) || (isVoiceRecording && !voiceRecordingState.isLocked)) {
			1f + currentAudioLevel.coerceIn(0f, 1f) * 0.55f
		} else {
			1f
		},
		animationSpec = tween(durationMillis = 60),
		label = "recording_mic_core_pulse_scale",
	)
	val boostedAudioLevel = (currentAudioLevel * 4.6f).coerceIn(0f, 1f)
	val micHaloPulseScale by animateFloatAsState(
		targetValue = if (isRecordingActive || isVoiceRecording) {
			1f + boostedAudioLevel * 0.42f
		} else {
			1f
		},
		animationSpec = tween(durationMillis = 85),
		label = "recording_mic_halo_pulse_scale",
	)
	val sendIconScale by animateFloatAsState(
		targetValue = if (recordingState.isLocked || voiceRecordingState.isLocked) 1f else 0.72f,
		animationSpec = spring(
			dampingRatio = 0.56f,
			stiffness = 620f,
		),
		label = "recording_send_icon_scale",
	)

	Box(
		modifier = modifier
			.testTag("chat_input_row")
			.fillMaxWidth()
			.padding(horizontal = 16.dp)
			.padding(top = 8.dp, bottom = 12.dp)
			.clickable(
				interactionSource = inputInteractionSource,
				indication = null,
				enabled = !isAnyRecording && !hasAnyDraft,
			) {
				controller.onInputTapped()
			},
	) {
		// Tail: Vector 82 in Frame 2234
		ChatTailVector(
			color = capsuleBg,
			isOutgoing = true,
			modifier = Modifier
				.align(Alignment.BottomEnd)
				.offset(x = (-8).dp, y = 4.5.dp)
		)

		var capsuleHeightPx by remember { mutableStateOf(0) }
		val density = LocalDensity.current
		val isMultiLine = remember(controller.textFieldValue.text, capsuleHeightPx, isAnyRecording, isVideoPreview) {
			!isAnyRecording && !isVideoPreview && (
				controller.textFieldValue.text.contains('\n') || 
				(capsuleHeightPx > 0 && capsuleHeightPx > with(density) { 56.dp.toPx() })
			)
		}
		val capsuleCornerRadius by animateDpAsState(
			targetValue = if (isMultiLine) 20.dp else 26.dp,
			animationSpec = tween(150),
			label = "capsule_radius"
		)
		val capsuleShape = RoundedCornerShape(capsuleCornerRadius)

		// Capsule: Frame 2232
		Box(
			modifier = Modifier
				.fillMaxWidth()
				.then(
					if (isAnyRecording || isVideoPreview) {
						Modifier.height(50.dp)
					} else {
						Modifier.defaultMinSize(minHeight = 50.dp)
					}
				)
				.onSizeChanged { size ->
					capsuleHeightPx = size.height
				}
				.background(capsuleBg, capsuleShape)
				.padding(
					start = if (isAnyRecording) 7.dp else if (onAttachClick != null && !hasAnyDraft) 13.dp else 16.dp,
					end = 7.dp,
					top = 7.dp,
					bottom = 7.dp,
				),
			contentAlignment = Alignment.BottomStart,
		) {
			if (onAttachClick != null && !isAnyRecording && !hasAnyDraft) {
				Box(
					contentAlignment = Alignment.Center,
					modifier = Modifier
						.align(Alignment.BottomStart)
						.padding(bottom = 4.dp)
						.size(28.dp)
						.clip(CircleShape)
						.clickable(onClick = onAttachClick)
						.testTag("chat_input_attach_button"),
				) {
					Icon(
						painter = painterResource(Res.drawable.chat_clip_icon),
						contentDescription = "Прикрепить",
						tint = iconTint,
						modifier = Modifier.size(28.dp),
					)
				}
			}

			// Center content area
			Box(
				contentAlignment = Alignment.BottomStart,
				modifier = Modifier
					.align(Alignment.BottomStart)
					.fillMaxWidth()
					.padding(
						start = if (onAttachClick != null && !isAnyRecording && !hasAnyDraft) 38.dp else 0.dp,
						end = when {
							isAnyRecording -> if (isAnyLocked) 54.dp else (RecordingInputButtonSize + 8.dp)
							hasAnyDraft -> 40.dp
							showEmojiButton -> 84.dp
							else -> 46.dp
						},
					)
					.testTag("chat_input_text_field"),
			) {
				val showTextField = voiceDraft == null && !isAnyRecording && !isVideoPreview
				Box(
					modifier = Modifier
						.fillMaxWidth()
						.graphicsLayer {
							alpha = if (showTextField) 1f else 0f
						},
				) {
					BasicTextField(
						value = controller.textFieldValue,
						onValueChange = controller::onTextFieldValueChange,
						onTextLayout = { textLayoutResult = it },
						textStyle = LocalTypography.current.titleMedium.copy(
							fontSize = 18.sp,
							lineHeight = 22.sp,
							fontWeight = FontWeight.Normal,
							color = inputTextColor,
						),
						maxLines = 6,
						cursorBrush = SolidColor(if (showTextField) inputTextColor else Color.Transparent),
						modifier = Modifier
							.fillMaxWidth()
							.heightIn(min = 22.dp)
							.padding(top = 7.dp, bottom = 7.dp)
							.nestedScroll(inputNestedScrollConnection)
							.focusRequester(focusRequester)
							.onFocusChanged { focusState ->
								isFocused = focusState.isFocused
								controller.onTextFieldFocusChanged(focusState.isFocused)
							}
							.semantics {
								stateDescription = controller.inputMode.name
							},
						interactionSource = inputInteractionSource,
					) { innerTextField ->
						Box(
							contentAlignment = Alignment.CenterStart,
							modifier = Modifier
								.fillMaxWidth()
								.onSizeChanged { size ->
									textFieldViewportHeightPx = size.height
								}
								.chatInputFadingEdge(
									topAlpha = topFadeAlpha,
									bottomAlpha = bottomFadeAlpha,
									fadeHeight = 18.dp,
								),
						) {
							if (showTextField && controller.textFieldValue.text.isEmpty()) {
								Text(
									text = "Сообщение",
									color = placeholderColor,
									style = LocalTypography.current.titleMedium.copy(
										fontSize = 18.sp,
										lineHeight = 22.sp,
										fontWeight = FontWeight.Normal,
									),
								)
							}
							innerTextField()
						}
					}
				}

				if (voiceDraft != null) {
					Row(
						verticalAlignment = Alignment.CenterVertically,
						modifier = Modifier.fillMaxWidth().height(36.dp),
					) {
						Icon(
							imageVector = Icons.Outlined.Delete,
							contentDescription = "Удалить черновик",
							tint = MaterialTheme.colorScheme.error,
							modifier = Modifier
								.size(24.dp)
								.clip(CircleShape)
								.clickable(onClick = onVoiceDraftDelete)
						)

						Spacer(modifier = Modifier.width(10.dp))

						Box(
							modifier = Modifier
								.size(32.dp)
								.clip(CircleShape)
								.background(sendButtonBg)
								.clickable(onClick = onVoiceDraftPlayPause),
							contentAlignment = Alignment.Center
						) {
							Icon(
								imageVector = if (voiceDraft.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
								contentDescription = if (voiceDraft.isPlaying) "Пауза" else "Слушать",
								tint = Color.White,
								modifier = Modifier.size(18.dp)
							)
						}

						Spacer(modifier = Modifier.width(10.dp))

						val totalDuration = voiceDraft.durationMs.coerceAtLeast(1L)
						val draftProgress = (voiceDraft.currentPositionMs.toFloat() / totalDuration).coerceIn(0f, 1f)
						WaveformBars(
							waveform = voiceDraft.waveform,
							progress = draftProgress,
							activeColor = sendButtonBg,
							inactiveColor = (if (isDark) Color.White else Color.Black).copy(alpha = 0.25f),
							onSeek = onVoiceDraftSeek,
							modifier = Modifier
								.weight(1f)
								.height(24.dp)
						)

						Spacer(modifier = Modifier.width(8.dp))

						val timeDisplay = if (voiceDraft.isPlaying) voiceDraft.currentPositionMs else voiceDraft.durationMs
						Text(
							text = formatDuration(timeDisplay),
							style = LocalTypography.current.labelMedium.copy(
								fontSize = 11.sp,
								color = iconTint
							)
						)
					}
				} else if (isAnyRecording || isVideoPreview) {
					val activeRecordingState = if (voiceRecordingState.isRecording) {
						voiceRecordingState.toChatMediaRecordingState()
					} else {
						recordingState.toChatMediaRecordingState()
					}
					UnifiedRecordingCapsule(
						state = activeRecordingState,
						onCancelClick = onRecordSwipeLeft,
						onStopClick = onRecordStopClick,
						modifier = Modifier.height(36.dp),
					)
				}
			}

			// Emoji toggle button
			if (showEmojiButton && !isAnyRecording && !hasAnyDraft) {
				val isEmojiMode = controller.inputMode == ChatInputMode.Emoji
				val toggleStateDescription = if (isEmojiMode) "keyboard" else "emoji"

				Box(
					contentAlignment = Alignment.Center,
					modifier = Modifier
						.align(Alignment.BottomEnd)
						.padding(end = 46.dp, bottom = 4.dp)
						.size(28.dp)
						.clip(CircleShape)
						.clickable {
							haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
							controller.toggleEmoji()
						}
						.testTag("chat_input_toggle_button")
						.semantics { stateDescription = toggleStateDescription },
				) {
					AnimatedContent(
						targetState = isEmojiMode,
						transitionSpec = {
							val springSpec = spring<Float>(
								dampingRatio = Spring.DampingRatioMediumBouncy,
								stiffness = Spring.StiffnessMediumLow,
							)
							(scaleIn(initialScale = 0.55f, animationSpec = springSpec) + fadeIn(tween(140)))
								.togetherWith(scaleOut(targetScale = 0.55f, animationSpec = tween(120)) + fadeOut(tween(120)))
						},
						label = "chat_input_emoji_toggle",
					) { inEmoji ->
						Icon(
							painter = painterResource(
								if (inEmoji) Res.drawable.chat_keyboard_icon else Res.drawable.chat_emoji_icon
							),
							contentDescription = toggleStateDescription,
							tint = iconTint,
							modifier = Modifier.size(24.dp),
						)
					}
				}
			}

			// Send / Action button
			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.testTag(if (showRecordingButton) "chat_input_record_button" else "chat_input_send_button")
					.size(36.dp)
					.graphicsLayer {
						if (isAnyRecording) {
							translationX = if (isAnyLocked) snapRecordingButtonOffsetX else rawRecordingButtonOffsetX
							translationY = if (isAnyLocked) snapRecordingButtonOffsetY else rawRecordingButtonOffsetY
							scaleX = lockedButtonScale * micPulseScale
							scaleY = lockedButtonScale * micPulseScale
						} else {
							scaleX = recordButtonScale
							scaleY = recordButtonScale
						}
					}
					.then(
						if (isAnyRecording) {
							Modifier
						} else {
							Modifier
								.clip(CircleShape)
								.background(sendButtonBg)
						}
					)
					.onGloballyPositioned { coordinates ->
						onRecordButtonBoundsChanged(
							if (isAnyRecording) coordinates.boundsInRoot() else null,
						)
					}
					.then(
						when {
							hasVoiceDraft -> {
								Modifier.clickable(onClick = onVoiceDraftSend)
							}
							isVideoPreview -> {
								Modifier.clickable(onClick = onRecordSendClick)
							}
							isVoiceRecording && voiceRecordingState.isLocked -> {
								Modifier.clickable(onClick = onVoiceDraftSend)
							}
							isRecordingActive && recordingState.isLocked -> {
								Modifier.clickable(onClick = onRecordSendClick)
							}
							showRecord && !isAnyLocked -> {
								Modifier.recordingButtonGesture(
									cancelThresholdPx = recordCancelThresholdPx,
									lockThresholdPx = recordLockThresholdPx,
									touchSlop = viewConfiguration.touchSlop,
									onTap = {
										haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
										onRecordButtonModeToggle()
									},
									onPress = onRecordButtonPress,
									onRelease = onRecordButtonRelease,
									onSwipeUp = onRecordSwipeUp,
									onSwipeLeft = onRecordSwipeLeft,
									onDrag = onRecordDrag,
									onPressStateChanged = { isRecordPressed = it },
								)
							}
							else -> {
								Modifier.clickable(
									enabled = sendButtonActive || isEditMode,
									onClick = {
										when {
											isRecordingActive || isVideoPreview -> onRecordSendClick()
											sendButtonActive || isEditMode -> onSendClick()
										}
									}
								)
							}
						}
					),
			) {
				when {
					isAnyRecording -> {
						RecordingButtonContent(
							isLocked = isAnyLocked,
							isRecording = (isRecordingActive && recordingState.isRecording) || isVoiceRecording,
							recordingButtonColor = recordingButtonColor,
							micCorePulseScale = micCorePulseScale,
							micHaloPulseScale = micHaloPulseScale,
							boostedAudioLevel = boostedAudioLevel,
							sendIconScale = sendIconScale,
						)
					}
					hasVoiceDraft -> {
						Icon(
							painter = painterResource(Res.drawable.chat_send_arrow),
							tint = Color.White,
							contentDescription = "Отправить голосовое",
							modifier = Modifier.size(width = 14.dp, height = 17.dp),
						)
					}
					isVideoPreview -> {
						Icon(
							painter = painterResource(Res.drawable.chat_send_arrow),
							tint = Color.White,
							contentDescription = "Отправить видео",
							modifier = Modifier.size(width = 14.dp, height = 17.dp),
						)
					}
					showRecord -> {
						AnimatedContent(
							targetState = recordButtonMode,
							transitionSpec = {
								val springSpec = spring<Float>(
									dampingRatio = 0.72f,
									stiffness = Spring.StiffnessMediumLow,
								)
								(scaleIn(initialScale = 0.5f, animationSpec = springSpec) + fadeIn(tween(150)))
									.togetherWith(scaleOut(targetScale = 0.5f, animationSpec = tween(120)) + fadeOut(tween(120)))
							},
							label = "chat_record_button_mode_toggle",
						) { mode ->
							val isVoice = mode == ChatRecordButtonMode.Voice
							Icon(
								painter = painterResource(
									if (isVoice) Res.drawable.chat_mic_icon else Res.drawable.chat_video_round_icon
								),
								tint = Color.White,
								contentDescription = if (isVoice) "record voice" else "record video",
								modifier = Modifier.size(22.dp),
							)
						}
					}
					else -> {
						Icon(
							painter = painterResource(
								if (isEditMode) Res.drawable.done_icon else Res.drawable.chat_send_arrow,
							),
							tint = Color.White.copy(alpha = if (sendButtonActive || isEditMode) 1f else 0.4f),
							contentDescription = if (isEditMode) "Готово" else "Отправить",
							modifier = Modifier.size(if (isEditMode) 18.dp else 17.dp),
						)
					}
				}
			}
		}
	}
}

@Composable
private fun sendButtonColor(sendButtonActive: Boolean) =
	if (sendButtonActive) MaterialTheme.colorScheme.primary
	else Color(0xFF868686)

@Composable
private fun RecordingButtonContent(
	isLocked: Boolean,
	isRecording: Boolean,
	recordingButtonColor: Color,
	micCorePulseScale: Float,
	micHaloPulseScale: Float,
	boostedAudioLevel: Float,
	sendIconScale: Float,
) {
	if (isRecording && !isLocked) {
		Box(
			modifier = Modifier
				.wrapContentSize(Alignment.Center, unbounded = true)
				.size(RecordingInputHaloSize)
				.graphicsLayer {
					scaleX = micHaloPulseScale
					scaleY = micHaloPulseScale
					alpha = boostedAudioLevel * 0.42f
				}
				.clip(CircleShape)
				.background(recordingButtonColor),
		)
	}
	Box(
		modifier = Modifier
			.wrapContentSize(Alignment.Center, unbounded = true)
			.size(if (isLocked) 36.dp else RecordingInputButtonSize)
			.clip(CircleShape)
			.background(recordingButtonColor),
		contentAlignment = Alignment.Center,
	) {
		if (isLocked) {
			Icon(
				painter = painterResource(Res.drawable.send_icon),
				tint = Color.White,
				contentDescription = "send recording",
				modifier = Modifier
					.size(18.dp)
					.graphicsLayer {
						scaleX = sendIconScale
						scaleY = sendIconScale
					},
			)
		} else {
			Box(
				modifier = Modifier
					.size(27.dp)
					.graphicsLayer {
						scaleX = micCorePulseScale
						scaleY = micCorePulseScale
					}
					.clip(CircleShape)
					.background(Color.White),
			)
		}
	}
}

private fun Modifier.chatInputFadingEdge(
	topAlpha: Float,
	bottomAlpha: Float,
	fadeHeight: Dp = 13.dp,
): Modifier = this
	.graphicsLayer {
		if (topAlpha > 0f || bottomAlpha > 0f) {
			compositingStrategy = CompositingStrategy.Offscreen
		}
	}
	.drawWithContent {
		drawContent()
		val fadePx = fadeHeight.toPx()
		if (topAlpha > 0f) {
			drawRect(
				brush = Brush.verticalGradient(
					0f to Color.Black,
					1f to Color.Transparent,
					startY = 0f,
					endY = fadePx,
				),
				blendMode = BlendMode.DstOut,
				alpha = topAlpha,
			)
		}
		if (bottomAlpha > 0f) {
			drawRect(
				brush = Brush.verticalGradient(
					0f to Color.Transparent,
					1f to Color.Black,
					startY = size.height - fadePx,
					endY = size.height,
				),
				blendMode = BlendMode.DstOut,
				alpha = bottomAlpha,
			)
		}
	}

