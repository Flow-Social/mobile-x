package me.floow.post.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.ProfileRepository
import me.floow.domain.models.Post
import me.floow.uikit.components.loading.FlowLoadingIndicator
import me.floow.uikit.components.misc.ErrorWithButtonContentBox
import org.koin.compose.koinInject

private sealed interface PostDeepLinkState {
	data object Loading : PostDeepLinkState
	data class Error(val title: String, val description: String) : PostDeepLinkState
	data class Success(val post: Post, val isSelf: Boolean) : PostDeepLinkState
}

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
	var state by remember { mutableStateOf<PostDeepLinkState>(PostDeepLinkState.Loading) }

	LaunchedEffect(postId, username) {
		state = PostDeepLinkState.Loading

		val postResponse = postsRepository.getPostById(postId)
		if (postResponse is GetDataResponse.Success) {
			val post = postResponse.data
			val linkUsername = username?.trim().orEmpty()
			val authorUsername = post.author.username?.value.orEmpty()
			if (linkUsername.isNotBlank() && !authorUsername.equals(linkUsername, ignoreCase = true)) {
				state = PostDeepLinkState.Error(
					title = "Пост не найден",
					description = "Ссылка содержит другого автора."
				)
				return@LaunchedEffect
			}

			var isSelf = false
			val selfResponse = profileRepository.getSelfData()
			if (selfResponse is GetDataResponse.Success) {
				val selfUsername = selfResponse.data.username?.value
				val authorUsername = post.author.username?.value
				isSelf = !selfUsername.isNullOrBlank() && selfUsername == authorUsername
			}

			state = PostDeepLinkState.Success(post = post, isSelf = isSelf)
		} else {
			state = PostDeepLinkState.Error(
				title = "Пост не найден",
				description = "Не удалось загрузить пост."
			)
		}
	}

	when (val current = state) {
		PostDeepLinkState.Loading -> {
			Box(
				modifier = modifier.fillMaxSize(),
				contentAlignment = Alignment.Center
			) {
				FlowLoadingIndicator()
			}
		}

		is PostDeepLinkState.Error -> {
			ErrorWithButtonContentBox(
				title = current.title,
				description = current.description,
				onButtonClick = onBackClick,
				buttonContent = { Text("Назад") },
				modifier = modifier.fillMaxSize()
			)
		}

		is PostDeepLinkState.Success -> {
			val post = current.post
			val detailImageUrls = post.content.imageVariants
				.mapNotNull { it.fullUrl?.takeIf(String::isNotBlank) }
				.ifEmpty { post.content.imageUrls }
			val normalizedOverrideImageUrls = overrideImageUrls
				.map { it.trim() }
				.filter { it.isNotBlank() }
			val effectiveImageUrls = normalizedOverrideImageUrls.ifEmpty { detailImageUrls }
			val effectiveDescription = overrideDescription ?: post.content.description
			val effectivePost = post.copy(
				content = post.content.copy(
					description = effectiveDescription,
					imageUrls = effectiveImageUrls,
					imageVariants = emptyList(),
				)
			)
			PostRoute(
				postId = post.id,
				imageUrls = effectiveImageUrls,
				description = effectiveDescription,
				authorId = post.author.id,
				authorName = post.author.name?.value,
				authorUsername = post.author.username?.value,
				authorAvatarUrl = post.author.avatarUrl,
					category = post.category,
					createdAt = post.createdAt,
					likesCount = post.likesCount,
					commentsCount = post.commentsCount,
					commentersPreview = post.commentersPreview,
					isSelf = current.isSelf,
					onBackClick = onBackClick,
					onProfileClick = onProfileClick,
					onCommentsClick = { _ -> onCommentsClick(effectivePost) },
					onProfileTagClick = onProfileTagClick,
					onPostLinkClick = onPostLinkClick,
					onEditPost = { _ -> onEditPost(effectivePost) },
					sharePost = sharePost,
					onPostUpdated = onPostUpdated,
					onPostDeleted = onPostDeleted,
				modifier = modifier
			)
		}
	}
}
