package me.floow.shared.chats.emoji

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val EmojiRecentLimit = 32

@Stable
class FlowEmojiRecentStore {
    private val _recent = MutableStateFlow(RecentEmojiPreferences.read().lineSequence().filter { it.isNotBlank() }.distinct().take(EmojiRecentLimit).toList())

    fun recentEmojis(): Flow<List<String>> = _recent.asStateFlow()

    fun persistRecentEmoji(emoji: String) {
        if (emoji.isBlank()) return
        val next = (listOf(emoji) + _recent.value.filter { it != emoji }).take(EmojiRecentLimit)
        if (next != _recent.value) {
            _recent.value = next
            RecentEmojiPreferences.write(next.joinToString("\n"))
        }
    }

    companion object {
        private val globalInstance by lazy { FlowEmojiRecentStore() }

        fun default(): FlowEmojiRecentStore = globalInstance
    }
}

@Composable
fun rememberFlowEmojiRecentStore(): FlowEmojiRecentStore {
    return remember { FlowEmojiRecentStore.default() }
}

internal expect object RecentEmojiPreferences {
    fun read(): String
    fun write(value: String)
}
