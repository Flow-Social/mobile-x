package me.floow.uikit.components.pickers

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private const val EmojiRecentStoreName = "emoji_recent_store"
private const val EmojiRecentLimit = 27
private const val EmojiRecentDelimiter = "\u001F"

private val Context.emojiRecentDataStore by preferencesDataStore(
	name = EmojiRecentStoreName
)

@Stable
internal class FlowEmojiRecentStore(
	private val context: Context
) {
	private val recentEmojiKey = stringPreferencesKey("recent_emoji_items")

	fun recentEmojis(): Flow<List<String>> = context.emojiRecentDataStore.data
		.map { preferences ->
			preferences[recentEmojiKey]
				.orEmpty()
				.split(EmojiRecentDelimiter)
				.filter { it.isNotBlank() }
				.distinct()
				.take(EmojiRecentLimit)
		}

	suspend fun persistRecentEmoji(emoji: String) {
		if (emoji.isBlank()) return

		context.emojiRecentDataStore.edit { preferences: MutablePreferences ->
			val updated = buildList {
				add(emoji)
				addAll(
					preferences[recentEmojiKey]
						.orEmpty()
						.split(EmojiRecentDelimiter)
						.filter { it.isNotBlank() && it != emoji }
				)
			}
				.take(EmojiRecentLimit)
				.joinToString(EmojiRecentDelimiter)

			preferences[recentEmojiKey] = updated
		}
	}
}

@Composable
internal fun rememberFlowEmojiRecentStore(): FlowEmojiRecentStore {
	val context = LocalContext.current.applicationContext
	return remember(context) {
		FlowEmojiRecentStore(context)
	}
}
