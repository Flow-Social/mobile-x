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

interface PostComposerRepository {
    suspend fun loadCategories(): Result<List<PostCategory>>

    suspend fun uploadImages(
        files: List<LocalImageFile>,
        kind: UploadKind,
        onProgress: (uploaded: Int, total: Int) -> Unit,
    ): Result<List<String>>

    suspend fun createPost(
        description: String?,
        imageUrls: List<String>,
        categoryCode: String,
    ): Result<ProfilePost>

    suspend fun updatePost(
        postId: String,
        description: String?,
        imageUrls: List<String>,
    ): Result<ProfilePost>
}
