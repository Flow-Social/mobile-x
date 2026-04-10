package me.floow.post.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.cache.PostsLocalStore
import me.floow.domain.cache.ProfileLocalStore
import me.floow.domain.deeplink.DeepLinkUrls
import me.floow.shared.post.ui.SharedPostRoute
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot
import me.floow.uikit.components.media.transfer.PostMediaTransferStore
import me.floow.uikit.util.SetStatusBarStyle
import org.koin.compose.koinInject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val postsRepository: PostsRepository = koinInject()
    val postsLocalStore: PostsLocalStore = koinInject()
    val profileLocalStore: ProfileLocalStore = koinInject()
    val mediaTransferStore: PostMediaTransferStore = koinInject()
    val statusBarColor = MaterialTheme.colorScheme.background
    val useDarkStatusIcons = statusBarColor.luminance() > 0.5f

    SetStatusBarStyle(
        color = statusBarColor,
        darkIcons = useDarkStatusIcons
    )

    SharedPostRoute(
        postId = postId,
        imageUrls = imageUrls,
        mediaTransferToken = mediaTransferToken,
        description = description,
        authorId = authorId,
        authorName = authorName,
        authorUsername = authorUsername,
        authorAvatarUrl = authorAvatarUrl,
        category = category,
        createdAtLabel = formatPostCreatedAt(createdAt),
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
        modifier = modifier
    )
}

private fun formatPostCreatedAt(timestamp: Long): String {
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return formatter.format(Date(timestamp))
}
