package me.floow.domain.data.repos

import me.floow.domain.api.models.CreatePostData
import me.floow.domain.data.GetDataResponse
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.models.Post

interface PostsRepository {
	suspend fun getPostById(postId: String): GetDataResponse<Post>

	suspend fun getUserPosts(
		userId: String,
		forceNetwork: Boolean = false,
		limit: Int = 50,
		offset: Int = 0
	): GetDataResponse<List<Post>>

	suspend fun createPost(data: CreatePostData): UpdateDataResponse

	suspend fun deletePost(postId: String): UpdateDataResponse

	suspend fun updatePost(postId: String, description: String?, imageUrls: List<String>?): UpdateDataResponse
}
