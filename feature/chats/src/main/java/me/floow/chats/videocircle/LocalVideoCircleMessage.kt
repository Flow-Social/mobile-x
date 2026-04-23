package me.floow.chats.videocircle

data class LocalVideoCircleMessage(
    val id: Long = System.currentTimeMillis(),
    val videoPath: String,
    val createdAtMs: Long = System.currentTimeMillis(),
    val isOutgoing: Boolean = true,
)
