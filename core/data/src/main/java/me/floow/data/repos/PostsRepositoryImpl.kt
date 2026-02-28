package me.floow.data.repos

import me.floow.domain.api.PostsApi
import me.floow.domain.api.models.CreatePostData
import me.floow.domain.api.models.CreatePostResponse
import me.floow.domain.api.models.GetPostResponse
import me.floow.domain.api.models.GetUserPostsResponse
import me.floow.domain.api.models.PostItem
import me.floow.domain.data.FailureError
import me.floow.domain.data.GetDataError
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.PostsRepository
import me.floow.domain.models.Post
import me.floow.domain.models.PostAuthor
import me.floow.domain.models.PostContent
import me.floow.domain.models.PostImageVariant
import me.floow.domain.utils.Logger
import me.floow.domain.values.ProfileName
import me.floow.domain.values.ProfileUsername
import me.floow.domain.values.util.RawValueObjectCreate

class PostsRepositoryImpl(
	private val logger: Logger,
	private val postsApi: PostsApi
) : PostsRepository {
	@OptIn(RawValueObjectCreate::class)
	override suspend fun getPostById(postId: String): GetDataResponse<Post> {
		return when (val response = postsApi.getPostById(postId)) {
			is GetPostResponse.Success -> GetDataResponse.Success(response.post.toDomainPost())

			is GetPostResponse.Error -> {
				logger.d("PostsRepositoryImpl.getPostById", "Failure response: $response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	@OptIn(RawValueObjectCreate::class)
	override suspend fun getUserPosts(
		userId: String,
		forceNetwork: Boolean,
		limit: Int,
		offset: Int
	): GetDataResponse<List<Post>> {
		return when (
			val response = postsApi.getUserPosts(
				userId = userId,
				forceNetwork = forceNetwork,
				limit = limit,
				offset = offset
			)
		) {
			is GetUserPostsResponse.Success -> {
				val posts = response.posts.map { item -> item.toDomainPost() }

				GetDataResponse.Success(posts)
			}

			is GetUserPostsResponse.Error -> {
				logger.d("PostsRepositoryImpl.getUserPosts", "Failure response: $response")
				GetDataResponse.Error(error = GetDataError.Other)
			}
		}
	}

	override suspend fun createPost(data: CreatePostData): UpdateDataResponse {
		return when (val response = postsApi.createPost(data)) {
			is CreatePostResponse.Success -> UpdateDataResponse.Success
			is CreatePostResponse.Error -> {
				logger.d("PostsRepositoryImpl.createPost", "Failure response: $response")
				UpdateDataResponse.Failure(FailureError.Other)
			}
		}
	}

	override suspend fun deletePost(postId: String): UpdateDataResponse {
		val result = postsApi.deletePost(postId)
		return if (result) {
			UpdateDataResponse.Success
		} else {
			UpdateDataResponse.Failure(FailureError.Other)
		}
	}

	override suspend fun updatePost(
		postId: String,
		description: String?,
		imageUrls: List<String>?
	): UpdateDataResponse {
		return when (val response = postsApi.updatePost(postId, description, imageUrls)) {
			is CreatePostResponse.Success -> UpdateDataResponse.Success
			is CreatePostResponse.Error -> UpdateDataResponse.Failure(FailureError.Other)
		}
	}
}

@OptIn(RawValueObjectCreate::class)
private fun PostItem.toDomainPost(): Post {
	return Post(
		id = id,
		author = PostAuthor(
			id = authorId,
			name = authorName?.let { ProfileName.createRaw(it) },
			username = authorUsername?.let { ProfileUsername.createRaw(it) },
			avatarUrl = authorAvatarUrl
		),
		content = PostContent(
			imageUrls = imageUrls,
			description = description,
			imageVariants = imageVariants.map { variant ->
				PostImageVariant(
					lqUrl = variant.lqUrl,
					previewUrl = variant.previewUrl,
					fullUrl = variant.fullUrl,
					width = variant.width,
					height = variant.height
				)
			}
		),
		category = category,
		createdAt = createdAt,
		likesCount = likesCount,
		commentsCount = commentsCount,
		commentersPreview = commentersPreview
	)
}
