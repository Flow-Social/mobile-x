package me.floow.shared.post.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.PostsRepository
import me.floow.shared.chats.ui.currentChatEpochMillis
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.components.media.transfer.PostMediaTransferStore

@Composable
fun SharedPostRoute(
    postId: String,
    imageUrls: List<String>,
    mediaTransferToken: String? = null,
    description: String?,
    authorId: String,
    authorName: String?,
    authorUsername: String?,
    authorAvatarUrl: String?,
    category: String,
    createdAtLabel: String,
    likesCount: Int,
    commentsCount: Int,
    commentersPreview: List<String>,
    isSelf: Boolean,
    postsRepository: PostsRepository,
    postsLocalStore: PostsLocalStore,
    profileLocalStore: ProfileLocalStore,
    postMediaTransferStore: PostMediaTransferStore,
    onBackClick: () -> Unit,
    onProfileClick: (String) -> Unit,
    onCommentsClick: (PostMediaSourceSnapshot?) -> Unit,
    onSharePost: (String) -> Unit,
    buildPostShareUrl: (String, String?) -> String,
    onEditPost: (PostMediaSourceSnapshot?) -> Unit = {},
    onPostDeleted: () -> Unit,
    onProfileTagClick: (String) -> Unit = {},
    onPostLinkClick: (String, String) -> Unit = { _, _ -> },
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
) {
    val scope = rememberCoroutineScope()
    val initialMediaSnapshot = remember(postId, mediaTransferToken, postMediaTransferStore) {
        mediaTransferToken
            ?.let(postMediaTransferStore::consume)
            ?.takeIf { it.postId == postId }
            ?: postMediaTransferStore.peek(postId)?.takeIf { it.postId == postId }
    }

    PostScreen(
        model = PostScreenModel(
            postId = postId,
            imageUrls = imageUrls,
            mediaTransferSnapshot = initialMediaSnapshot,
            description = description,
            authorId = authorId,
            authorName = authorName,
            authorUsername = authorUsername,
            authorAvatarUrl = authorAvatarUrl,
            category = category,
            createdAtLabel = createdAtLabel,
            likesCount = likesCount,
            commentsCount = commentsCount,
            commentersPreview = commentersPreview,
            isSelf = isSelf,
        ),
        onBackClick = onBackClick,
        onProfileClick = onProfileClick,
        onCommentsClick = onCommentsClick,
        onProfileTagClick = onProfileTagClick,
        onPostLinkClick = onPostLinkClick,
        onShareClick = {
            onSharePost(buildPostShareUrl(postId, authorUsername))
        },
        onEditPost = onEditPost,
        onDeletePost = {
            scope.launch {
                val result = postsRepository.deletePost(postId)
                if (result is UpdateDataResponse.Success) {
                    if (isSelf) {
                        syncDeletedPostCaches(
                            postId = postId,
                            authorId = authorId,
                            postsLocalStore = postsLocalStore,
                            profileLocalStore = profileLocalStore,
                        )
                    }
                    onPostDeleted()
                }
            }
        },
        modifier = modifier,
    )
}

internal suspend fun syncDeletedPostCaches(
    postId: String,
    authorId: String,
    postsLocalStore: PostsLocalStore,
    profileLocalStore: ProfileLocalStore,
) {
    val cacheKeys = buildList {
        add("me")
        if (authorId.isNotBlank()) add(authorId)
    }.distinct()

    cacheKeys.forEach { userId ->
        val currentPosts = postsLocalStore.observePosts(userId).first()
        val deletedPost = currentPosts.firstOrNull { post -> post.id == postId }
        val updatedPosts = currentPosts.filterNot { post -> post.id == postId }
        if (updatedPosts.size == currentPosts.size) return@forEach

        val updatedAt = currentChatEpochMillis()
        postsLocalStore.replacePosts(
            userId = userId,
            posts = updatedPosts,
            updatedAt = updatedAt,
        )

        val likesToSubtract = deletedPost?.likesCount ?: 0
        if (likesToSubtract <= 0) return@forEach

        val cachedProfile = profileLocalStore.observeProfile(userId).first() ?: return@forEach
        profileLocalStore.upsertProfile(
            userId = userId,
            profile = cachedProfile.copy(
                totalLikesReceived = (cachedProfile.totalLikesReceived - likesToSubtract).coerceAtLeast(0),
            ),
            updatedAt = updatedAt,
        )
    }
}
