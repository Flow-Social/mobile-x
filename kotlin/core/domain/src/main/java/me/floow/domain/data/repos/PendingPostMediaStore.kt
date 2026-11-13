package me.floow.domain.data.repos

import kotlinx.serialization.Serializable

@Serializable
data class PendingPostStagedMedia(
    val id: String,
    val fileName: String,
    val contentType: String,
    val sizeBytes: Long,
    val absolutePath: String,
)

@Serializable
enum class PendingPostImageEntryKind {
    REMOTE,
    STAGED,
}

@Serializable
data class PendingPostImageEntry(
    val kind: PendingPostImageEntryKind,
    val remoteUrl: String? = null,
    val stagedMediaId: String? = null,
)

@Serializable
data class PendingPostImagePayload(
    val entries: List<PendingPostImageEntry> = emptyList(),
)

interface PendingPostMediaStore {
    suspend fun stageFiles(commandId: String, files: List<UploadImageData>): List<PendingPostStagedMedia>
    suspend fun readUploadData(media: PendingPostStagedMedia): UploadImageData?
    suspend fun deleteStaged(commandId: String)
    fun previewUri(media: PendingPostStagedMedia): String = "file://${media.absolutePath}"
}
