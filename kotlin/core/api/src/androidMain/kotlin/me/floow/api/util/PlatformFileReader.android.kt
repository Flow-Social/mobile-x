package me.floow.api.util

import android.net.Uri
import java.io.File

actual class PlatformFileReader actual constructor(private val filePath: String) {
    private val uri: Uri? = runCatching {
        if (filePath.startsWith("content://") || filePath.startsWith("file://")) {
            Uri.parse(filePath)
        } else {
            Uri.fromFile(File(filePath))
        }
    }.getOrNull()

    private val isContentUri: Boolean = uri?.scheme == "content"
    private val localFile: File? = if (!isContentUri) {
        val cleanPath = if (filePath.startsWith("file://")) filePath.removePrefix("file://") else filePath
        File(cleanPath)
    } else null

    actual val exists: Boolean
        get() {
            if (isContentUri) {
                val targetUri = uri ?: return false
                val resolver = AndroidApiContext.get()?.contentResolver ?: return false
                return runCatching {
                    resolver.openAssetFileDescriptor(targetUri, "r")?.use { true } ?: false
                }.getOrDefault(false)
            }
            return localFile?.let { it.exists() && it.canRead() } ?: false
        }

    actual val length: Long
        get() {
            if (isContentUri) {
                val targetUri = uri ?: return 0L
                val resolver = AndroidApiContext.get()?.contentResolver ?: return 0L
                return runCatching {
                    resolver.openAssetFileDescriptor(targetUri, "r")?.use { it.length } ?: 0L
                }.getOrDefault(0L)
            }
            return localFile?.length() ?: 0L
        }

    actual fun openStream(): PlatformFileStream? {
        if (!exists) return null
        val input = if (isContentUri) {
            val targetUri = uri ?: return null
            val resolver = AndroidApiContext.get()?.contentResolver ?: return null
            runCatching { resolver.openInputStream(targetUri) }.getOrNull() ?: return null
        } else {
            localFile?.inputStream() ?: return null
        }

        return object : PlatformFileStream {
            override fun read(buffer: ByteArray): Int = input.read(buffer)
            override fun close() = input.close()
        }
    }
}

