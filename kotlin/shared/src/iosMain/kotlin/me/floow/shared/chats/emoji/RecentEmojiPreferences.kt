package me.floow.shared.chats.emoji

import platform.Foundation.NSUserDefaults

internal actual object RecentEmojiPreferences {
    actual fun read(): String = NSUserDefaults.standardUserDefaults.stringForKey("chat_emoji_recent").orEmpty()
    actual fun write(value: String) {
        NSUserDefaults.standardUserDefaults.setObject(value, forKey = "chat_emoji_recent")
    }
}
