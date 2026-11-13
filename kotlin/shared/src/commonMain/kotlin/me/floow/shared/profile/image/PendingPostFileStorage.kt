package me.floow.shared.profile.image

interface PendingPostFileStorage {
    suspend fun saveFile(commandId: String, fileName: String, bytes: ByteArray): String
    suspend fun copyFile(commandId: String, fileName: String, sourcePath: String): String {
        val bytes = readFile(sourcePath) ?: ByteArray(0)
        return saveFile(commandId, fileName, bytes)
    }
    suspend fun readFile(absolutePath: String): ByteArray?
    suspend fun fileExists(absolutePath: String): Boolean = readFile(absolutePath) != null
    suspend fun getFileSize(absolutePath: String): Long = readFile(absolutePath)?.size?.toLong() ?: 0L
    suspend fun deleteCommandFiles(commandId: String)
}

class InMemoryPendingPostFileStorage : PendingPostFileStorage {
    private val files = mutableMapOf<String, ByteArray>()
    private val commandDirs = mutableMapOf<String, MutableList<String>>()

    override suspend fun saveFile(commandId: String, fileName: String, bytes: ByteArray): String {
        val path = "memory://$commandId/$fileName"
        files[path] = bytes
        commandDirs.getOrPut(commandId) { mutableListOf() }.add(path)
        return path
    }

    override suspend fun readFile(absolutePath: String): ByteArray? = files[absolutePath]

    override suspend fun fileExists(absolutePath: String): Boolean = files.containsKey(absolutePath)

    override suspend fun getFileSize(absolutePath: String): Long = files[absolutePath]?.size?.toLong() ?: 0L

    override suspend fun deleteCommandFiles(commandId: String) {
        commandDirs.remove(commandId)?.forEach { files.remove(it) }
    }
}

expect fun createPendingPostFileStorage(): PendingPostFileStorage
