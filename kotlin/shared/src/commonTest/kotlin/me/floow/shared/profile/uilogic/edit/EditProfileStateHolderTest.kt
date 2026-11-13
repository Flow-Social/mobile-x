package me.floow.shared.profile.uilogic.edit

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import me.floow.shared.profile.image.LocalImageFile
import me.floow.shared.profile.image.LocalImageFileReader
import me.floow.shared.profile.image.LocalImageReadResult
import me.floow.shared.profile.ui.edit.EditProfileState
import me.floow.shared.profile.uilogic.ProfilePost
import me.floow.shared.profile.uilogic.compose.PlatformImagePicker
import me.floow.shared.profile.uilogic.compose.PlatformPickedImage
import me.floow.shared.profile.uilogic.compose.PostCategory
import me.floow.shared.profile.uilogic.compose.PostComposerImageInput
import me.floow.shared.profile.uilogic.compose.PostComposerRepository
import me.floow.shared.profile.uilogic.compose.UploadKind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

@OptIn(ExperimentalCoroutinesApi::class)
class EditProfileStateHolderTest {

    private class FakeProfileEditorRepository : ProfileEditorRepository {
        var usernameAvailable = true
        var updatedName: String? = null
        var updatedUsername: String? = null
        var updatedBio: String? = null
        val mediaUpdates = mutableMapOf<String, String>()

        override suspend fun checkUsernameAvailability(username: String): Boolean = usernameAvailable

        override suspend fun updateProfile(name: String, username: String, bio: String): Result<Unit> {
            updatedName = name
            updatedUsername = username
            updatedBio = bio
            return Result.success(Unit)
        }

        override suspend fun updateProfileMedia(kind: String, mediaUrl: String): Result<Unit> {
            mediaUpdates[kind] = mediaUrl
            return Result.success(Unit)
        }
    }

    private class FakePlatformImagePicker : PlatformImagePicker {
        var imageToPick: PlatformPickedImage? = null

        override suspend fun pickSingleImage(): PlatformPickedImage? = imageToPick
        override suspend fun pickPostImages(maxItems: Int): List<PlatformPickedImage> =
            imageToPick?.let { listOf(it) } ?: emptyList()
    }

    private class FakeLocalImageFileReader : LocalImageFileReader {
        val readAvatarCalls = mutableListOf<String>()
        val readBackgroundCalls = mutableListOf<String>()

        override suspend fun read(uriString: String): LocalImageFile? = LocalImageFile(
            fileName = "image.jpg",
            contentType = "image/jpeg",
            sizeBytes = 100L,
            bytes = ByteArray(10),
            filePath = "/tmp/image.jpg"
        )

        override suspend fun readAvatar(uriString: String): LocalImageReadResult {
            readAvatarCalls.add(uriString)
            return LocalImageReadResult.Success(
                LocalImageFile(
                    fileName = "avatar.jpg",
                    contentType = "image/jpeg",
                    sizeBytes = 120L,
                    filePath = "/tmp/avatar.jpg"
                )
            )
        }

        override suspend fun readBackground(uriString: String): LocalImageReadResult {
            readBackgroundCalls.add(uriString)
            return LocalImageReadResult.Success(
                LocalImageFile(
                    fileName = "bg.jpg",
                    contentType = "image/jpeg",
                    sizeBytes = 200L,
                    filePath = "/tmp/bg.jpg"
                )
            )
        }
    }

    private class FakePostComposerRepository : PostComposerRepository {
        val uploadedFiles = mutableListOf<Pair<UploadKind, List<LocalImageFile>>>()

        override suspend fun loadCategories(): Result<List<PostCategory>> = Result.success(emptyList())

        override suspend fun uploadImages(
            files: List<LocalImageFile>,
            kind: UploadKind,
            onProgress: (uploaded: Int, total: Int) -> Unit
        ): Result<List<String>> {
            uploadedFiles.add(kind to files)
            val urls = files.mapIndexed { index, _ -> "https://cdn.example.com/${kind.name.lowercase()}_$index.jpg" }
            return Result.success(urls)
        }

        override suspend fun createPost(
            description: String?,
            images: List<PostComposerImageInput>,
            categoryCode: String,
            localPostId: String?
        ): Result<ProfilePost> = Result.failure(UnsupportedOperationException())

