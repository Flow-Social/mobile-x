package me.floow.domain.api

import me.floow.domain.api.models.CreatePostData
import me.floow.domain.api.models.CreatePostResponse
import me.floow.domain.api.models.GetPostResponse
import me.floow.domain.api.models.GetUserPostsResponse

interface PostsApi {
	suspend fun getPostById(postId: String): GetPostResponse

	suspend fun getUserPosts(
		userId: String,
		forceNetwork: Boolean = false,
		limit: Int = 50,
		offset: Int = 0
	): GetUserPostsResponse

	suspend fun createPost(data: CreatePostData): CreatePostResponse

	suspend fun deletePost(postId: String): Boolean

	suspend fun updatePost(postId: String, description: String?, imageUrls: List<String>?): CreatePostResponse
}
