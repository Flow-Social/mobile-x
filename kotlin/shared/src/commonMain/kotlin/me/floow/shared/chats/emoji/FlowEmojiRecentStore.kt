package me.floow.shared.chats.emoji

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

private const val EmojiRecentLimit = 32

@Stable
class FlowEmojiRecentStore {
    private val _recent = MutableStateFlow<List<String>>(emptyList())

    fun recentEmojis(): Flow<List<String>> = _recent.asStateFlow()

    fun persistRecentEmoji(emoji: String) {
        if (emoji.isBlank()) return
        _recent.update { current ->
            (listOf(emoji) + current.filter { it != emoji }).take(EmojiRecentLimit)
        }
    }

    companion object {
        private val globalInstance = FlowEmojiRecentStore()

        fun default(): FlowEmojiRecentStore = globalInstance
    }
}

@Composable
fun rememberFlowEmojiRecentStore(): FlowEmojiRecentStore {
    return remember { FlowEmojiRecentStore.default() }
}
