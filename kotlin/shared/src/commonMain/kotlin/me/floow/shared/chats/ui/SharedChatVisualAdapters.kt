package me.floow.shared.chats.ui

import androidx.compose.ui.graphics.Color
import me.floow.shared.chats.model.ChatThreadHeaderModel
import me.floow.uikit.chat.model.ChatLayoutMode
import me.floow.uikit.chat.model.ChatScreenConfig

private val RepliesDividerColor = Color.Black.copy(alpha = 0.1f)

internal fun sharedRepliesChatConfig(): ChatScreenConfig {
    return ChatScreenConfig(
        layoutMode = ChatLayoutMode.OldestAtTop,
        showTypingIndicator = false,
        showPinActions = false,
        showInputBar = false,
        showMessageOptions = false,
        showReplyInteractions = false,
        showHighlightedMessageBackground = false,
        animateJumpToHighlightedMessage = false,
        showEmojiButton = false,
        scrollToBottomOnInputFocus = false,
        alwaysShowScrollToBottomWhenNotAtBottom = true,
        liftMessageListWithIme = false,
        showAuthorHeaderForInMessages = true,
        showTopBarSubtitle = false,
        showTopBarDropdown = false,
        dividerColor = RepliesDividerColor,
    )
}

internal fun sharedDirectChatConfig(header: ChatThreadHeaderModel?): ChatScreenConfig {
    return if (header?.isSavedMessages == true) {
        ChatScreenConfig(
            showTypingIndicator = false,
            showTopBarSubtitle = false,
        )
    } else {
        ChatScreenConfig()
    }
}
