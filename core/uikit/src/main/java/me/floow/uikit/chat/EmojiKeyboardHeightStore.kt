package me.floow.uikit.chat

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.emojiKeyboardHeightDataStore by preferencesDataStore(
	name = "emoji_keyboard_height"
)

@Stable
internal class EmojiKeyboardHeightStore(
	private val context: Context
) {
	private val portraitImeHeightKey = intPreferencesKey("cached_ime_height_portrait_px")
	private val landscapeImeHeightKey = intPreferencesKey("cached_ime_height_landscape_px")

	fun cachedImeHeightPx(isLandscape: Boolean): Flow<Int> = context.emojiKeyboardHeightDataStore.data
		.map { preferences -> preferences[cachedImeHeightKey(isLandscape)] ?: 0 }

	suspend fun persistCachedImeHeightPx(heightPx: Int, isLandscape: Boolean) {
		if (heightPx <= 0) return
		context.emojiKeyboardHeightDataStore.edit { preferences: MutablePreferences ->
			preferences[cachedImeHeightKey(isLandscape)] = heightPx
		}
	}

	private fun cachedImeHeightKey(isLandscape: Boolean) =
		if (isLandscape) landscapeImeHeightKey else portraitImeHeightKey
}

@Composable
internal fun rememberEmojiKeyboardHeightStore(): EmojiKeyboardHeightStore {
	val context = LocalContext.current.applicationContext
	return remember(context) {
		EmojiKeyboardHeightStore(context)
	}
}

@Composable
internal fun rememberPersistedKeyboardHeightPx(
	keyboardHeightStore: EmojiKeyboardHeightStore
): Int {
	val configuration = LocalConfiguration.current
	val isLandscape = configuration.screenWidthDp > configuration.screenHeightDp
	val persistedHeight by remember(keyboardHeightStore, isLandscape) {
		keyboardHeightStore.cachedImeHeightPx(isLandscape)
	}.collectAsState(initial = 0)
	return persistedHeight
}

@Composable
internal fun rememberIsLandscapeKeyboardContext(): Boolean {
	val configuration = LocalConfiguration.current
	return configuration.screenWidthDp > configuration.screenHeightDp
}
