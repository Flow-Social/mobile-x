package me.floow.uikit.chat.model

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

@Immutable
data class ChatInteractionPolicy(
	val showTypingIndicator: Boolean = true,
	val showPinActions: Boolean = true,
	val showInputBar: Boolean = true,
	val showMessageOptions: Boolean = true,
	val showReplyInteractions: Boolean = true,
	val showEmojiButton: Boolean = true,
	val showAuthorHeaderForInMessages: Boolean = false,
	val maxInputLength: Int? = null
)

@Immutable
data class ChatScrollPolicy(
	val animateJumpToHighlightedMessage: Boolean = true,
	val scrollToBottomOnInputFocus: Boolean = true,
	val alwaysShowScrollToBottomWhenNotAtBottom: Boolean = false,
	val liftMessageListWithIme: Boolean = false,
	val jumpAlignment: ChatJumpAlignment = ChatJumpAlignment.Center,
	val jumpAnimationDurationMs: Int = 180,
	val allowAutoCenterCorrection: Boolean = false
)

@Immutable
data class ChatTopBarPolicy(
	val mode: ChatTopBarMode = ChatTopBarMode.Standard,
	val title: String? = null,
	val avatarResId: Int? = null,
	val showSubtitle: Boolean = true,
	val showDropdown: Boolean = true,
	val dividerColor: Color? = null
)

@Immutable
data class ChatScreenConfig(
	val layoutMode: ChatLayoutMode = ChatLayoutMode.NewestAtBottom,
	val showTypingIndicator: Boolean = true,
	val showPinActions: Boolean = true,
	val showInputBar: Boolean = true,
	val showMessageOptions: Boolean = true,
	val showReplyInteractions: Boolean = true,
	val showHighlightedMessageBackground: Boolean = true,
	val animateJumpToHighlightedMessage: Boolean = true,
	val showEmojiButton: Boolean = true,
	val scrollToBottomOnInputFocus: Boolean = true,
	val alwaysShowScrollToBottomWhenNotAtBottom: Boolean = false,
	val liftMessageListWithIme: Boolean = false,
	val jumpAlignment: ChatJumpAlignment = ChatJumpAlignment.Center,
	val jumpAnimationDurationMs: Int = 180,
	val allowAutoCenterCorrection: Boolean = false,
	val showAuthorHeaderForInMessages: Boolean = false,
	val maxInputLength: Int? = null,
	val topBarMode: ChatTopBarMode = ChatTopBarMode.Standard,
	val topBarTitle: String? = null,
	val topBarAvatarResId: Int? = null,
	val showTopBarSubtitle: Boolean = true,
	val showTopBarDropdown: Boolean = true,
	val dividerColor: Color? = null
) {
	val interactionPolicy: ChatInteractionPolicy
		get() = ChatInteractionPolicy(
			showTypingIndicator = showTypingIndicator,
			showPinActions = showPinActions,
			showInputBar = showInputBar,
			showMessageOptions = showMessageOptions,
			showReplyInteractions = showReplyInteractions,
			showEmojiButton = showEmojiButton,
			showAuthorHeaderForInMessages = showAuthorHeaderForInMessages,
			maxInputLength = maxInputLength
		)

	val scrollPolicy: ChatScrollPolicy
		get() = ChatScrollPolicy(
			animateJumpToHighlightedMessage = animateJumpToHighlightedMessage,
			scrollToBottomOnInputFocus = scrollToBottomOnInputFocus,
			alwaysShowScrollToBottomWhenNotAtBottom = alwaysShowScrollToBottomWhenNotAtBottom,
			liftMessageListWithIme = liftMessageListWithIme,
			jumpAlignment = jumpAlignment,
			jumpAnimationDurationMs = jumpAnimationDurationMs,
			allowAutoCenterCorrection = allowAutoCenterCorrection
		)

	val topBarPolicy: ChatTopBarPolicy
		get() = ChatTopBarPolicy(
			mode = topBarMode,
			title = topBarTitle,
			avatarResId = topBarAvatarResId,
			showSubtitle = showTopBarSubtitle,
			showDropdown = showTopBarDropdown,
			dividerColor = dividerColor
		)
}

enum class ChatLayoutMode {
	NewestAtBottom,
	OldestAtTop
}

enum class ChatJumpAlignment {
	Center,
	Top,
	Bottom
}

enum class ChatTopBarMode {
	Standard,
	TitleOnly
}
