package me.floow.app.posts

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.floow.domain.data.repos.PendingPostMediaStore
import me.floow.domain.data.repos.PendingPostStagedMedia
import me.floow.domain.data.repos.UploadImageData
import java.io.File
import java.util.UUID

class PendingPostMediaStoreImpl(
    private val context: Context,
) : PendingPostMediaStore {
    override suspend fun stageFiles(commandId: String, files: List<UploadImageData>): List<PendingPostStagedMedia> = withContext(Dispatchers.IO) {
        if (files.isEmpty()) return@withContext emptyList()
        val commandDir = File(File(context.filesDir, "pending_posts"), commandId)
        if (!commandDir.exists()) {
            commandDir.mkdirs()
        }
        files.mapIndexed { index, file ->
            val extension = file.fileName.substringAfterLast('.', "bin")
            val stagedId = UUID.randomUUID().toString()
            val target = File(commandDir, "${index}_$stagedId.$extension")
            val sourcePath = file.filePath
            if (!sourcePath.isNullOrBlank()) {
                val sourceFile = File(sourcePath)
                if (sourceFile.exists() && sourceFile.isFile) {
                    sourceFile.copyTo(target, overwrite = true)
                } else {
                    target.writeBytes(file.bytes)
                }
            } else {
                target.writeBytes(file.bytes)
            }
            PendingPostStagedMedia(
                id = stagedId,
                fileName = file.fileName,
                contentType = file.contentType,
                sizeBytes = file.sizeBytes,
                absolutePath = target.absolutePath,
            )
        }
    }

    override suspend fun readUploadData(media: PendingPostStagedMedia): UploadImageData? = withContext(Dispatchers.IO) {
        val file = File(media.absolutePath)
        if (!file.exists() || !file.isFile) {
            return@withContext null
        }
        UploadImageData(
            fileName = media.fileName,
            contentType = media.contentType,
            sizeBytes = file.length(),
            bytes = ByteArray(0),
            filePath = media.absolutePath,
        )
    }

    override suspend fun deleteStaged(commandId: String): Unit = withContext(Dispatchers.IO) {
        File(File(context.filesDir, "pending_posts"), commandId).deleteRecursively()
        Unit
    }
}
