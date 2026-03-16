package me.floow.uikit.chat.input

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

enum class ChatInputMode {
	None,
	Keyboard,
	Emoji
}

private enum class ChatInputUiState {
	Closed,
	EmojiVisible,
	KeyboardRequested,
	KeyboardVisible,
	KeyboardClosing,
	SwitchingEmojiToKeyboard
}

private const val CHAT_INPUT_DEBUG_TAG = "ChatInputDebug"
private const val MIN_VALID_KEYBOARD_HEIGHT_PX = 200
private const val STABLE_KEYBOARD_HEIGHT_DELTA_PX = 24
private const val STABLE_KEYBOARD_HEIGHT_LARGE_SHIFT_PX = 96

@Stable
class ChatInputController internal constructor(
	initialText: String,
	private val maxLength: Int?,
	private val fallbackPanelHeightPx: Int,
) {
	internal var onTextChanged: (String) -> Unit = {}

	private var uiState by mutableStateOf(ChatInputUiState.Closed)

	var textFieldValue by mutableStateOf(
		TextFieldValue(
			text = initialText,
			selection = TextRange(initialText.length)
		)
	)
		private set

	var imeHeightPx by mutableIntStateOf(0)
		private set

	var stableKeyboardHeightPx by mutableIntStateOf(0)
		private set

	private var lastKeyboardSampleHeightPx by mutableIntStateOf(0)
	private var consecutiveNearKeyboardSamples by mutableIntStateOf(0)
	private var keyboardHandoffHeightPx by mutableIntStateOf(0)
	private var keepEmojiModeUntilImeHidden by mutableStateOf(false)

	var keyboardRequestToken by mutableIntStateOf(0)
		private set

	var hideImeToken by mutableIntStateOf(0)
		private set

	private var isTextFieldFocused by mutableStateOf(false)
	private var imeWasOpenDuringCurrentSession by mutableStateOf(false)
	private var shouldRestoreKeyboardOnResume by mutableStateOf(false)

	val inputMode: ChatInputMode
		get() = when (uiState) {
			ChatInputUiState.Closed -> ChatInputMode.None
			ChatInputUiState.EmojiVisible -> ChatInputMode.Emoji
			ChatInputUiState.KeyboardRequested,
			ChatInputUiState.KeyboardVisible,
			ChatInputUiState.KeyboardClosing,
			ChatInputUiState.SwitchingEmojiToKeyboard -> ChatInputMode.Keyboard
		}

	val keyboardLayoutHeightPx: Int
		get() = when (uiState) {
			ChatInputUiState.KeyboardRequested -> max(
				imeHeightPx,
				max(stableKeyboardHeightPx, fallbackPanelHeightPx)
			)
			ChatInputUiState.KeyboardVisible -> imeHeightPx
			ChatInputUiState.KeyboardClosing -> imeHeightPx
			ChatInputUiState.SwitchingEmojiToKeyboard -> max(imeHeightPx, keyboardHandoffHeightPx)
			ChatInputUiState.Closed,
			ChatInputUiState.EmojiVisible -> 0
		}

	val emojiPanelHeightPx: Int
		get() = if (uiState == ChatInputUiState.EmojiVisible) {
			max(stableKeyboardHeightPx, fallbackPanelHeightPx)
		} else {
			0
		}

	fun onTextFieldValueChange(value: TextFieldValue) {
		val trimmedText = value.text.trimToMaxLength()
		val constrainedSelection = TextRange(
			start = value.selection.start.coerceIn(0, trimmedText.length),
			end = value.selection.end.coerceIn(0, trimmedText.length)
		)
		val constrainedComposition = value.composition?.let { composition ->
			TextRange(
				start = composition.start.coerceIn(0, trimmedText.length),
				end = composition.end.coerceIn(0, trimmedText.length)
			)
		}

		textFieldValue = value.copy(
			text = trimmedText,
			selection = constrainedSelection,
			composition = constrainedComposition
		)
		onTextChanged(trimmedText)
	}

	fun syncExternalText(text: String) {
		val normalizedText = text.trimToMaxLength()
		if (normalizedText == textFieldValue.text) return

		textFieldValue = textFieldValue.copy(
			text = normalizedText,
			selection = TextRange(
				start = textFieldValue.selection.start.coerceIn(0, normalizedText.length),
				end = textFieldValue.selection.end.coerceIn(0, normalizedText.length)
			),
			composition = null
		)
	}

	fun syncPersistedKeyboardHeight(heightPx: Int) {
		if (heightPx < MIN_VALID_KEYBOARD_HEIGHT_PX) return
		if (imeHeightPx > 0 || uiState == ChatInputUiState.SwitchingEmojiToKeyboard) return
		if (uiState == ChatInputUiState.KeyboardClosing) return
		if (inputMode == ChatInputMode.Keyboard && isTextFieldFocused) return

		stableKeyboardHeightPx = heightPx
		lastKeyboardSampleHeightPx = heightPx
		consecutiveNearKeyboardSamples = 0
		logState("syncPersistedKeyboardHeight($heightPx)")
	}

	fun onImeHeightChanged(heightPx: Int) {
		imeHeightPx = heightPx.coerceAtLeast(0)
		if (imeHeightPx > 0) {
			imeWasOpenDuringCurrentSession = true
			updateStableKeyboardHeight(imeHeightPx)
			if (uiState == ChatInputUiState.SwitchingEmojiToKeyboard && imeHeightPx >= keyboardHandoffHeightPx) {
				clearKeyboardHandoff()
				uiState = ChatInputUiState.KeyboardVisible
			}
			when (uiState) {
				ChatInputUiState.KeyboardRequested -> uiState = ChatInputUiState.KeyboardVisible
				ChatInputUiState.KeyboardVisible,
				ChatInputUiState.KeyboardClosing,
				ChatInputUiState.SwitchingEmojiToKeyboard -> Unit
				ChatInputUiState.EmojiVisible -> {
					if (!keepEmojiModeUntilImeHidden) {
						if (imeHeightPx < max(stableKeyboardHeightPx, fallbackPanelHeightPx)) {
							keyboardHandoffHeightPx = max(stableKeyboardHeightPx, fallbackPanelHeightPx)
							uiState = ChatInputUiState.SwitchingEmojiToKeyboard
						} else {
							clearKeyboardHandoff()
							uiState = ChatInputUiState.KeyboardVisible
						}
					}
				}
				ChatInputUiState.Closed -> {
					if (isTextFieldFocused) {
						uiState = ChatInputUiState.KeyboardVisible
					}
				}
			}
		} else {
			val shouldClose = when (uiState) {
				ChatInputUiState.KeyboardVisible,
				ChatInputUiState.KeyboardClosing -> true
				ChatInputUiState.SwitchingEmojiToKeyboard ->
					!isTextFieldFocused || imeWasOpenDuringCurrentSession
				else -> false
			}
			if (shouldClose) {
				resetKeyboardSampling()
				clearKeyboardHandoff()
				uiState = ChatInputUiState.Closed
				imeWasOpenDuringCurrentSession = false
			}
			keepEmojiModeUntilImeHidden = false
		}
		logState("onImeHeightChanged($heightPx)")
	}

	fun insertEmoji(emoji: String) {
		val current = textFieldValue
		val start = min(current.selection.start, current.selection.end)
			.coerceIn(0, current.text.length)
		val end = max(current.selection.start, current.selection.end)
			.coerceIn(0, current.text.length)
		val updatedText = buildString(current.text.length + emoji.length) {
			append(current.text.substring(0, start))
			append(emoji)
			append(current.text.substring(end))
		}.trimToMaxLength()
		val newCursor = (start + emoji.length).coerceAtMost(updatedText.length)

		textFieldValue = current.copy(
			text = updatedText,
			selection = TextRange(newCursor),
			composition = null
		)
		onTextChanged(updatedText)
	}

	fun deleteBackward() {
		val current = textFieldValue
		val start = min(current.selection.start, current.selection.end)
			.coerceIn(0, current.text.length)
		val end = max(current.selection.start, current.selection.end)
			.coerceIn(0, current.text.length)

		if (start == 0 && end == 0) return

		val deleteStart = if (start != end) {
			start
		} else {
			findPreviousGraphemeBoundary(current.text, start)
		}

		val updatedText = buildString(current.text.length - (end - deleteStart)) {
			append(current.text.substring(0, deleteStart))
			append(current.text.substring(end))
		}

		textFieldValue = current.copy(
			text = updatedText,
			selection = TextRange(deleteStart),
			composition = null
		)
		onTextChanged(updatedText)
	}

	fun openKeyboard() {
		keepEmojiModeUntilImeHidden = false
		imeWasOpenDuringCurrentSession = false
		when (uiState) {
			ChatInputUiState.EmojiVisible -> {
				keyboardHandoffHeightPx = max(stableKeyboardHeightPx, fallbackPanelHeightPx)
				uiState = ChatInputUiState.SwitchingEmojiToKeyboard
			}
			ChatInputUiState.KeyboardClosing -> {
				uiState = if (imeHeightPx > 0) ChatInputUiState.KeyboardVisible else ChatInputUiState.KeyboardRequested
			}
			ChatInputUiState.KeyboardRequested,
			ChatInputUiState.KeyboardVisible,
			ChatInputUiState.SwitchingEmojiToKeyboard -> Unit
			ChatInputUiState.Closed -> {
				clearKeyboardHandoff()
				uiState = ChatInputUiState.KeyboardRequested
			}
		}
		keyboardRequestToken += 1
		logState("openKeyboard()")
	}

	fun openEmoji() {
		if (imeHeightPx >= MIN_VALID_KEYBOARD_HEIGHT_PX) {
			stableKeyboardHeightPx = imeHeightPx
		}
		keepEmojiModeUntilImeHidden = imeHeightPx > 0
		clearKeyboardHandoff()
		uiState = ChatInputUiState.EmojiVisible
		logState("openEmoji()")
	}

	fun toggleEmoji() {
		if (inputMode == ChatInputMode.Emoji) {
			openKeyboard()
		} else {
			openEmoji()
		}
	}

	fun onInputTapped() {
		if (inputMode != ChatInputMode.Keyboard || imeHeightPx == 0 || !isTextFieldFocused) {
			openKeyboard()
		}
	}

	fun closeInput() {
		keepEmojiModeUntilImeHidden = false
		clearKeyboardHandoff()
		imeWasOpenDuringCurrentSession = false
		if (imeHeightPx > 0) {
			hideImeToken += 1
			uiState = ChatInputUiState.KeyboardClosing
		} else {
			hideImeToken += 1
			uiState = ChatInputUiState.Closed
		}
		logState("closeInput()")
	}

	fun onTextFieldFocusChanged(isFocused: Boolean) {
		isTextFieldFocused = isFocused
		if (isFocused && uiState == ChatInputUiState.EmojiVisible) {
			keyboardHandoffHeightPx = max(stableKeyboardHeightPx, fallbackPanelHeightPx)
			imeWasOpenDuringCurrentSession = false
			uiState = ChatInputUiState.SwitchingEmojiToKeyboard
			logState("onTextFieldFocusChanged(isFocused=true) → emoji_to_keyboard_handoff")
			return
		}
		if (isFocused && uiState == ChatInputUiState.KeyboardClosing) {
			uiState = if (imeHeightPx > 0) ChatInputUiState.KeyboardVisible else ChatInputUiState.KeyboardRequested
			logState("onTextFieldFocusChanged(isFocused=true) → cancel_closing")
			return
		}
		if (!isFocused && imeHeightPx == 0) {
			val shouldClose = when (uiState) {
				ChatInputUiState.KeyboardVisible,
				ChatInputUiState.KeyboardClosing,
				ChatInputUiState.KeyboardRequested -> true
				else -> false
			}
			if (shouldClose) {
				keepEmojiModeUntilImeHidden = false
				clearKeyboardHandoff()
				uiState = ChatInputUiState.Closed
				logState("onTextFieldFocusChanged(isFocused=false) → closed")
				return
			}
		}
		logState("onTextFieldFocusChanged(isFocused=$isFocused)")
	}

	fun onHostPaused() {
		val wasFocused = isTextFieldFocused
		val wasKeyboardMode = inputMode == ChatInputMode.Keyboard || uiState == ChatInputUiState.KeyboardClosing
		shouldRestoreKeyboardOnResume = wasFocused && wasKeyboardMode
		isTextFieldFocused = false
		if (wasKeyboardMode) {
			keepEmojiModeUntilImeHidden = false
			clearKeyboardHandoff()
			uiState = ChatInputUiState.Closed
			logState("onHostPaused() → forced_close restoreOnResume=$shouldRestoreKeyboardOnResume")
			return
		}
		logState("onHostPaused()")
	}

	fun onHostResumed() {
		if (shouldRestoreKeyboardOnResume) {
			shouldRestoreKeyboardOnResume = false
			imeWasOpenDuringCurrentSession = false
			keepEmojiModeUntilImeHidden = false
			uiState = ChatInputUiState.KeyboardRequested
			keyboardRequestToken += 1
			logState("onHostResumed() → restore_keyboard")
			return
		}
		if ((inputMode == ChatInputMode.Keyboard || uiState == ChatInputUiState.KeyboardClosing) &&
			imeHeightPx == 0 && !isTextFieldFocused
		) {
			keepEmojiModeUntilImeHidden = false
			clearKeyboardHandoff()
			uiState = ChatInputUiState.Closed
			logState("onHostResumed() → forced_close")
			return
		}
		logState("onHostResumed()")
	}

	fun handleBack(): Boolean {
		return when (uiState) {
			ChatInputUiState.Closed -> false
			ChatInputUiState.KeyboardClosing -> false
			else -> {
				closeInput()
				true
			}
		}
	}

	private fun String.trimToMaxLength(): String {
		return if (maxLength != null && codePointCount(0, length) > maxLength) {
			substring(0, offsetByCodePoints(0, maxLength))
		} else {
			this
		}
	}

	private fun updateStableKeyboardHeight(heightPx: Int) {
		if (heightPx < MIN_VALID_KEYBOARD_HEIGHT_PX) return

		if (stableKeyboardHeightPx == 0) {
			stableKeyboardHeightPx = heightPx
			lastKeyboardSampleHeightPx = heightPx
			consecutiveNearKeyboardSamples = 0
			return
		}

		val isNearLastSample = lastKeyboardSampleHeightPx > 0 &&
			abs(heightPx - lastKeyboardSampleHeightPx) <= STABLE_KEYBOARD_HEIGHT_DELTA_PX
		consecutiveNearKeyboardSamples = if (isNearLastSample) {
			consecutiveNearKeyboardSamples + 1
		} else {
			0
		}
		lastKeyboardSampleHeightPx = heightPx

		val isNearStable = abs(heightPx - stableKeyboardHeightPx) <= STABLE_KEYBOARD_HEIGHT_DELTA_PX
		val isReasonableShift = abs(heightPx - stableKeyboardHeightPx) <= STABLE_KEYBOARD_HEIGHT_LARGE_SHIFT_PX
		val isStableCandidate = isNearStable ||
			(isReasonableShift && consecutiveNearKeyboardSamples >= 1) ||
			consecutiveNearKeyboardSamples >= 2

		if (isStableCandidate) {
			stableKeyboardHeightPx = heightPx
		}
	}

	private fun resetKeyboardSampling() {
		lastKeyboardSampleHeightPx = 0
		consecutiveNearKeyboardSamples = 0
	}

	private fun clearKeyboardHandoff() {
		keyboardHandoffHeightPx = 0
	}

	private fun logState(event: String) {
		runCatching {
			Log.d(
				CHAT_INPUT_DEBUG_TAG,
				buildString {
					append(event)
					append(" | uiState=")
					append(uiState)
					append(" mode=")
					append(inputMode)
					append(" imeHeightPx=")
					append(imeHeightPx)
					append(" stableKeyboardHeightPx=")
					append(stableKeyboardHeightPx)
					append(" keyboardLayoutHeightPx=")
					append(keyboardLayoutHeightPx)
					append(" emojiPanelHeightPx=")
					append(emojiPanelHeightPx)
					append(" keyboardHandoffHeightPx=")
					append(keyboardHandoffHeightPx)
					append(" keepEmojiModeUntilImeHidden=")
					append(keepEmojiModeUntilImeHidden)
					append(" isTextFieldFocused=")
					append(isTextFieldFocused)
					append(" keyboardRequestToken=")
					append(keyboardRequestToken)
				}
			)
		}
	}
}

