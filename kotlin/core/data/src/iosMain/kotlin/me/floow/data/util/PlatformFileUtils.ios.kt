package me.floow.data.util

import kotlinx.cinterop.ExperimentalForeignApi
import platform.posix.F_OK
import platform.posix.SEEK_END
import platform.posix.access
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fseek
import platform.posix.ftell

@OptIn(ExperimentalForeignApi::class)
actual fun platformFileExistsAndNotEmpty(filePath: String): Boolean {
    if (access(filePath, F_OK) != 0) return false
    val file = fopen(filePath, "rb") ?: return false
    fseek(file, 0L, SEEK_END)
    val size = ftell(file)
    fclose(file)
    return size > 0L
}
