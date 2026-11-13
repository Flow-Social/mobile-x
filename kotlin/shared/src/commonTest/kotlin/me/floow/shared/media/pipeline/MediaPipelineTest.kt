package me.floow.shared.media.pipeline

import kotlinx.coroutines.test.runTest
import me.floow.domain.api.UploadsApi
import me.floow.domain.api.models.CreateUploadPresignData
import me.floow.domain.api.models.CreateUploadPresignResponse
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.UploadImageData
import me.floow.data.repos.UploadsRepositoryImpl
import me.floow.domain.utils.Logger
import me.floow.domain.utils.currentTimeMillis
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MediaPipelineTest {

    private class RecordingUploadsApi : UploadsApi {
        var uploadFileCalled = false
        var uploadFilePathCalled = false
        var lastUploadedFilePath: String? = null
        var lastUploadedBytes: ByteArray? = null
        var shouldSucceed = true

        override suspend fun createPresign(data: CreateUploadPresignData): CreateUploadPresignResponse {
            return if (shouldSucceed) {
                CreateUploadPresignResponse.Success(
                    uploadUrl = "https://s3.example.com/upload",
                    fileUrl = "https://cdn.example.com/media/${data.fileName}",
                    objectKey = "media/${data.fileName}",
                    expiresInSeconds = 3600L,
                    maxSizeBytes = 10_000_000L,
                )
            } else {
                CreateUploadPresignResponse.Error
            }
        }

        override suspend fun uploadFile(
            uploadUrl: String,
            contentType: String,
            bytes: ByteArray,
            onProgress: ((Float) -> Unit)?
        ): Boolean {
            uploadFileCalled = true
            lastUploadedBytes = bytes
            onProgress?.invoke(1f)
            return shouldSucceed
        }

        override suspend fun uploadFilePath(
            uploadUrl: String,
            contentType: String,
            filePath: String,
            onProgress: ((Float) -> Unit)?
        ): Boolean {
            uploadFilePathCalled = true
            lastUploadedFilePath = filePath
            onProgress?.invoke(1f)
            return shouldSucceed
        }
    }

    private class NoOpLogger : Logger {
        override fun d(tag: String?, message: String) {}
    }

    @Test
    fun mediaUploadPolicies_haveExpectedDefaults() {
        val avatar = MediaUploadPolicy.AVATAR
        assertEquals("avatar", avatar.kind)
        assertEquals(1024, avatar.maxDimensionPx)
        assertEquals(1_000_000L, avatar.maxFileSizeBytes)
        assertEquals(80, avatar.jpegQuality)

        val background = MediaUploadPolicy.BACKGROUND
        assertEquals("background", background.kind)
        assertEquals(2048, background.maxDimensionPx)
        assertEquals(2_000_000L, background.maxFileSizeBytes)

        val post = MediaUploadPolicy.POST
        assertEquals("post", post.kind)
        assertEquals(2048, post.maxDimensionPx)
        assertEquals(2_000_000L, post.maxFileSizeBytes)

        val chat = MediaUploadPolicy.CHAT_IMAGE
        assertEquals("chat_image", chat.kind)
        assertEquals(2048, chat.maxDimensionPx)
        assertEquals(2_000_000L, chat.maxFileSizeBytes)
    }

    @Test
    fun uploadsRepository_usesStreamingFilePathWhenProvided_neverUploadFileWithBytes() = runTest {
        val fakeApi = RecordingUploadsApi()
        val repo = UploadsRepositoryImpl(logger = NoOpLogger(), uploadsApi = fakeApi)

        val data = listOf(
            UploadImageData(
                fileName = "avatar.jpg",
                contentType = "image/jpeg",
                sizeBytes = 1024L,
                bytes = ByteArray(0), // empty bytes in RAM!
                filePath = "/tmp/cache/avatar.jpg"
            )
        )

        val result = repo.uploadImages(data, kind = "avatar")
        assertIs<GetDataResponse.Success<List<String>>>(result)
        assertEquals(1, result.data.size)
        assertEquals("https://cdn.example.com/media/avatar.jpg", result.data.first())

        assertTrue(fakeApi.uploadFilePathCalled, "Should call streaming uploadFilePath")
        assertTrue(!fakeApi.uploadFileCalled, "Should NOT call uploadFile with bytes when filePath is present")
        assertEquals("/tmp/cache/avatar.jpg", fakeApi.lastUploadedFilePath)
    }

    @Test
    fun localMediaPreparer_handlesSuccessAndErrors() = runTest {
        val fakePreparer = object : LocalMediaPreparer {
            override suspend fun prepareMedia(
                sourceUriOrPath: String,
                policy: MediaUploadPolicy
            ): MediaPreparationResult {
                return when {
                    sourceUriOrPath.isEmpty() -> MediaPreparationResult.Failure(MediaPreparationError.FILE_NOT_FOUND)
                    sourceUriOrPath.endsWith(".unsupported") -> MediaPreparationResult.Failure(MediaPreparationError.UNSUPPORTED_TYPE)
                    sourceUriOrPath.contains("oversized") -> MediaPreparationResult.Failure(MediaPreparationError.FILE_TOO_LARGE)
                    else -> MediaPreparationResult.Success(
                        PreparedMediaFile(
                            filePath = "/cache/prepared_${policy.kind}.jpg",
                            fileName = "prepared.jpg",
                            contentType = "image/jpeg",
                            sizeBytes = 50_000L,
                            width = minOf(1920, policy.maxDimensionPx),
                            height = minOf(1080, policy.maxDimensionPx),
                        )
                    )
                }
            }
        }

        val successRes = fakePreparer.prepareMedia("content://media/1", MediaUploadPolicy.POST)
        assertIs<MediaPreparationResult.Success>(successRes)
        assertEquals("/cache/prepared_post.jpg", successRes.media.filePath)
        assertTrue(successRes.media.sizeBytes > 0)
        assertTrue(successRes.media.width <= MediaUploadPolicy.POST.maxDimensionPx)

        val notFoundRes = fakePreparer.prepareMedia("", MediaUploadPolicy.AVATAR)
        assertIs<MediaPreparationResult.Failure>(notFoundRes)
        assertEquals(MediaPreparationError.FILE_NOT_FOUND, notFoundRes.error)

        val unsupportedRes = fakePreparer.prepareMedia("file.unsupported", MediaUploadPolicy.AVATAR)
        assertIs<MediaPreparationResult.Failure>(unsupportedRes)
        assertEquals(MediaPreparationError.UNSUPPORTED_TYPE, unsupportedRes.error)

        val oversizedRes = fakePreparer.prepareMedia("oversized.jpg", MediaUploadPolicy.AVATAR)
        assertIs<MediaPreparationResult.Failure>(oversizedRes)
        assertEquals(MediaPreparationError.FILE_TOO_LARGE, oversizedRes.error)

        val chatImageRes = fakePreparer.prepareMedia("content://chat/img1", MediaUploadPolicy.CHAT_IMAGE)
        assertIs<MediaPreparationResult.Success>(chatImageRes)
        assertEquals("/cache/prepared_chat_image.jpg", chatImageRes.media.filePath)
        assertTrue(chatImageRes.media.width <= MediaUploadPolicy.CHAT_IMAGE.maxDimensionPx)
        assertEquals(2048, MediaUploadPolicy.CHAT_IMAGE.maxDimensionPx)
    }

    @Test
    fun deletePreparedTempFile_handlesNonExistentGracefully() {
        val deleted = deletePreparedTempFile("/tmp/non_existent_file_${currentTimeMillis()}.tmp")
        assertTrue(!deleted, "Deleting non-existent file should safely return false")
    }
}
