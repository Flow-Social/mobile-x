package me.floow.comments

import android.content.ClipData
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.res.stringResource
import me.floow.comments.uilogic.CommentsViewModel
import me.floow.uikit.components.media.transfer.PostMediaTransferStore
import me.floow.uikit.util.SetStatusBarStyle
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel
import org.koin.compose.koinInject

@Composable
fun CommentsRoute(
	initialData: CommentsRouteInitialData,
	onBackClick: () -> Unit,
	onAuthorClick: (String) -> Unit = {},
	vm: CommentsViewModel = koinViewModel(),
	modifier: Modifier = Modifier
) {
	val statusBarColor = MaterialTheme.colorScheme.background
	val useDarkStatusIcons = statusBarColor.luminance() > 0.5f
	val commentsTitle = stringResource(R.string.comments_title)
	val commentsPhotoSubtitle = stringResource(R.string.comments_photo_subtitle)
	val clipboard = LocalClipboard.current
	val scope = rememberCoroutineScope()
	val mediaTransferStore: PostMediaTransferStore = koinInject()

	SetStatusBarStyle(
		color = statusBarColor,
		darkIcons = useDarkStatusIcons
	)

	SharedCommentsRoute(
		initialData = initialData,
		onBackClick = onBackClick,
		onAuthorClick = onAuthorClick,
		onCopyText = { text ->
			scope.launch {
				clipboard.setClipEntry(ClipEntry(ClipData.newPlainText("comment-message", text)))
			}
		},
		component = vm,
		mediaTransferStore = mediaTransferStore,
		commentsTitle = commentsTitle,
		commentsPhotoSubtitle = commentsPhotoSubtitle,
		modifier = modifier
	)
}
