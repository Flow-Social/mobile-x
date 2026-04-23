package me.floow.uikit.chat.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material.icons.outlined.KeyboardAlt
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
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
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
import flow.core.uikit.generated.resources.emoji_picker_icon
import flow.core.uikit.generated.resources.send_icon
import me.floow.uikit.chat.input.ChatInputController
import me.floow.uikit.chat.input.ChatInputMode
import me.floow.uikit.chat.model.VideoRecordingMode
import me.floow.uikit.chat.model.VideoRecordingState
import me.floow.uikit.theme.LocalTypography
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

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
private val RecordingInputButtonSize = 76.dp
private val RecordingInputHaloSize = 94.dp
private val RecordingInputButtonOffsetX = 33.dp
private val RecordingInputButtonOffsetY = 0.dp
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
	onRecordButtonBoundsChanged: (Rect?) -> Unit = {},
	recordingState: VideoRecordingState = VideoRecordingState(),
	recordCancelThresholdPx: Float = RecordCancelThresholdPx,
	recordLockThresholdPx: Float = 180f,
	modifier: Modifier = Modifier,
) {
	val focusRequester = remember { FocusRequester() }
	val focusManager = LocalFocusManager.current
	val keyboardController = LocalSoftwareKeyboardController.current
	val viewConfiguration = LocalViewConfiguration.current
	val inputInteractionSource = remember { MutableInteractionSource() }
	var isFocused by remember { mutableStateOf(false) }
	val isRecordingActive = recordingState.mode == VideoRecordingMode.Recording

	LaunchedEffect(controller.keyboardRequestToken) {
		if (controller.keyboardRequestToken == 0) return@LaunchedEffect
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

	Box(
		modifier = modifier
			.testTag("chat_input_row")
			.then(
				if (isRecordingActive) {
					Modifier.height(ChatInputMinHeight)
				} else {
					Modifier.defaultMinSize(minHeight = ChatInputMinHeight)
				}
			)
			.padding(horizontal = ChatInputSidePadding)
			.clickable(
				interactionSource = inputInteractionSource,
				indication = null,
				enabled = !isRecordingActive,
			) {
				controller.onInputTapped()
			},
	) {
		if (showEmojiButton && !isRecordingActive) {
			val toggleStateDescription = if (controller.inputMode == ChatInputMode.Emoji) "keyboard" else "emoji"

			Box(
				contentAlignment = Alignment.Center,
				modifier = Modifier
					.align(Alignment.BottomStart)
					.testTag("chat_input_toggle_button")
					.semantics { stateDescription = toggleStateDescription }
					.padding(bottom = ChatInputIconBottomInset)
					.size(ChatInputIconSize)
					.clip(CircleShape)
					.clickable { controller.toggleEmoji() },
			) {
				if (controller.inputMode == ChatInputMode.Emoji) {
					Icon(
						imageVector = Icons.Outlined.KeyboardAlt,
						contentDescription = toggleStateDescription,
						tint = Color(0xFF8E959B),
						modifier = Modifier.size(ChatInputKeyboardGlyphSize),
					)
				} else {
					Icon(
						painter = painterResource(Res.drawable.emoji_picker_icon),
						contentDescription = toggleStateDescription,
						tint = Color(0xFF8E959B),
						modifier = Modifier.size(ChatInputGlyphSize),
					)
				}
			}
		}

		Box(
			contentAlignment = Alignment.BottomStart,
			modifier = Modifier
				.align(Alignment.BottomStart)
				.fillMaxWidth()
				.padding(
					start = if (showEmojiButton && !isRecordingActive) ChatInputTextStartInset else 0.dp,
					end = if (isRecordingActive) RecordingInputButtonSize + ChatInputSidePadding else ChatInputTextEndInset,
				)
				.padding(bottom = ChatInputTextBottomInset)
				.testTag("chat_input_text_field"),
		) {
			if (isRecordingActive) {
				val dismissProgress by animateFloatAsState(
					targetValue = recordingState.cancelProgress,
					animationSpec = tween(durationMillis = 220),
					label = "recording_input_dismiss_progress",
				)
				Text(
					text = if (recordingState.isLocked) stringResource(Res.string.chat_recording_cancel) else "← swipe to dismiss",
					color = if (recordingState.isLocked) {
						MaterialTheme.colorScheme.primary
					} else {
						MaterialTheme.colorScheme.onSurface.copy(alpha = 0.82f)
					},
					style = LocalTypography.current.titleMedium.copy(fontSize = 14.sp),
					fontWeight = if (recordingState.isLocked) FontWeight.SemiBold else FontWeight.Normal,
					modifier = Modifier
						.then(
							if (recordingState.isLocked) {
								Modifier.clickable(onClick = onRecordSwipeLeft)
							} else {
								Modifier
							},
						)
						.graphicsLayer {
							alpha = if (recordingState.isLocked) 1f else 1f - dismissProgress * 0.55f
							scaleX = 1f - dismissProgress * 0.04f
							scaleY = 1f - dismissProgress * 0.04f
						},
				)
			} else {
				BasicTextField(
					value = controller.textFieldValue,
					onValueChange = controller::onTextFieldValueChange,
					textStyle = LocalTypography.current.titleMedium.copy(
						fontSize = ChatInputTextSize,
						fontWeight = FontWeight.Normal,
						color = MaterialTheme.colorScheme.onBackground,
					),
					maxLines = 8,
					cursorBrush = SolidColor(MaterialTheme.colorScheme.onBackground),
					modifier = Modifier
						.fillMaxWidth()
						.heightIn(min = 22.dp)
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
					Box {
						if (controller.textFieldValue.text.isEmpty()) {
							Text(
								text = "Сообщение",
								color = Color.Gray,
								style = LocalTypography.current.titleMedium.copy(fontSize = ChatInputTextSize),
								fontWeight = FontWeight.Normal,
							)
						}
						innerTextField()
					}
				}
			}
		}

		val showRecord = showRecordButton && !isEditMode && controller.textFieldValue.text.isEmpty() && !sendButtonActive
		val showRecordingButton = showRecord || isRecordingActive

		var isRecordPressed by remember { mutableStateOf(false) }
		val recordButtonScale by animateFloatAsState(
			targetValue = if (isRecordPressed && !isRecordingActive) 1.18f else 1f,
			animationSpec = spring(
				dampingRatio = 0.55f,
				stiffness = 420f,
			),
			label = "record_button_scale",
		)
		val recordingButtonColor by animateColorAsState(
			targetValue = if (recordingState.isLocked) MaterialTheme.colorScheme.primary else RecordingInputRed,
			animationSpec = tween(durationMillis = 160),
			label = "recording_input_button_color",
		)
		val rawRecordingButtonOffsetX = if (recordingState.isLocked) {
			0f
		} else {
			recordingState.dragOffsetX.coerceIn(-recordingState.cancelThresholdPx, 0f)
		}
		val rawRecordingButtonOffsetY = if (recordingState.isLocked) {
			0f
		} else {
			recordingState.dragOffsetY.coerceIn(-recordingState.lockThresholdPx, 0f)
		}
		val snapRecordingButtonOffsetX by animateFloatAsState(
			targetValue = if (recordingState.isLocked) 0f else recordingState.dragOffsetX.coerceIn(-recordingState.cancelThresholdPx, 0f),
			animationSpec = tween(durationMillis = 160),
			label = "recording_input_button_offset_x",
		)
		val snapRecordingButtonOffsetY by animateFloatAsState(
			targetValue = if (recordingState.isLocked) 0f else recordingState.dragOffsetY.coerceIn(-recordingState.lockThresholdPx, 0f),
			animationSpec = tween(durationMillis = 180),
			label = "recording_input_button_offset_y",
		)
		val lockedButtonScale by animateFloatAsState(
			targetValue = if (recordingState.isLocked) 1.07f else 1f,
			animationSpec = spring(
				dampingRatio = 0.48f,
				stiffness = 520f,
			),
			label = "recording_locked_button_scale",
		)
		val micPulseScale by animateFloatAsState(
			targetValue = if (isRecordingActive) {
				1f + recordingState.audioLevel.coerceIn(0f, 1f) * 0.14f
			} else {
				1f
			},
			animationSpec = tween(durationMillis = 70),
			label = "recording_mic_pulse_scale",
		)
		val micCorePulseScale by animateFloatAsState(
			targetValue = if (isRecordingActive && !recordingState.isLocked) {
				1f + recordingState.audioLevel.coerceIn(0f, 1f) * 0.55f
			} else {
				1f
			},
			animationSpec = tween(durationMillis = 60),
			label = "recording_mic_core_pulse_scale",
		)
		val boostedAudioLevel = (recordingState.audioLevel * 4.6f).coerceIn(0f, 1f)
		val micHaloPulseScale by animateFloatAsState(
			targetValue = if (isRecordingActive) {
				1f + boostedAudioLevel * 0.42f
			} else {
				1f
			},
			animationSpec = tween(durationMillis = 85),
			label = "recording_mic_halo_pulse_scale",
		)
		val sendIconScale by animateFloatAsState(
			targetValue = if (recordingState.isLocked) 1f else 0.72f,
			animationSpec = spring(
				dampingRatio = 0.56f,
				stiffness = 620f,
			),
			label = "recording_send_icon_scale",
		)

		Box(
			contentAlignment = Alignment.Center,
			modifier = Modifier
				.align(Alignment.BottomEnd)
				.testTag(if (showRecordingButton) "chat_input_record_button" else "chat_input_send_button")
				.padding(bottom = if (isRecordingActive) 0.dp else ChatInputIconBottomInset)
				.offset(
					x = if (isRecordingActive) RecordingInputButtonOffsetX else 0.dp,
					y = if (isRecordingActive) RecordingInputButtonOffsetY else 0.dp,
				)
				.then(
					if (isRecordingActive) {
						Modifier.requiredSize(RecordingInputHaloSize)
					} else {
						Modifier.size(ChatInputIconSize)
					}
				)
				.graphicsLayer {
					if (isRecordingActive) {
						translationX = if (recordingState.isLocked) snapRecordingButtonOffsetX else rawRecordingButtonOffsetX
						translationY = if (recordingState.isLocked) snapRecordingButtonOffsetY else rawRecordingButtonOffsetY
						scaleX = lockedButtonScale * micPulseScale
						scaleY = lockedButtonScale * micPulseScale
					} else {
						scaleX = recordButtonScale
						scaleY = recordButtonScale
					}
				}
				.clip(CircleShape)
				.onGloballyPositioned { coordinates ->
					onRecordButtonBoundsChanged(
						if (isRecordingActive) coordinates.boundsInRoot() else null,
					)
				}
				.then(
					if (showRecord && !recordingState.isLocked) {
						Modifier.recordingButtonGesture(
							cancelThresholdPx = recordCancelThresholdPx,
							lockThresholdPx = recordLockThresholdPx,
							touchSlop = viewConfiguration.touchSlop,
							onPress = onRecordButtonPress,
							onRelease = onRecordButtonRelease,
							onSwipeUp = onRecordSwipeUp,
							onSwipeLeft = onRecordSwipeLeft,
							onDrag = onRecordDrag,
							onPressStateChanged = { isRecordPressed = it },
						)
					} else {
						Modifier.clickable {
							when {
								isRecordingActive -> onRecordStopClick()
								sendButtonActive -> onSendClick()
							}
						}
					}
				),
		) {
			if (isRecordingActive) {
				RecordingButtonContent(
					recordingState = recordingState,
					recordingButtonColor = recordingButtonColor,
					micCorePulseScale = micCorePulseScale,
					micHaloPulseScale = micHaloPulseScale,
					boostedAudioLevel = boostedAudioLevel,
					sendIconScale = sendIconScale,
				)
			} else if (showRecord) {
				Icon(
					imageVector = Icons.Filled.Videocam,
					tint = if (isRecordPressed) Color(0xFFA855F7) else Color(0xFF8A8F98),
					contentDescription = "record video",
					modifier = Modifier.size(ChatInputGlyphSize + 2.dp),
				)
			} else {
				Icon(
					painter = painterResource(
						if (isEditMode) Res.drawable.done_icon else Res.drawable.send_icon,
					),
					tint = sendButtonColor(sendButtonActive),
					contentDescription = null,
					modifier = Modifier.size(ChatInputGlyphSize),
				)
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
	recordingState: VideoRecordingState,
	recordingButtonColor: Color,
	micCorePulseScale: Float,
	micHaloPulseScale: Float,
	boostedAudioLevel: Float,
	sendIconScale: Float,
) {
	if (recordingState.isRecording) {
		Box(
			modifier = Modifier
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
			.size(RecordingInputButtonSize)
			.clip(CircleShape)
			.background(recordingButtonColor),
		contentAlignment = Alignment.Center,
	) {
		if (recordingState.isLocked) {
			Icon(
				painter = painterResource(Res.drawable.send_icon),
				tint = Color.White,
				contentDescription = "send recording",
				modifier = Modifier
					.size(30.dp)
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
