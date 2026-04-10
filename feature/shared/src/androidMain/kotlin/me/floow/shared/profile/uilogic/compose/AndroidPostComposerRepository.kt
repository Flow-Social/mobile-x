package me.floow.shared.profile.uilogic.compose

import me.floow.domain.api.models.CreatePostData
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.CategoryCatalogRepository
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.UploadImageData
import me.floow.domain.data.repos.UploadsRepository
import me.floow.shared.profile.image.LocalImageFile
import me.floow.shared.profile.uilogic.ProfileImageVariant
import me.floow.shared.profile.uilogic.ProfilePost

class AndroidPostComposerRepository(
    private val categoryCatalogRepository: CategoryCatalogRepository,
    private val postsRepository: PostsRepository,
    private val uploadsRepository: UploadsRepository,
) : PostComposerRepository {
    override suspend fun loadCategories(): Result<List<PostCategory>> {
        return when (val response = categoryCatalogRepository.getOrRefresh()) {
            is GetDataResponse.Success -> Result.success(
                response.data.map { item ->
                    PostCategory(
                        code = item.code,
                        title = item.code.toDisplayCategoryTitle(),
                    )
                }
            )
            is GetDataResponse.Error -> Result.failure(IllegalStateException("Failed to load categories"))
        }
    }

    override suspend fun uploadImages(
        files: List<LocalImageFile>,
        kind: UploadKind,
        onProgress: (uploaded: Int, total: Int) -> Unit,
    ): Result<List<String>> {
        val uploadData = files.map { file ->
            UploadImageData(
                fileName = file.fileName,
                contentType = file.contentType,
                sizeBytes = file.sizeBytes,
                bytes = file.bytes,
            )
        }
        return when (val response = uploadsRepository.uploadImages(
            data = uploadData,
            kind = kind.toBackendKind(),
            onProgress = onProgress,
        )) {
            is GetDataResponse.Success -> Result.success(response.data)
            is GetDataResponse.Error -> Result.failure(IllegalStateException("Failed to upload images"))
        }
    }

    override suspend fun createPost(
        description: String?,
        imageUrls: List<String>,
        categoryCode: String,
    ): Result<ProfilePost> {
        return when (val response = postsRepository.createPost(
            CreatePostData(
                imageUrls = imageUrls,
                description = description,
                category = categoryCode,
            )
        )) {
            is UpdateDataResponse.Success -> {
                Result.success(
                    ProfilePost(
                        id = "",
                        description = description,
                        imageVariants = imageUrls.map { url ->
                            ProfileImageVariant(
                                lqUrl = url,
                                previewUrl = url,
                                fullUrl = url,
                            )
                        },
                        imageUrls = imageUrls,
                        likesCount = 0,
                        commentsCount = 0,
                    )
                )
            }
            is UpdateDataResponse.Failure -> Result.failure(IllegalStateException("Failed to create post"))
        }
    }

    override suspend fun updatePost(
        postId: String,
        description: String?,
        imageUrls: List<String>,
    ): Result<ProfilePost> {
        return when (val response = postsRepository.updatePost(postId, description, imageUrls)) {
            is UpdateDataResponse.Success -> {
                Result.success(
                    ProfilePost(
                        id = postId,
                        description = description,
                        imageVariants = imageUrls.map { url ->
                            ProfileImageVariant(
                                lqUrl = url,
                                previewUrl = url,
                                fullUrl = url,
                            )
                        },
                        imageUrls = imageUrls,
                        likesCount = 0,
                        commentsCount = 0,
                    )
                )
            }
            is UpdateDataResponse.Failure -> Result.failure(IllegalStateException("Failed to update post"))
        }
    }

    private companion object {
        const val SELF_USER_ID = "me"
    }
}

private fun String.toDisplayCategoryTitle(): String {
    return split('_', '-')
        .filter(String::isNotBlank)
        .joinToString(" ") { part ->
            part.lowercase().replaceFirstChar { char -> char.uppercase() }
        }
        .ifBlank { this }
}

private fun UploadKind.toBackendKind(): String {
    return when (this) {
        UploadKind.Post -> "post"
        UploadKind.Avatar -> "avatar"
        UploadKind.Background -> "background"
    }
}
