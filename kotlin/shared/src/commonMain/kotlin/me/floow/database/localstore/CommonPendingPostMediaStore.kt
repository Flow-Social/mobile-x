package me.floow.database.localstore

import me.floow.domain.data.repos.PendingPostMediaStore
import me.floow.domain.data.repos.PendingPostStagedMedia
import me.floow.domain.data.repos.PendingPostsSyncScheduler
import me.floow.domain.data.repos.UploadImageData
import me.floow.domain.utils.currentTimeMillis

import me.floow.shared.profile.image.PendingPostFileStorage
import me.floow.shared.profile.image.createPendingPostFileStorage

class CommonPendingPostMediaStore(
    private val fileStorage: PendingPostFileStorage = createPendingPostFileStorage(),
) : PendingPostMediaStore {

    override suspend fun stageFiles(commandId: String, files: List<UploadImageData>): List<PendingPostStagedMedia> {
        return files.mapIndexed { index, file ->
            val extension = file.fileName.substringAfterLast('.', "bin")
            val stagedId = "${commandId}_${index}_${currentTimeMillis()}"
            val fileName = "${index}_$stagedId.$extension"
            val localPath = file.filePath
            val absolutePath = if (!localPath.isNullOrBlank()) {
                fileStorage.copyFile(commandId, fileName, localPath)
            } else {
                fileStorage.saveFile(commandId, fileName, file.bytes)
            }
            PendingPostStagedMedia(
                id = stagedId,
                fileName = file.fileName,
                contentType = file.contentType,
                sizeBytes = file.sizeBytes,
                absolutePath = absolutePath,
            )
        }
    }

    override suspend fun readUploadData(media: PendingPostStagedMedia): UploadImageData? {
        if (!fileStorage.fileExists(media.absolutePath)) return null
        val resolvedSize = if (media.sizeBytes > 0L) media.sizeBytes else fileStorage.getFileSize(media.absolutePath)
        return UploadImageData(
            fileName = media.fileName,
            contentType = media.contentType,
            sizeBytes = resolvedSize,
            bytes = ByteArray(0),
            filePath = media.absolutePath,
        )
    }

    override suspend fun deleteStaged(commandId: String) {
        fileStorage.deleteCommandFiles(commandId)
    }
}

class CommonPendingPostsSyncScheduler(
    private val engineProvider: () -> me.floow.shared.media.outbox.MediaOutboxEngine? = { null },
) : PendingPostsSyncScheduler {
    override fun enqueueNow() {
        engineProvider()?.syncPosts()
    }

    override fun ensurePeriodic() {
        engineProvider()?.syncPosts()
    }
}
