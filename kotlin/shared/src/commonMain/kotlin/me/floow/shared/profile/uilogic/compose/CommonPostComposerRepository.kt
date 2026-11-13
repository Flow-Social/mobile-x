package me.floow.shared.profile.uilogic.compose

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import me.floow.domain.api.models.CreatePostData
import me.floow.domain.cache.PendingPostMutation
import me.floow.domain.cache.PendingPostMutationState
import me.floow.domain.cache.PendingPostMutationType
import me.floow.domain.cache.PendingPostsLocalStore
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.CategoryCatalogRepository
import me.floow.domain.data.repos.PendingPostImageEntry
import me.floow.domain.data.repos.PendingPostImageEntryKind
import me.floow.domain.data.repos.PendingPostImagePayload
import me.floow.domain.data.repos.PendingPostMediaStore
import me.floow.domain.data.repos.PendingPostStagedMedia
import me.floow.domain.data.repos.PendingPostsSyncScheduler
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.UploadImageData
import me.floow.domain.data.repos.UploadsRepository
import me.floow.domain.models.Post
import me.floow.domain.models.PostAuthor
import me.floow.domain.models.PostContent
import me.floow.domain.models.previewImageUrls
import me.floow.domain.models.resolvedImageVariants
import me.floow.domain.models.viewerImageUrls
import me.floow.domain.utils.currentTimeMillis
import me.floow.domain.values.util.RawValueObjectCreate
import me.floow.shared.profile.uilogic.ProfileImageVariant
import me.floow.shared.profile.uilogic.ProfilePost
import me.floow.shared.profile.uilogic.ProfilePostOptimisticState
import me.floow.shared.profile.uilogic.ProfilePostPendingMutationType
import me.floow.shared.profile.uilogic.ProfilePostSyncState
import kotlin.random.Random

private fun generatePostUuid(): String {
    return "${currentTimeMillis()}_${Random.nextLong(100000, 999999)}"
}

