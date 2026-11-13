package me.floow.data.util

import java.io.File

actual fun platformFileExistsAndNotEmpty(filePath: String): Boolean {
    val file = File(filePath)
    return file.exists() && file.length() > 0L
}
