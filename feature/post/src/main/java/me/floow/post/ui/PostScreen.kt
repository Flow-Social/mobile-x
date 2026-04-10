package me.floow.post.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.floow.shared.post.ui.PostScreenModel
import me.floow.shared.post.ui.PostScreen as SharedPostScreen
import me.floow.uikit.components.media.transfer.PostMediaSourceSnapshot

@Composable
internal fun PostScreen(
    model: PostScreenModel,
    onBackClick: () -> Unit,
    onProfileClick: (String) -> Unit,
    onCommentsClick: (PostMediaSourceSnapshot?) -> Unit,
    onShareClick: () -> Unit,
    onEditPost: (PostMediaSourceSnapshot?) -> Unit,
    onDeletePost: () -> Unit,
    onProfileTagClick: (String) -> Unit = {},
    onPostLinkClick: (String, String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    SharedPostScreen(
        model = model,
        onBackClick = onBackClick,
        onProfileClick = onProfileClick,
        onCommentsClick = onCommentsClick,
        onShareClick = onShareClick,
        onEditPost = onEditPost,
        onDeletePost = onDeletePost,
        onProfileTagClick = onProfileTagClick,
        onPostLinkClick = onPostLinkClick,
        modifier = modifier
    )
}
