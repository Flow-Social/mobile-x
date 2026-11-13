package me.floow.shared.profile.image

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AndroidLocalImageFileReader(
    private val context: Context
) : LocalImageFileReader {
    private val mediaPreparer = me.floow.shared.media.pipeline.AndroidLocalMediaPreparer(context)

    override suspend fun read(uriString: String): LocalImageFile? = withContext(Dispatchers.IO) {
        when (val result = readPost(uriString)) {
            is LocalImageReadResult.Success -> result.file
            is LocalImageReadResult.Failure -> null
        }
    }

    override suspend fun readPost(uriString: String): LocalImageReadResult = withContext(Dispatchers.IO) {
        prepareWithPolicy(uriString, me.floow.shared.media.pipeline.MediaUploadPolicy.POST)
    }

    override suspend fun readAvatar(uriString: String): LocalImageReadResult = withContext(Dispatchers.IO) {
        prepareWithPolicy(uriString, me.floow.shared.media.pipeline.MediaUploadPolicy.AVATAR)
    }

    override suspend fun readBackground(uriString: String): LocalImageReadResult = withContext(Dispatchers.IO) {
        prepareWithPolicy(uriString, me.floow.shared.media.pipeline.MediaUploadPolicy.BACKGROUND)
    }

    private suspend fun prepareWithPolicy(
        uriString: String,
        policy: me.floow.shared.media.pipeline.MediaUploadPolicy,
    ): LocalImageReadResult {
        return when (val prep = mediaPreparer.prepareMedia(uriString, policy)) {
            is me.floow.shared.media.pipeline.MediaPreparationResult.Success -> {
                val media = prep.media
                LocalImageReadResult.Success(
                    LocalImageFile(
                        fileName = media.fileName,
                        contentType = media.contentType,
                        sizeBytes = media.sizeBytes,
                        bytes = ByteArray(0),
                        filePath = media.filePath,
                    )
                )
            }
            is me.floow.shared.media.pipeline.MediaPreparationResult.Failure -> {
                val err = when (prep.error) {
                    me.floow.shared.media.pipeline.MediaPreparationError.FILE_NOT_FOUND -> LocalImageReadError.CORRUPTED_IMAGE
                    me.floow.shared.media.pipeline.MediaPreparationError.UNSUPPORTED_TYPE -> LocalImageReadError.UNSUPPORTED_TYPE
                    me.floow.shared.media.pipeline.MediaPreparationError.FILE_TOO_LARGE -> LocalImageReadError.FILE_TOO_LARGE
                    me.floow.shared.media.pipeline.MediaPreparationError.CORRUPTED_IMAGE -> LocalImageReadError.CORRUPTED_IMAGE
                }
                LocalImageReadResult.Failure(err)
            }
        }
    }
}

actual fun createLocalImageFileReader(): LocalImageFileReader {
    val context = runCatching { me.floow.database.AndroidDatabaseContext.get() }.getOrNull()
        ?: me.floow.shared.auth.AndroidTokenContext.get()
        ?: org.koin.mp.KoinPlatformTools.defaultContext().getOrNull()?.getOrNull<Context>()
        ?: throw IllegalStateException("Context not initialized in AndroidTokenContext or Koin")
    return AndroidLocalImageFileReader(context)
}
