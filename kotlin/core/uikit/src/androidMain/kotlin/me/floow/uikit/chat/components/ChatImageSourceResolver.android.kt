package me.floow.uikit.chat.components

import java.io.File

actual fun platformChatFileExists(filePath: String): Boolean {
	if (filePath.isBlank()) return false
	if (filePath.startsWith("content://")) {
		return true
	}
	val cleanPath = if (filePath.startsWith("file://")) {
		filePath.removePrefix("file://")
	} else {
		filePath
	}
	return runCatching {
		val file = File(cleanPath)
		file.exists() && file.length() > 0L
	}.getOrDefault(false)
}
