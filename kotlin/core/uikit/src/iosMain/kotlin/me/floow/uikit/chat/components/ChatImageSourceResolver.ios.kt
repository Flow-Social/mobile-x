package me.floow.uikit.chat.components

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.F_OK
import platform.posix.SEEK_END
import platform.posix.access
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fseek
import platform.posix.ftell

@OptIn(ExperimentalForeignApi::class)
actual fun platformChatFileExists(filePath: String): Boolean {
	if (filePath.isBlank()) return false
	val cleanPath = if (filePath.startsWith("file://")) filePath.removePrefix("file://") else filePath
	if (access(cleanPath, F_OK) != 0) return false
	val file = fopen(cleanPath, "rb") ?: return false
	fseek(file, 0L, SEEK_END)
	val size = ftell(file)
	fclose(file)
	return size > 0L
}
