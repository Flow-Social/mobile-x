package me.floow.shared.profile.image

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.floow.database.AndroidDatabaseContext
import me.floow.shared.auth.AndroidTokenContext
import org.koin.mp.KoinPlatformTools
import java.io.File

class AndroidPendingPostFileStorage(
    private val baseDir: File,
) : PendingPostFileStorage {

    override suspend fun saveFile(commandId: String, fileName: String, bytes: ByteArray): String = withContext(Dispatchers.IO) {
        val commandDir = File(baseDir, commandId)
        if (!commandDir.exists()) {
            commandDir.mkdirs()
        }
        val target = File(commandDir, fileName)
        target.writeBytes(bytes)
        target.absolutePath
    }

    override suspend fun copyFile(commandId: String, fileName: String, sourcePath: String): String = withContext(Dispatchers.IO) {
        val commandDir = File(baseDir, commandId)
        if (!commandDir.exists()) {
            commandDir.mkdirs()
        }
        val target = File(commandDir, fileName)
        val cleanPath = if (sourcePath.startsWith("file://")) sourcePath.removePrefix("file://") else sourcePath
        val sourceFile = File(cleanPath)
        if (sourceFile.exists() && sourceFile.isFile) {
            sourceFile.copyTo(target, overwrite = true)
        } else if (sourcePath.startsWith("content://")) {
            val context = AndroidDatabaseContext.get()
            val uri = android.net.Uri.parse(sourcePath)
            val copied = runCatching {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                true
            }.getOrDefault(false)
            if (!copied || !target.exists()) {
                target.createNewFile()
            }
        } else {
            target.createNewFile()
        }
        target.absolutePath
    }

    override suspend fun readFile(absolutePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val file = File(absolutePath)
        if (!file.exists() || !file.isFile) return@withContext null
        file.readBytes()
    }

    override suspend fun fileExists(absolutePath: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(absolutePath)
        file.exists() && file.isFile
    }

    override suspend fun getFileSize(absolutePath: String): Long = withContext(Dispatchers.IO) {
        val file = File(absolutePath)
        if (file.exists() && file.isFile) file.length() else 0L
    }

    override suspend fun deleteCommandFiles(commandId: String): Unit = withContext(Dispatchers.IO) {
        File(baseDir, commandId).deleteRecursively()
        Unit
    }
}

actual fun createPendingPostFileStorage(): PendingPostFileStorage {
    val context = runCatching { AndroidDatabaseContext.get() }.getOrNull()
        ?: AndroidTokenContext.get()
        ?: KoinPlatformTools.defaultContext().getOrNull()?.getOrNull<Context>()

    return if (context != null) {
        val baseDir = File(context.filesDir, "pending_posts")
        if (!baseDir.exists()) baseDir.mkdirs()
        AndroidPendingPostFileStorage(baseDir)
    } else {
        InMemoryPendingPostFileStorage()
    }
}
