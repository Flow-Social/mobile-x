package me.floow.shared.post.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.models.PostImageVariant
import me.floow.shared.chats.ui.currentChatEpochMillis
import me.floow.shared.profile.uilogic.syncDeletedPostCaches as syncProfileDeletedPostCaches
import me.floow.uikit.components.media.viewer2.HostedFullscreenImageViewer
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.components.media.transfer.PostMediaTransferStore

@Composable
fun SharedPostRoute(
    postId: String,
    imageUrls: List<String>,
    imageVariants: List<PostImageVariant> = emptyList(),
    mediaTransferToken: String? = null,
    description: String?,
    authorId: String,
    authorName: String?,
    authorUsername: String?,
    authorAvatarUrl: String?,
    category: String,
    createdAtLabel: String,
    topBarCreatedAtLabel: String = createdAtLabel,
    descriptionTimeLabel: String = createdAtLabel,
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
    onPresentFullscreenViewer: ((HostedFullscreenImageViewer?) -> Unit)? = null,
    presentation: PostScreenPresentation = PostScreenPresentation.FullscreenLegacy,
    onProfileModalScrollAtTopChanged: (Boolean) -> Unit = {},
    topBarModifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
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
            imageVariants = imageVariants,
            mediaTransferSnapshot = initialMediaSnapshot,
            description = description,
            authorId = authorId,
            authorName = authorName,
            authorUsername = authorUsername,
            authorAvatarUrl = authorAvatarUrl,
            category = category,
            createdAtLabel = createdAtLabel,
            topBarCreatedAtLabel = topBarCreatedAtLabel,
            descriptionTimeLabel = descriptionTimeLabel,
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
        presentation = presentation,
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
        onPresentFullscreenViewer = onPresentFullscreenViewer,
        onProfileModalScrollAtTopChanged = onProfileModalScrollAtTopChanged,
        topBarModifier = topBarModifier,
        modifier = modifier,
    )
}

internal suspend fun syncDeletedPostCaches(
    postId: String,
    authorId: String,
    postsLocalStore: PostsLocalStore,
    profileLocalStore: ProfileLocalStore,
) {
    syncProfileDeletedPostCaches(
        postId = postId,
        authorId = authorId,
        postsLocalStore = postsLocalStore,
        profileLocalStore = profileLocalStore,
        updatedAt = currentChatEpochMillis(),
    )
}
