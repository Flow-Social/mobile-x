package me.floow.uikit.chat.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class ChatScreenConfig(
	val layoutMode: ChatLayoutMode = ChatLayoutMode.NewestAtBottom,
	val showTypingIndicator: Boolean = true,
	val showPinActions: Boolean = true,
	val showEmojiButton: Boolean = true,
	val scrollToBottomOnInputFocus: Boolean = true,
	val liftMessageListWithIme: Boolean = false,
	val showAuthorHeaderForInMessages: Boolean = false,
	val maxInputLength: Int? = null,
	val topBarMode: ChatTopBarMode = ChatTopBarMode.Standard,
	val topBarTitle: String? = null,
	val showTopBarDropdown: Boolean = true,
	val dividerColor: Color? = null
)

enum class ChatLayoutMode {
	NewestAtBottom,
	OldestAtTop
}

enum class ChatTopBarMode {
	Standard,
	TitleOnly
}
