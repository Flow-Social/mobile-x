package me.floow.shared.post.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.deeplink.DeepLinkUrls
import me.floow.domain.models.PostImageVariant
import me.floow.uikit.components.media.viewer2.HostedFullscreenImageViewer
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.components.media.transfer.PostMediaTransferStore
import me.floow.uikit.util.FlowTimeFormatters
import me.floow.uikit.util.SetStatusBarStyle
import org.koin.compose.koinInject

@Composable
fun PostRoute(
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
    onPresentFullscreenViewer: ((HostedFullscreenImageViewer?) -> Unit)? = null,
    presentation: PostScreenPresentation = PostScreenPresentation.FullscreenLegacy,
    onProfileModalScrollAtTopChanged: (Boolean) -> Unit = {},
    topBarModifier: Modifier = Modifier,
    modifier: Modifier = Modifier
) {
    val postsRepository: PostsRepository = koinInject()
    val postsLocalStore: PostsLocalStore = koinInject()
    val profileLocalStore: ProfileLocalStore = koinInject()
    val mediaTransferStore: PostMediaTransferStore = koinInject()
    val statusBarColor = MaterialTheme.colorScheme.background
    val useDarkStatusIcons = statusBarColor.luminance() > 0.5f
    val topBarCreatedAtLabel = FlowTimeFormatters.formatRelativeTime(createdAt)
    val descriptionTimeLabel = FlowTimeFormatters.formatHourMinute(createdAt)

    if (presentation != PostScreenPresentation.ProfileModal) {
        SetStatusBarStyle(
            color = statusBarColor,
            darkIcons = useDarkStatusIcons
        )
    }

    SharedPostRoute(
        postId = postId,
        imageUrls = imageUrls,
        imageVariants = imageVariants,
        mediaTransferToken = mediaTransferToken,
        description = description,
        authorId = authorId,
        authorName = authorName,
        authorUsername = authorUsername,
        authorAvatarUrl = authorAvatarUrl,
        category = category,
        createdAtLabel = topBarCreatedAtLabel,
        topBarCreatedAtLabel = topBarCreatedAtLabel,
        descriptionTimeLabel = descriptionTimeLabel,
        likesCount = likesCount,
        commentsCount = commentsCount,
        commentersPreview = commentersPreview,
        isSelf = isSelf,
        postsRepository = postsRepository,
        postsLocalStore = postsLocalStore,
        profileLocalStore = profileLocalStore,
        postMediaTransferStore = mediaTransferStore,
        onBackClick = onBackClick,
        onProfileClick = onProfileClick,
        onCommentsClick = onCommentsClick,
        onSharePost = sharePost,
        buildPostShareUrl = { targetPostId, targetUsername ->
            val username = targetUsername?.takeIf { it.isNotBlank() } ?: authorId
            DeepLinkUrls.postUrl(targetPostId, username)
        },
        onEditPost = onEditPost,
        onPostDeleted = onPostDeleted,
        onProfileTagClick = onProfileTagClick,
        onPostLinkClick = onPostLinkClick,
        onPresentFullscreenViewer = onPresentFullscreenViewer,
        presentation = presentation,
        onProfileModalScrollAtTopChanged = onProfileModalScrollAtTopChanged,
        topBarModifier = topBarModifier,
        modifier = modifier,
    )
}