@Composable
fun rememberChatInputController(
	text: String,
	maxLength: Int?,
	fallbackPanelHeightPx: Int,
	onTextChanged: (String) -> Unit
): ChatInputController {
	val latestOnTextChanged by rememberUpdatedState(onTextChanged)
	val controller = remember(maxLength, fallbackPanelHeightPx) {
		ChatInputController(
			initialText = text,
			maxLength = maxLength,
			fallbackPanelHeightPx = fallbackPanelHeightPx
		)
	}

	controller.onTextChanged = latestOnTextChanged
	controller.syncExternalText(text)

	return controller
}

private fun findPreviousGraphemeBoundary(text: String, cursor: Int): Int {
	if (cursor <= 0) return 0

	var boundary = previousCodePointStart(text, cursor)
	if (boundary <= 0) return 0

	while (boundary > 0) {
		val codePoint = text.codePointAt(boundary)
		when {
			codePoint.isEmojiModifier() || codePoint.isVariationSelector() || codePoint.isKeycapCombiningMark() -> {
				boundary = previousCodePointStart(text, boundary)
				continue
			}
		}

		val previousStart = previousCodePointStartOrNull(text, boundary) ?: break
		val previousCodePoint = text.codePointAt(previousStart)

		if (previousCodePoint == ZeroWidthJoiner) {
			boundary = previousCodePointStart(text, previousStart)
			continue
		}

		if (codePoint.isRegionalIndicator() && previousCodePoint.isRegionalIndicator()) {
			boundary = previousStart
		}
		break
	}

	return boundary.coerceAtLeast(0)
}

private fun previousCodePointStart(text: String, index: Int): Int =
	Character.offsetByCodePoints(text, index, -1)

private fun previousCodePointStartOrNull(text: String, index: Int): Int? =
	if (index <= 0) null else previousCodePointStart(text, index)

private fun Int.isEmojiModifier(): Boolean = this in 0x1F3FB..0x1F3FF

private fun Int.isVariationSelector(): Boolean = this == 0xFE0F || this == 0xFE0E

private fun Int.isKeycapCombiningMark(): Boolean = this == 0x20E3

private fun Int.isRegionalIndicator(): Boolean = this in 0x1F1E6..0x1F1FF

private const val ZeroWidthJoiner = 0x200D
