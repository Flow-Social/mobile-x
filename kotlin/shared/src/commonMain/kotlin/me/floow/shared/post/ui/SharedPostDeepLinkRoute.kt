package me.floow.shared.post.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.data.repos.ProfileRepository
import me.floow.domain.models.Post
import me.floow.uikit.components.loading.FlowLoadingIndicator

private sealed interface PostDeepLinkLoadState {
    data object Loading : PostDeepLinkLoadState
    data class Error(val title: String, val description: String) : PostDeepLinkLoadState
    data class Success(val post: Post, val isSelf: Boolean) : PostDeepLinkLoadState
}

@Composable
fun SharedPostDeepLinkRoute(
    postId: String,
    username: String?,
    overrideDescription: String? = null,
    overrideImageUrls: List<String> = emptyList(),
    onBackClick: () -> Unit,
    postsRepository: PostsRepository,
    profileRepository: ProfileRepository,
    modifier: Modifier = Modifier,
    content: @Composable (effectivePost: Post, isSelf: Boolean, modifier: Modifier) -> Unit,
) {
    var state by remember(postId, username) { mutableStateOf<PostDeepLinkLoadState>(PostDeepLinkLoadState.Loading) }

    LaunchedEffect(postId, username, postsRepository, profileRepository) {
        state = PostDeepLinkLoadState.Loading

        val postResponse = postsRepository.getPostById(postId)
        if (postResponse !is GetDataResponse.Success) {
            state = PostDeepLinkLoadState.Error(
                title = "Пост не найден",
                description = "Не удалось загрузить пост.",
            )
            return@LaunchedEffect
        }

        val post = postResponse.data
        val linkUsername = username?.trim().orEmpty()
        val authorUsername = post.author.username?.value.orEmpty()
        if (linkUsername.isNotBlank() && !authorUsername.equals(linkUsername, ignoreCase = true)) {
            state = PostDeepLinkLoadState.Error(
                title = "Пост не найден",
                description = "Ссылка содержит другого автора.",
            )
            return@LaunchedEffect
        }

        var isSelf = false
        val selfResponse = profileRepository.getSelfData()
        if (selfResponse is GetDataResponse.Success) {
            val selfUsername = selfResponse.data.username?.value
            isSelf = !selfUsername.isNullOrBlank() && selfUsername == post.author.username?.value
        }

        state = PostDeepLinkLoadState.Success(post = post, isSelf = isSelf)
    }

    when (val current = state) {
        PostDeepLinkLoadState.Loading -> {
            Box(
                modifier = modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                FlowLoadingIndicator()
            }
        }

        is PostDeepLinkLoadState.Error -> {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(text = current.title)
                Text(text = current.description)
                OutlinedButton(onClick = onBackClick) {
                    Text("Назад")
                }
            }
        }

        is PostDeepLinkLoadState.Success -> {
            val detailImageUrls = current.post.content.imageVariants
                .mapNotNull { it.fullUrl?.takeIf(String::isNotBlank) }
                .ifEmpty { current.post.content.imageUrls }
            val normalizedOverrideImageUrls = overrideImageUrls
                .map(String::trim)
                .filter(String::isNotBlank)
            val effectiveImageUrls = normalizedOverrideImageUrls.ifEmpty { detailImageUrls }
            val effectiveDescription = overrideDescription ?: current.post.content.description
            val effectivePost = current.post.copy(
                content = current.post.content.copy(
                    description = effectiveDescription,
                    imageUrls = effectiveImageUrls,
                    imageVariants = emptyList(),
                )
            )
            content(
                effectivePost,
                current.isSelf,
                modifier,
            )
        }
    }
}