        override suspend fun updatePost(
            postId: String,
            description: String?,
            images: List<PostComposerImageInput>,
            categoryCode: String?
        ): Result<ProfilePost> = Result.failure(UnsupportedOperationException())
    }

    @Test
    fun setAvatarAndBackgroundUpdatesStateImmediately() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val repository = FakeProfileEditorRepository()
        val imagePicker = FakePlatformImagePicker()
        val imageFileReader = FakeLocalImageFileReader()
        val uploadsRepository = FakePostComposerRepository()

        val holder = EditProfileStateHolder(
            initialData = EditProfileOverlayData(
                name = "Alice",
                username = "alice",
                bio = "Bio",
                avatarUrl = "https://cdn.example.com/old_avatar.jpg",
                backgroundUrl = "https://cdn.example.com/old_bg.jpg",
            ),
            repository = repository,
            imagePicker = imagePicker,
            imageFileReader = imageFileReader,
            uploadsRepository = uploadsRepository,
            scope = testScope,
        )

        val initialState = holder.state.value
        assertIs<EditProfileState.Edit>(initialState)
        assertEquals("https://cdn.example.com/old_avatar.jpg", initialState.avatarRemoteUrl)
        assertEquals("https://cdn.example.com/old_bg.jpg", initialState.backgroundRemoteUrl)
        assertNull(initialState.avatarPreviewUri)
        assertNull(initialState.backgroundPreviewUri)

        // Set local avatar
        holder.setAvatar("content://media/external/images/media/123")
        val stateAfterAvatar = holder.state.value
        assertIs<EditProfileState.Edit>(stateAfterAvatar)
        assertEquals("content://media/external/images/media/123", stateAfterAvatar.avatarPreviewUri)

        // Set local background
        holder.setBackground("content://media/external/images/media/456")
        val stateAfterBg = holder.state.value
        assertIs<EditProfileState.Edit>(stateAfterBg)
        assertEquals("content://media/external/images/media/456", stateAfterBg.backgroundPreviewUri)
    }

    @Test
    fun saveUploadsLocalMediaAndUpdatesProfile() = runTest {
        val testDispatcher = StandardTestDispatcher(testScheduler)
        val testScope = TestScope(testDispatcher)

        val repository = FakeProfileEditorRepository()
        val imagePicker = FakePlatformImagePicker()
        val imageFileReader = FakeLocalImageFileReader()
        val uploadsRepository = FakePostComposerRepository()

        val holder = EditProfileStateHolder(
            initialData = EditProfileOverlayData(
                name = "Alice",
                username = "alice",
                bio = "Bio",
                avatarUrl = null,
                backgroundUrl = null,
            ),
            repository = repository,
            imagePicker = imagePicker,
            imageFileReader = imageFileReader,
            uploadsRepository = uploadsRepository,
            scope = testScope,
        )

        holder.setAvatar("content://media/avatar_test.jpg")
        holder.setBackground("content://media/bg_test.jpg")

        holder.save()
        testScope.advanceUntilIdle()

        // Verify imageFileReader was called
        assertEquals(listOf("content://media/avatar_test.jpg"), imageFileReader.readAvatarCalls)
        assertEquals(listOf("content://media/bg_test.jpg"), imageFileReader.readBackgroundCalls)

        // Verify uploads
        assertEquals(2, uploadsRepository.uploadedFiles.size)
        assertEquals(UploadKind.Avatar, uploadsRepository.uploadedFiles[0].first)
        assertEquals(UploadKind.Background, uploadsRepository.uploadedFiles[1].first)

        // Verify profile repository updates
        assertEquals("Alice", repository.updatedName)
        assertEquals("alice", repository.updatedUsername)
        assertEquals("Bio", repository.updatedBio)
        assertEquals("https://cdn.example.com/avatar_0.jpg", repository.mediaUpdates["avatar"])
        assertEquals("https://cdn.example.com/background_0.jpg", repository.mediaUpdates["background"])

        // Verify state is updated with remote URLs and local handles cleared
        val finalState = holder.state.value
        assertIs<EditProfileState.Edit>(finalState)
        assertEquals("https://cdn.example.com/avatar_0.jpg", finalState.avatarRemoteUrl)
        assertEquals("https://cdn.example.com/background_0.jpg", finalState.backgroundRemoteUrl)
    }
}
