package me.floow.shared.chats.emoji

import android.content.Context
import me.floow.database.AndroidDatabaseContext

internal actual object RecentEmojiPreferences {
    private val preferences by lazy { AndroidDatabaseContext.get().getSharedPreferences("chat_emoji", Context.MODE_PRIVATE) }

    actual fun read(): String = preferences.getString("recent", null).orEmpty()
    actual fun write(value: String) {
        preferences.edit().putString("recent", value).apply()
    }
}
