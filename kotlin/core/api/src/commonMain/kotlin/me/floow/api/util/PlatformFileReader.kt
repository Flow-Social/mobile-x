package me.floow.api.util

interface PlatformFileStream {
    fun read(buffer: ByteArray): Int
    fun close()
}

expect class PlatformFileReader(filePath: String) {
    val exists: Boolean
    val length: Long
    fun openStream(): PlatformFileStream?
}
