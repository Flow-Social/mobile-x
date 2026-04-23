package me.floow.shared.chats.model

/**
 * Message content contract.
 *
 * Replaces the ad-hoc `localVideoCirclePath` nullable on [ChatMessageItemModel] and
 * prepares the chat timeline pipeline for first-class media messages
 * (reply / edit / delete / pin / selection / status) without branching per-feature.
 *
 * Text is the default, implicit content. Media variants carry their own metadata
 * and upload state so the shared-element transition and future backend pipeline
 * can reason about progress without inspecting UI fields.
 */
sealed interface ChatMessageContent {

    /** Regular textual content. */
    data object Text : ChatMessageContent

    /**
     * Post preview card content for chat timeline.
     */
    data class PostPreview(
        val imageUrl: String? = null,
        val likesCount: Int = 0,
        val title: String? = null,
    ) : ChatMessageContent

    /**
     * Round video message (a.k.a. video circle / telescope).
     *
     * @param localPath      Absolute path to the local recording (nullable after remote-only rehydration).
     * @param remoteUrl      Canonical remote URL once upload finishes.
     * @param durationMs     Clip duration in milliseconds.
     * @param thumbnailPath  Optional local thumbnail/first-frame snapshot for flight transition.
     * @param width          Intrinsic video width (rotation-applied).
     * @param height         Intrinsic video height (rotation-applied).
     * @param uploadState    Upload/network lifecycle, independent from message delivery status.
     */
    data class VideoCircle(
        val localPath: String?,
        val remoteUrl: String? = null,
        val durationMs: Long = 0L,
        val thumbnailPath: String? = null,
        val width: Int = 0,
        val height: Int = 0,
        val uploadState: VideoUploadState = VideoUploadState.Pending,
    ) : ChatMessageContent
}

enum class VideoUploadState {
    Pending,
    Uploading,
    Uploaded,
    Failed,
}
