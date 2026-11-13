package me.floow.shared.profile.uilogic.compose

import me.floow.shared.profile.image.LocalImageFile
import me.floow.shared.profile.uilogic.ProfilePost

data class PostCategory(
    val code: String,
    val title: String,
)

enum class UploadKind {
    Post,
    Avatar,
    Background,
}

data class PostComposerImageInput(
    val remoteUrl: String? = null,
    val localFile: LocalImageFile? = null,
) {
    init {
        require((remoteUrl != null) xor (localFile != null)) {
            "Exactly one image source must be provided"
        }
    }
}

interface PostComposerRepository {
    suspend fun loadCategories(): Result<List<PostCategory>>

    suspend fun uploadImages(
        files: List<LocalImageFile>,
        kind: UploadKind,
        onProgress: (uploaded: Int, total: Int) -> Unit,
    ): Result<List<String>>

    suspend fun createPost(
        description: String?,
        images: List<PostComposerImageInput>,
        categoryCode: String,
        localPostId: String? = null,
    ): Result<ProfilePost>

    suspend fun updatePost(
        postId: String,
        description: String?,
        images: List<PostComposerImageInput>,
        categoryCode: String? = null,
    ): Result<ProfilePost>
}
