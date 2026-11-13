package me.floow.api.util

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.posix.F_OK
import platform.posix.SEEK_END
import platform.posix.access
import platform.posix.fclose
import platform.posix.fopen
import platform.posix.fread
import platform.posix.fseek
import platform.posix.ftell

actual class PlatformFileReader actual constructor(filePath: String) {
    private val cleanPath = when {
        filePath.startsWith("file://") -> filePath.removePrefix("file://")
        filePath.startsWith("file:") -> filePath.removePrefix("file:")
        else -> filePath
    }

    actual val exists: Boolean
        get() = access(cleanPath, F_OK) == 0

    @OptIn(ExperimentalForeignApi::class)
    actual val length: Long
        get() {
            val file = fopen(cleanPath, "rb") ?: return 0L
            fseek(file, 0L, SEEK_END)
            val size = ftell(file)
            fclose(file)
            return size
        }

    @OptIn(ExperimentalForeignApi::class)
    actual fun openStream(): PlatformFileStream? {
        val file = fopen(cleanPath, "rb") ?: return null
        return object : PlatformFileStream {
            override fun read(buffer: ByteArray): Int {
                if (buffer.isEmpty()) return 0
                return buffer.usePinned { pinned ->
                    val readBytes = fread(pinned.addressOf(0), 1u, buffer.size.toULong(), file)
                    readBytes.toInt()
                }
            }

            override fun close() {
                fclose(file)
            }
        }
    }
}
