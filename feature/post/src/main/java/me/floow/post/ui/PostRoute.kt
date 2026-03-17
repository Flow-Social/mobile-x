package me.floow.post.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.first
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.deeplink.DeepLinkUrls
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.PostsRepository
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.components.media.transfer.PostMediaTransferStore
import me.floow.uikit.util.SetStatusBarStyle
import org.koin.compose.koinInject

@Composable
fun PostRoute(
    postId: String,
    imageUrls: List<String>,
    mediaTransferToken: String? = null,
    description: String?,
    authorId: String,
    authorName: String?,
    authorUsername: String?,
    authorAvatarUrl: String?,
    category: String,
    createdAt: Long,
    likesCount: Int,
    commentsCount: Int,
    commentersPreview: List<String>,
    isSelf: Boolean,
    onBackClick: () -> Unit,
    onProfileClick: (String) -> Unit,
    onCommentsClick: (PostMediaSourceSnapshot?) -> Unit,
    sharePost: (String) -> Unit,
    onEditPost: (PostMediaSourceSnapshot?) -> Unit = {},
    onPostUpdated: () -> Unit,
    onPostDeleted: () -> Unit,
    onProfileTagClick: (String) -> Unit = {},
    onPostLinkClick: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val repository: PostsRepository = koinInject()
    val postsLocalStore: PostsLocalStore = koinInject()
    val profileLocalStore: ProfileLocalStore = koinInject()
    val mediaTransferStore: PostMediaTransferStore = koinInject()
    val scope = rememberCoroutineScope()
    val statusBarColor = MaterialTheme.colorScheme.background
    val useDarkStatusIcons = statusBarColor.luminance() > 0.5f
    val initialMediaSnapshot = remember(postId, mediaTransferToken) {
        mediaTransferToken
            ?.let(mediaTransferStore::consume)
            ?.takeIf { it.postId == postId }
            ?: mediaTransferStore.peek(postId)?.takeIf { it.postId == postId }
    }

    SetStatusBarStyle(
        color = statusBarColor,
        darkIcons = useDarkStatusIcons
    )

    PostScreen(
        postId = postId,
        imageUrls = imageUrls,
        mediaTransferSnapshot = initialMediaSnapshot,
        description = description,
        authorId = authorId,
        authorName = authorName,
        authorUsername = authorUsername,
        authorAvatarUrl = authorAvatarUrl,
        category = category,
        createdAt = createdAt,
        likesCount = likesCount,
        commentsCount = commentsCount,
        commentersPreview = commentersPreview,
        isSelf = isSelf,
        onBackClick = onBackClick,
        onProfileClick = onProfileClick,
        onCommentsClick = onCommentsClick,
        onProfileTagClick = onProfileTagClick,
        onPostLinkClick = onPostLinkClick,
        onShareClick = {
            val username = authorUsername?.takeIf { it.isNotBlank() } ?: authorId
            sharePost(DeepLinkUrls.postUrl(postId, username))
        },
        onEditPost = onEditPost,
        onDeletePost = {
            scope.launch {
                val result = repository.deletePost(postId)
                if (result is UpdateDataResponse.Success) {
                    if (isSelf) {
                        val cacheKeys = buildList {
                            add("me")
                            if (authorId.isNotBlank()) add(authorId)
                        }.distinct()

                        cacheKeys.forEach { userId ->
                            val currentPosts = postsLocalStore.observePosts(userId).first()
                            val deletedPost = currentPosts.firstOrNull { post -> post.id == postId }
                            val updatedPosts = currentPosts.filter { post -> post.id != postId }
                            if (updatedPosts.size != currentPosts.size) {
                                postsLocalStore.replacePosts(
                                    userId = userId,
                                    posts = updatedPosts,
                                    updatedAt = System.currentTimeMillis()
                                )

                                val likesToSubtract = deletedPost?.likesCount ?: 0
                                if (likesToSubtract > 0) {
                                    val cachedProfile = profileLocalStore.observeProfile(userId).first()
                                    if (cachedProfile != null) {
                                        profileLocalStore.upsertProfile(
                                            userId = userId,
                                            profile = cachedProfile.copy(
                                                totalLikesReceived = (cachedProfile.totalLikesReceived - likesToSubtract)
                                                    .coerceAtLeast(0)
                                            ),
                                            updatedAt = System.currentTimeMillis()
                                        )
                                    }
                                }
                            }
                        }
                    }
                    onPostDeleted()
                }
            }
        },
        modifier = modifier
    )
}
