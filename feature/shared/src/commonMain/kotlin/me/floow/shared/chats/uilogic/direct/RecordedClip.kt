package me.floow.shared.chats.uilogic.direct

/**
 * Platform-agnostic record of a finalized video circle clip.
 *
 * Produced by the platform [VideoRecorderBridge] and consumed by the shared
 * [VideoRecordingStateHolder] / `DirectChatStateHolder`. Keeps the shared
 * layer decoupled from Android-only `MediaMetadataRetriever` types.
 */
data class RecordedClip(
    val path: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
)
