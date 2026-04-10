package me.floow.post.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.ProfileRepository
import me.floow.domain.models.Post
import me.floow.shared.post.ui.SharedPostDeepLinkRoute
import org.koin.compose.koinInject

@Composable
fun PostDeepLinkRoute(
	postId: String,
	username: String?,
	overrideDescription: String? = null,
	overrideImageUrls: List<String> = emptyList(),
	onBackClick: () -> Unit,
	onProfileClick: (String) -> Unit,
	onCommentsClick: (Post) -> Unit = {},
	onProfileTagClick: (String) -> Unit = {},
	onPostLinkClick: (String, String) -> Unit = { _, _ -> },
	onEditPost: (Post) -> Unit = {},
	sharePost: (String) -> Unit,
	onPostUpdated: () -> Unit,
	onPostDeleted: () -> Unit,
	modifier: Modifier = Modifier
) {
	val postsRepository: PostsRepository = koinInject()
	val profileRepository: ProfileRepository = koinInject()

	SharedPostDeepLinkRoute(
		postId = postId,
		username = username,
		overrideDescription = overrideDescription,
		overrideImageUrls = overrideImageUrls,
		onBackClick = onBackClick,
		postsRepository = postsRepository,
		profileRepository = profileRepository,
		modifier = modifier,
	) { effectivePost, isSelf, routeModifier ->
		PostRoute(
			postId = effectivePost.id,
			imageUrls = effectivePost.content.imageUrls,
			description = effectivePost.content.description,
			authorId = effectivePost.author.id,
			authorName = effectivePost.author.name?.value,
			authorUsername = effectivePost.author.username?.value,
			authorAvatarUrl = effectivePost.author.avatarUrl,
			category = effectivePost.category,
			createdAt = effectivePost.createdAt,
			likesCount = effectivePost.likesCount,
			commentsCount = effectivePost.commentsCount,
			commentersPreview = effectivePost.commentersPreview,
			isSelf = isSelf,
			onBackClick = onBackClick,
			onProfileClick = onProfileClick,
			onCommentsClick = { _ -> onCommentsClick(effectivePost) },
			onProfileTagClick = onProfileTagClick,
			onPostLinkClick = onPostLinkClick,
			onEditPost = { _ -> onEditPost(effectivePost) },
			sharePost = sharePost,
			onPostUpdated = onPostUpdated,
			onPostDeleted = onPostDeleted,
			modifier = routeModifier,
		)
	}
}
