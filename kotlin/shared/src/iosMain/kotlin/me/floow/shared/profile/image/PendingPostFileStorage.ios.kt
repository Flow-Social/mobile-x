package me.floow.shared.profile.image

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.usePinned
import platform.Foundation.NSData
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask
import platform.Foundation.create
import platform.Foundation.dataWithBytes
import platform.Foundation.writeToFile

@OptIn(ExperimentalForeignApi::class)
class IosPendingPostFileStorage : PendingPostFileStorage {
    private val fileManager = NSFileManager.defaultManager

    private fun getBaseDirectory(): String {
        val docDir = fileManager.URLForDirectory(
            directory = NSDocumentDirectory,
            inDomain = NSUserDomainMask,
            appropriateForURL = null,
            create = true,
            error = null,
        )
        val path = requireNotNull(docDir?.path) + "/pending_posts"
        fileManager.createDirectoryAtPath(path, withIntermediateDirectories = true, attributes = null, error = null)
        return path
    }

    override suspend fun saveFile(commandId: String, fileName: String, bytes: ByteArray): String {
        val commandDir = "${getBaseDirectory()}/$commandId"
        fileManager.createDirectoryAtPath(commandDir, withIntermediateDirectories = true, attributes = null, error = null)
        val filePath = "$commandDir/$fileName"
        if (bytes.isNotEmpty()) {
            bytes.usePinned { pinned ->
                val data = NSData.dataWithBytes(pinned.addressOf(0), bytes.size.toULong())
                data.writeToFile(filePath, atomically = true)
            }
        } else {
            val emptyData = NSData()
            emptyData.writeToFile(filePath, atomically = true)
        }
        return filePath
    }

    override suspend fun copyFile(commandId: String, fileName: String, sourcePath: String): String {
        val commandDir = "${getBaseDirectory()}/$commandId"
        fileManager.createDirectoryAtPath(commandDir, withIntermediateDirectories = true, attributes = null, error = null)
        val filePath = "$commandDir/$fileName"
        if (fileManager.fileExistsAtPath(filePath)) {
            fileManager.removeItemAtPath(filePath, error = null)
        }
        if (fileManager.fileExistsAtPath(sourcePath)) {
            fileManager.copyItemAtPath(sourcePath, toPath = filePath, error = null)
        } else {
            NSData().writeToFile(filePath, atomically = true)
        }
        return filePath
    }

    override suspend fun readFile(absolutePath: String): ByteArray? {
        if (!fileManager.fileExistsAtPath(absolutePath)) return null
        val data = NSData.create(contentsOfFile = absolutePath) ?: return null
        val length = data.length.toInt()
        if (length == 0) return ByteArray(0)
        val byteArray = ByteArray(length)
        byteArray.usePinned { pinned ->
            platform.posix.memcpy(pinned.addressOf(0), data.bytes, data.length)
        }
        return byteArray
    }

    override suspend fun fileExists(absolutePath: String): Boolean {
        return fileManager.fileExistsAtPath(absolutePath)
    }

    override suspend fun getFileSize(absolutePath: String): Long {
        if (!fileManager.fileExistsAtPath(absolutePath)) return 0L
        val attrs = fileManager.attributesOfItemAtPath(absolutePath, error = null) ?: return 0L
        val sizeNumber = attrs[platform.Foundation.NSFileSize] as? platform.Foundation.NSNumber
        return sizeNumber?.longLongValue ?: 0L
    }

    override suspend fun deleteCommandFiles(commandId: String) {
        val commandDir = "${getBaseDirectory()}/$commandId"
        fileManager.removeItemAtPath(commandDir, error = null)
    }
}

actual fun createPendingPostFileStorage(): PendingPostFileStorage = IosPendingPostFileStorage()