class CommonPostComposerRepository(
    private val categoryCatalogRepository: CategoryCatalogRepository,
    private val postsRepository: PostsRepository,
    private val uploadsRepository: UploadsRepository,
    private val postsLocalStore: PostsLocalStore,
    private val pendingPostsLocalStore: PendingPostsLocalStore,
    private val pendingPostsSyncScheduler: PendingPostsSyncScheduler,
    private val pendingPostMediaStore: PendingPostMediaStore,
) : PostComposerRepository {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

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
        files: List<me.floow.shared.profile.image.LocalImageFile>,
        kind: UploadKind,
        onProgress: (uploaded: Int, total: Int) -> Unit,
    ): Result<List<String>> {
        val uploadData = files.map { file ->
            UploadImageData(
                fileName = file.fileName,
                contentType = file.contentType,
                sizeBytes = file.sizeBytes,
                bytes = file.bytes,
                filePath = file.filePath,
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
        images: List<PostComposerImageInput>,
        categoryCode: String,
        localPostId: String?,
    ): Result<ProfilePost> {
        if (images.isEmpty()) {
            return Result.failure(IllegalArgumentException("Create post requires at least one image"))
        }
        val localFiles = images.mapNotNull(PostComposerImageInput::localFile)
        if (localFiles.isEmpty()) {
            return Result.failure(IllegalArgumentException("No valid image files provided"))
        }

        val now = currentTimeMillis()
        val commandId = generatePostUuid()
        val effectiveLocalPostId = localPostId ?: "pending_create_${generatePostUuid()}"
        val clientMutationId = generatePostUuid()

        val stagedMedia = pendingPostMediaStore.stageFiles(
            commandId = commandId,
            files = localFiles.map { file ->
                UploadImageData(
                    fileName = file.fileName,
                    contentType = file.contentType,
                    sizeBytes = file.sizeBytes,
                    bytes = file.bytes,
                    filePath = file.filePath,
                )
            }
        )
        // Clean up transient preparer temp files if they were staged into pending post storage directory
        localFiles.forEach { file ->
            val path = file.filePath
            if (path != null && stagedMedia.none { it.absolutePath == path }) {
                me.floow.shared.media.pipeline.deletePreparedTempFile(path)
            }
        }
        val payload = images.toPendingImagePayload(stagedMedia)
        val optimisticPost = createOptimisticProfilePost(
            id = effectiveLocalPostId,
            description = description,
            category = categoryCode,
            createdAtMillis = now,
            payload = payload,
            stagedMedia = stagedMedia,
            mutationType = ProfilePostPendingMutationType.CREATE,
            syncState = ProfilePostSyncState.PENDING,
            remotePostId = null,
            lastError = null,
            likesCount = 0,
            commentsCount = 0,
            commentersPreview = emptyList(),
        )

        pendingPostsLocalStore.upsert(
            PendingPostMutation(
                commandId = commandId,
                type = PendingPostMutationType.CREATE,
                localPostId = effectiveLocalPostId,
                remotePostId = null,
                clientMutationId = clientMutationId,
                state = PendingPostMutationState.PENDING,
                description = description,
                category = categoryCode,
                imagePayloadJson = json.encodeToString(payload),
                stagedMediaJson = json.encodeToString(stagedMedia),
                previousPostSnapshotJson = null,
                lastError = null,
                retryCount = 0,
                nextRetryAt = now,
                createdAt = now,
                updatedAt = now,
            )
        )
        pendingPostsSyncScheduler.enqueueNow()
        pendingPostsSyncScheduler.ensurePeriodic()

        return Result.success(optimisticPost)
    }

    override suspend fun updatePost(
        postId: String,
        description: String?,
        images: List<PostComposerImageInput>,
        categoryCode: String?,
    ): Result<ProfilePost> {
        if (images.isEmpty()) {
            return Result.failure(IllegalArgumentException("Update post requires at least one image"))
        }
        val existingPost = postsLocalStore.getPost(SELF_USER_ID, postId)
            ?: refreshSelfPostsCacheAndFindPost(
                fallbackPost = null,
                fallbackMatcher = { candidate -> candidate.id == postId }
            ).getOrNull()
            ?: return Result.failure(IllegalArgumentException("Post $postId was not found in local cache"))
        val now = currentTimeMillis()
        val commandId = generatePostUuid()
        val stagedMedia = pendingPostMediaStore.stageFiles(
            commandId = commandId,
            files = images.mapNotNull(PostComposerImageInput::localFile).map { file ->
                UploadImageData(
                    fileName = file.fileName,
                    contentType = file.contentType,
                    sizeBytes = file.sizeBytes,
                    bytes = file.bytes,
                    filePath = file.filePath,
                )
            }
        )
        val payload = images.toPendingImagePayload(stagedMedia)
        val previousSnapshot = ExistingPostSnapshot.from(existingPost)
        val optimisticPost = createOptimisticProfilePost(
            id = postId,
            description = description,
            category = categoryCode ?: existingPost.category,
            createdAtMillis = existingPost.createdAt,
            payload = payload,
            stagedMedia = stagedMedia,
            mutationType = ProfilePostPendingMutationType.UPDATE,
            syncState = ProfilePostSyncState.PENDING,
            remotePostId = postId,
            lastError = null,
            likesCount = existingPost.likesCount,
            commentsCount = existingPost.commentsCount,
            commentersPreview = existingPost.commentersPreview,
        ).copy(
            optimisticState = null,
            syncState = null,
            pendingMutationType = null,
            isOptimistic = false,
            lastSyncError = null,
        )
        postsLocalStore.upsertPost(
            userId = SELF_USER_ID,
            post = existingPost.toOptimisticDomainPost(
                description = description,
                category = categoryCode,
                payload = payload,
                stagedMedia = stagedMedia,
            ),
            updatedAt = now,
        )
        pendingPostsLocalStore.upsert(
            PendingPostMutation(
                commandId = commandId,
                type = PendingPostMutationType.UPDATE,
                localPostId = postId,
                remotePostId = postId,
                clientMutationId = generatePostUuid(),
                state = PendingPostMutationState.PENDING,
                description = description,
                category = categoryCode ?: existingPost.category,
                imagePayloadJson = json.encodeToString(payload),
                stagedMediaJson = json.encodeToString(stagedMedia),
                previousPostSnapshotJson = json.encodeToString(previousSnapshot),
                lastError = null,
                retryCount = 0,
                nextRetryAt = now,
                createdAt = now,
                updatedAt = now,
            )
        )
        pendingPostsSyncScheduler.enqueueNow()
        pendingPostsSyncScheduler.ensurePeriodic()
        return Result.success(optimisticPost)
    }

    fun observeSelfPosts(): Flow<List<ProfilePost>> {
        return combine(
            postsLocalStore.observePosts(SELF_USER_ID),
            pendingPostsLocalStore.observeAllPendingForSelf(),
        ) { remotePosts, pendingCommands ->
            mergePendingPosts(
                remotePosts = remotePosts.map { it.toSharedProfilePost() },
                pendingCommands = pendingCommands,
            )
        }
    }

    private suspend fun refreshSelfPostsCacheAndFindPost(
        fallbackPost: Post?,
        fallbackMatcher: (Post) -> Boolean,
    ): Result<Post> {
        return when (val response = postsRepository.getUserPosts(
            userId = SELF_USER_ID,
            forceNetwork = true,
        )) {
            is GetDataResponse.Success -> {
                val refreshedPosts = response.data
                if (refreshedPosts.isEmpty()) {
                    return fallbackPost?.let(Result.Companion::success)
                        ?: Result.failure(IllegalStateException("Self posts list is empty"))
                }

                val updatedAt = currentTimeMillis()
                postsLocalStore.replacePosts(
                    userId = SELF_USER_ID,
                    posts = refreshedPosts,
                    updatedAt = updatedAt,
                )

                val selfAuthorId = refreshedPosts.firstOrNull()?.author?.id
                    ?.takeIf { it.isNotBlank() && it != SELF_USER_ID }
                if (selfAuthorId != null) {
                    postsLocalStore.replacePosts(
                        userId = selfAuthorId,
                        posts = refreshedPosts,
                        updatedAt = updatedAt,
                    )
                }

                val updatedPost = refreshedPosts.firstOrNull(fallbackMatcher)
                    ?: fallbackPost
                    ?: return Result.failure(IllegalStateException("Updated post was not found after refresh"))

                Result.success(updatedPost)
            }
            is GetDataResponse.Error -> {
                fallbackPost?.let(Result.Companion::success)
                    ?: Result.failure(IllegalStateException("Failed to refresh self posts cache"))
            }
        }
    }

    private fun mergePendingPosts(
        remotePosts: List<ProfilePost>,
        pendingCommands: List<PendingPostMutation>,
    ): List<ProfilePost> {
        val activeCommands = pendingCommands.filter { command ->
            command.state != PendingPostMutationState.DONE && command.state != PendingPostMutationState.CANCELED
        }
        if (activeCommands.isEmpty()) return remotePosts

        val hiddenRemoteIds = activeCommands
            .filter { it.type == PendingPostMutationType.DELETE }
            .mapNotNullTo(hashSetOf()) { it.remotePostId }
        val updateCommandsByRemoteId = activeCommands
            .filter { it.type == PendingPostMutationType.UPDATE }
            .mapNotNull { command -> command.remotePostId?.let { it to command } }
            .toMap()

        val mergedRemote = remotePosts
            .filterNot { post -> post.id in hiddenRemoteIds }
            .map { post ->
                val command = updateCommandsByRemoteId[post.id] ?: return@map post
                command.toPendingProfilePost(basePost = post)
            }

        val pendingCreates = activeCommands
            .filter { it.type == PendingPostMutationType.CREATE }
            .map { it.toPendingProfilePost(basePost = null) }
            .filterNot { pending -> mergedRemote.any { remote -> remote.id == pending.id || remote.id == pending.remotePostId } }

        return pendingCreates + mergedRemote
    }

    private fun PendingPostMutation.toPendingProfilePost(basePost: ProfilePost?): ProfilePost {
        val stagedMedia = decodeStagedMedia(stagedMediaJson)
        val payload = decodeImagePayload(imagePayloadJson)
        val imageUrls = payload.resolveDisplayUrls(stagedMedia, pendingPostMediaStore)
        val remoteId = remotePostId ?: basePost?.remotePostId
        val shouldExposePendingUi = type != PendingPostMutationType.UPDATE
        return ProfilePost(
            id = if (type == PendingPostMutationType.CREATE) localPostId else (remotePostId ?: basePost?.id ?: localPostId),
            description = description ?: basePost?.description,
            imageVariants = imageUrls.map { url ->
                ProfileImageVariant(
                    thumbUrl = url,
                    fullUrl = url,
                )
            },
            imageUrls = imageUrls,
            likesCount = basePost?.likesCount ?: 0,
            commentsCount = basePost?.commentsCount ?: 0,
            commentersPreview = basePost?.commentersPreview ?: emptyList(),
            category = category ?: basePost?.category,
            createdAtMillis = basePost?.createdAtMillis ?: createdAt,
            optimisticState = state.toOptimisticState().takeIf { shouldExposePendingUi },
            syncState = state.toSyncState().takeIf { shouldExposePendingUi },
            pendingMutationType = type.toUiMutationType().takeIf { shouldExposePendingUi },
            isOptimistic = shouldExposePendingUi,
            localPostId = localPostId,
            remotePostId = remoteId,
            lastSyncError = lastError.takeIf { shouldExposePendingUi },
        )
    }

    private fun createOptimisticProfilePost(
        id: String,
        description: String?,
        category: String?,
        createdAtMillis: Long,
        payload: PendingPostImagePayload,
        stagedMedia: List<PendingPostStagedMedia>,
        mutationType: ProfilePostPendingMutationType,
        syncState: ProfilePostSyncState,
        remotePostId: String?,
        lastError: String?,
        likesCount: Int = 0,
        commentsCount: Int = 0,
        commentersPreview: List<String> = emptyList(),
    ): ProfilePost {
        val imageUrls = payload.resolveDisplayUrls(stagedMedia, pendingPostMediaStore)
        return ProfilePost(
            id = id,
            description = description,
            imageVariants = imageUrls.map { url ->
                ProfileImageVariant(
                    thumbUrl = url,
                    fullUrl = url,
                )
            },
            imageUrls = imageUrls,
            likesCount = likesCount,
            commentsCount = commentsCount,
            commentersPreview = commentersPreview,
            category = category,
            createdAtMillis = createdAtMillis,
            optimisticState = when (syncState) {
                ProfilePostSyncState.FAILED -> ProfilePostOptimisticState.FAILED
                else -> ProfilePostOptimisticState.PENDING
            },
            syncState = syncState,
            pendingMutationType = mutationType,
            isOptimistic = true,
            localPostId = id,
            remotePostId = remotePostId,
            lastSyncError = lastError,
        )
    }

    private fun List<PostComposerImageInput>.toPendingImagePayload(stagedMedia: List<PendingPostStagedMedia>): PendingPostImagePayload {
        val stagedIterator = stagedMedia.iterator()
        return PendingPostImagePayload(
            entries = map { image ->
                val remoteUrl = image.remoteUrl
                if (remoteUrl != null) {
                    PendingPostImageEntry(
                        kind = PendingPostImageEntryKind.REMOTE,
                        remoteUrl = remoteUrl,
                    )
                } else {
                    val staged = stagedIterator.next()
                    PendingPostImageEntry(
                        kind = PendingPostImageEntryKind.STAGED,
                        stagedMediaId = staged.id,
                    )
                }
            }
        )
    }

    private fun PendingPostImagePayload.resolveDisplayUrls(
        stagedMedia: List<PendingPostStagedMedia>,
        mediaStore: PendingPostMediaStore,
    ): List<String> {
        val stagedById = stagedMedia.associateBy(PendingPostStagedMedia::id)
        return entries.mapNotNull { entry ->
            when (entry.kind) {
                PendingPostImageEntryKind.REMOTE -> entry.remoteUrl
                PendingPostImageEntryKind.STAGED -> stagedById[entry.stagedMediaId]?.let(mediaStore::previewUri)
            }
        }
    }

    private fun decodeImagePayload(payloadJson: String): PendingPostImagePayload {
        return runCatching {
            json.decodeFromString<PendingPostImagePayload>(payloadJson)
        }.getOrElse {
            PendingPostImagePayload()
        }
    }

    private fun decodeStagedMedia(payloadJson: String): List<PendingPostStagedMedia> {
        return runCatching {
            json.decodeFromString<List<PendingPostStagedMedia>>(payloadJson)
        }.getOrElse {
            emptyList()
        }
    }

    private companion object {
        const val SELF_USER_ID = "me"
    }
}

// Typealias for backward compatibility with existing androidMain code
typealias AndroidPostComposerRepository = CommonPostComposerRepository

private fun PendingPostMutationState.toOptimisticState(): ProfilePostOptimisticState {
    return when (this) {
        PendingPostMutationState.FAILED -> ProfilePostOptimisticState.FAILED
        PendingPostMutationState.PENDING,
        PendingPostMutationState.UPLOADING,
        PendingPostMutationState.SYNCING,
        PendingPostMutationState.DONE,
        PendingPostMutationState.CANCELED -> ProfilePostOptimisticState.PENDING
    }
}

private fun PendingPostMutationState.toSyncState(): ProfilePostSyncState {
    return when (this) {
        PendingPostMutationState.PENDING -> ProfilePostSyncState.PENDING
        PendingPostMutationState.UPLOADING -> ProfilePostSyncState.UPLOADING
        PendingPostMutationState.SYNCING -> ProfilePostSyncState.SYNCING
        PendingPostMutationState.FAILED -> ProfilePostSyncState.FAILED
        PendingPostMutationState.DONE,
        PendingPostMutationState.CANCELED -> ProfilePostSyncState.PENDING
    }
}

private fun PendingPostMutationType.toUiMutationType(): ProfilePostPendingMutationType {
    return when (this) {
        PendingPostMutationType.CREATE -> ProfilePostPendingMutationType.CREATE
        PendingPostMutationType.UPDATE -> ProfilePostPendingMutationType.UPDATE
        PendingPostMutationType.DELETE -> ProfilePostPendingMutationType.DELETE
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

private fun Post.toSharedProfilePost(): ProfilePost {
    return ProfilePost(
        id = id,
        description = content.description,
        imageVariants = content.resolvedImageVariants().map { variant ->
            ProfileImageVariant(
                thumbUrl = variant.thumbUrl,
                fullUrl = variant.fullUrl,
            )
        },
        imageUrls = content.viewerImageUrls().ifEmpty { content.previewImageUrls() },
        likesCount = likesCount,
        commentsCount = commentsCount,
        commentersPreview = commentersPreview,
        category = category,
        createdAtMillis = createdAt,
        remotePostId = id,
    )
}

@OptIn(RawValueObjectCreate::class)
private fun Post.toOptimisticDomainPost(
    description: String?,
    category: String?,
    payload: PendingPostImagePayload,
    stagedMedia: List<PendingPostStagedMedia>,
): Post {
    val stagedById = stagedMedia.associateBy(PendingPostStagedMedia::id)
    val imageUrls = payload.entries.mapNotNull { entry ->
        when (entry.kind) {
            PendingPostImageEntryKind.REMOTE -> entry.remoteUrl
            PendingPostImageEntryKind.STAGED -> stagedById[entry.stagedMediaId]?.absolutePath?.let { "file://$it" }
        }
    }
    return copy(
        content = PostContent(
            imageUrls = imageUrls,
            description = description,
            imageVariants = emptyList(),
        ),
        category = category ?: this.category,
    )
}

@Serializable
private data class ExistingPostSnapshot(
    val id: String,
    val description: String?,
    val imageUrls: List<String>,
    val category: String,
    val createdAt: Long,
    val likesCount: Int,
    val commentsCount: Int,
    val commentersPreview: List<String>,
) {
    companion object {
        fun from(post: Post): ExistingPostSnapshot {
            return ExistingPostSnapshot(
                id = post.id,
                description = post.content.description,
                imageUrls = post.content.viewerImageUrls().ifEmpty { post.content.previewImageUrls() },
                category = post.category,
                createdAt = post.createdAt,
                likesCount = post.likesCount,
                commentsCount = post.commentsCount,
                commentersPreview = post.commentersPreview,
            )
        }
    }
}
